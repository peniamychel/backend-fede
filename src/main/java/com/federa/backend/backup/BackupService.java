package com.federa.backend.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FORMATO_ID = DateTimeFormatter.ofPattern(
            "yyyyMMdd_HHmmss_SSS");
    private static final String SUFIJO_ARCHIVO = ".sql.gz";
    private static final String SUFIJO_MANIFIESTO = ".json";
    private static final String SUFIJO_CHECKSUM = ".sha256";

    private final BackupPropiedades propiedades;
    private final EjecutorDumpMariaDb ejecutorDump;
    private final ObjectMapper json;
    private final Executor ejecutor;
    private final AtomicReference<String> activo = new AtomicReference<>();

    public BackupService(BackupPropiedades propiedades,
                         EjecutorDumpMariaDb ejecutorDump,
                         ObjectMapper json,
                         @Qualifier("ejecutorBackups") Executor ejecutor) {
        this.propiedades = propiedades;
        this.ejecutorDump = ejecutorDump;
        this.json = json;
        this.ejecutor = ejecutor;
    }

    @PostConstruct
    void prepararDirectorio() {
        try {
            Files.createDirectories(raiz());
            limpiarTemporalesInterrumpidos();
            cerrarProcesosInterrumpidos();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar el directorio de respaldos: "
                    + raiz(), e);
        }
    }

    public BackupInfo iniciarManual(String usuario) {
        return iniciar(TipoBackup.MANUAL, usuario);
    }

    public void iniciarAutomatico() {
        if (!propiedades.isHabilitado() || !propiedades.getAutomatico().isHabilitado()) {
            return;
        }
        try {
            iniciar(TipoBackup.AUTOMATICO, "SISTEMA");
        } catch (ReglaNegocioException e) {
            log.warn("No se inició el respaldo automático: {}", e.getMessage());
        }
    }

    public List<BackupInfo> listar() {
        List<BackupInfo> respaldos = new ArrayList<>();
        try (DirectoryStream<Path> archivos = Files.newDirectoryStream(
                raiz(), "*" + SUFIJO_MANIFIESTO)) {
            for (Path archivo : archivos) {
                try {
                    respaldos.add(json.readValue(archivo.toFile(), BackupInfo.class));
                } catch (IOException e) {
                    log.warn("Se ignoró un manifiesto de respaldo ilegible: {}", archivo);
                }
            }
        } catch (IOException e) {
            throw new BackupFallidoException("No se pudo leer el historial de respaldos.", e);
        }
        respaldos.sort(Comparator.comparing(BackupInfo::iniciadoEn).reversed());
        return List.copyOf(respaldos);
    }

    public BackupInfo obtener(String id) {
        validarId(id);
        Path manifiesto = raiz().resolve(id + SUFIJO_MANIFIESTO);
        if (!Files.isRegularFile(manifiesto)) {
            throw new RecursoNoEncontradoException("No existe el respaldo " + id);
        }
        try {
            return json.readValue(manifiesto.toFile(), BackupInfo.class);
        } catch (IOException e) {
            throw new BackupFallidoException("El manifiesto del respaldo está dañado.", e);
        }
    }

    public Path archivoParaDescarga(String id) {
        BackupInfo info = obtener(id);
        if (info.estado() != EstadoBackup.COMPLETADO) {
            throw new ReglaNegocioException("El respaldo todavía no está disponible.");
        }
        Path archivo = raiz().resolve(id + SUFIJO_ARCHIVO).normalize();
        if (!archivo.startsWith(raiz()) || !Files.isRegularFile(archivo)) {
            throw new RecursoNoEncontradoException("No se encontró el archivo del respaldo.");
        }
        return archivo;
    }

    public void eliminar(String id) {
        validarId(id);
        if (id.equals(activo.get())) {
            throw new ReglaNegocioException("No se puede eliminar un respaldo en ejecución.");
        }
        obtener(id);
        borrarArchivos(id);
    }

    private BackupInfo iniciar(TipoBackup tipo, String usuario) {
        if (!propiedades.isHabilitado()) {
            throw new ReglaNegocioException("La creación de respaldos está deshabilitada.");
        }

        String id = nuevoId();
        if (!activo.compareAndSet(null, id)) {
            throw new ReglaNegocioException("Ya hay un respaldo en ejecución.");
        }

        BackupInfo inicial = new BackupInfo(
                id, tipo, EstadoBackup.EN_PROCESO, Instant.now(), null,
                id + SUFIJO_ARCHIVO, null, null, propiedades.getBaseDatos(),
                usuario == null || usuario.isBlank() ? "desconocido" : usuario,
                null, null);
        try {
            escribirManifiesto(inicial);
            ejecutor.execute(() -> ejecutar(inicial));
            return inicial;
        } catch (RuntimeException e) {
            activo.compareAndSet(id, null);
            throw e;
        }
    }

    private void ejecutar(BackupInfo inicial) {
        Path temporal = raiz().resolve(inicial.id() + SUFIJO_ARCHIVO + ".part");
        Path definitivo = raiz().resolve(inicial.id() + SUFIJO_ARCHIVO);
        try {
            EjecutorDumpMariaDb.ResultadoDump resultado = ejecutorDump.ejecutar(temporal);
            validarGzip(temporal);
            String checksum = sha256(temporal);
            moverAtomico(temporal, definitivo);
            escribirChecksum(inicial.id(), checksum);

            BackupInfo completado = new BackupInfo(
                    inicial.id(), inicial.tipo(), EstadoBackup.COMPLETADO,
                    inicial.iniciadoEn(), Instant.now(), inicial.archivo(),
                    resultado.tamanoBytes(), checksum, inicial.baseDatos(),
                    inicial.solicitadoPor(), resultado.versionMariaDb(), null);
            escribirManifiesto(completado);
            if (inicial.tipo() == TipoBackup.AUTOMATICO) aplicarRetencion();
            log.info("Respaldo {} completado: {} bytes", inicial.id(), resultado.tamanoBytes());
        } catch (Exception e) {
            borrarSilencioso(temporal);
            BackupInfo fallido = new BackupInfo(
                    inicial.id(), inicial.tipo(), EstadoBackup.FALLIDO,
                    inicial.iniciadoEn(), Instant.now(), inicial.archivo(),
                    null, null, inicial.baseDatos(), inicial.solicitadoPor(),
                    null, mensajeSeguro(e));
            try {
                escribirManifiesto(fallido);
            } catch (RuntimeException manifiestoError) {
                log.error("No se pudo registrar el fallo del respaldo {}", inicial.id(),
                        manifiestoError);
            }
            log.error("Falló el respaldo {}: {}", inicial.id(), mensajeSeguro(e));
        } finally {
            activo.compareAndSet(inicial.id(), null);
        }
    }

    private void aplicarRetencion() {
        List<BackupInfo> automaticos = listar().stream()
                .filter(b -> b.tipo() == TipoBackup.AUTOMATICO)
                .filter(b -> b.estado() == EstadoBackup.COMPLETADO)
                .sorted(Comparator.comparing(BackupInfo::iniciadoEn).reversed())
                .toList();

        Set<String> conservar = new HashSet<>();
        Set<LocalDate> dias = new HashSet<>();
        Set<String> semanas = new HashSet<>();
        Set<YearMonth> meses = new HashSet<>();
        ZoneId zona = zona();
        WeekFields iso = WeekFields.ISO;

        // Incluso con una configuración de retención equivocada en cero,
        // nunca se elimina el último respaldo automático correcto.
        if (!automaticos.isEmpty()) conservar.add(automaticos.get(0).id());

        for (BackupInfo backup : automaticos) {
            ZonedDateTime fecha = backup.iniciadoEn().atZone(zona);
            boolean seConserva = false;
            LocalDate dia = fecha.toLocalDate();
            if (dias.size() < propiedades.getRetencion().getDiarios() && dias.add(dia)) {
                seConserva = true;
            }
            String semana = fecha.get(iso.weekBasedYear()) + "-" + fecha.get(iso.weekOfWeekBasedYear());
            if (semanas.size() < propiedades.getRetencion().getSemanales()
                    && semanas.add(semana)) {
                seConserva = true;
            }
            YearMonth mes = YearMonth.from(fecha);
            if (meses.size() < propiedades.getRetencion().getMensuales() && meses.add(mes)) {
                seConserva = true;
            }
            if (seConserva) conservar.add(backup.id());
        }

        for (BackupInfo backup : automaticos) {
            if (!conservar.contains(backup.id())) {
                borrarArchivos(backup.id());
                log.info("Respaldo automático {} eliminado por retención", backup.id());
            }
        }
    }

    private void cerrarProcesosInterrumpidos() {
        for (BackupInfo info : listar()) {
            if (info.estado() != EstadoBackup.EN_PROCESO) continue;
            BackupInfo fallido = new BackupInfo(
                    info.id(), info.tipo(), EstadoBackup.FALLIDO,
                    info.iniciadoEn(), Instant.now(), info.archivo(), null, null,
                    info.baseDatos(), info.solicitadoPor(), info.versionMariaDb(),
                    "El backend se detuvo antes de terminar el respaldo.");
            escribirManifiesto(fallido);
        }
    }

    private void limpiarTemporalesInterrumpidos() throws IOException {
        try (DirectoryStream<Path> temporales = Files.newDirectoryStream(raiz(), "*.part")) {
            for (Path temporal : temporales) Files.deleteIfExists(temporal);
        }
    }

    private void validarGzip(Path archivo) throws IOException {
        try (InputStream entrada = new GZIPInputStream(Files.newInputStream(archivo))) {
            if (entrada.read() < 0) {
                throw new IOException("El respaldo comprimido no contiene datos.");
            }
            entrada.transferTo(OutputStreamNulo.INSTANCIA);
        }
    }

    private String sha256(Path archivo) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream entrada = new DigestInputStream(
                    Files.newInputStream(archivo), digest)) {
                entrada.transferTo(OutputStreamNulo.INSTANCIA);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no dispone de SHA-256.", e);
        }
    }

    private void escribirChecksum(String id, String checksum) throws IOException {
        Files.writeString(raiz().resolve(id + SUFIJO_CHECKSUM),
                checksum + "  " + id + SUFIJO_ARCHIVO + System.lineSeparator(),
                StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
    }

    private void escribirManifiesto(BackupInfo info) {
        Path destino = raiz().resolve(info.id() + SUFIJO_MANIFIESTO);
        Path temporal = raiz().resolve(info.id() + SUFIJO_MANIFIESTO + ".part");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporal.toFile(), info);
            moverAtomico(temporal, destino);
        } catch (IOException e) {
            borrarSilencioso(temporal);
            throw new BackupFallidoException("No se pudo guardar el manifiesto del respaldo.", e);
        }
    }

    private void moverAtomico(Path origen, Path destino) throws IOException {
        try {
            Files.move(origen, destino, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void borrarArchivos(String id) {
        borrar(raiz().resolve(id + SUFIJO_ARCHIVO));
        borrar(raiz().resolve(id + SUFIJO_CHECKSUM));
        borrar(raiz().resolve(id + SUFIJO_MANIFIESTO));
    }

    private void borrarSilencioso(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignored) {
            // La limpieza no debe ocultar el error original del respaldo.
        }
    }

    private void borrar(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            throw new BackupFallidoException("No se pudo eliminar " + archivo.getFileName(), e);
        }
    }

    private String nuevoId() {
        return "federa_" + FORMATO_ID.format(ZonedDateTime.now(zona())) + "_"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private ZoneId zona() {
        return ZoneId.of(propiedades.getAutomatico().getZona());
    }

    private Path raiz() {
        return propiedades.getRaiz().toAbsolutePath().normalize();
    }

    private void validarId(String id) {
        if (id == null
                || !id.matches("federa_[0-9]{8}_[0-9]{6}_[0-9]{3}_[0-9a-f]{8}")) {
            throw new RecursoNoEncontradoException("Identificador de respaldo inválido.");
        }
    }

    private String mensajeSeguro(Exception error) {
        String mensaje = error.getMessage();
        if (mensaje == null || mensaje.isBlank()) mensaje = error.getClass().getSimpleName();
        String clave = propiedades.getContrasena();
        if (clave != null && !clave.isEmpty()) mensaje = mensaje.replace(clave, "***");
        mensaje = mensaje.replaceAll("\\s+", " ").trim();
        return mensaje.length() <= 1000 ? mensaje : mensaje.substring(0, 1000);
    }

    private static final class OutputStreamNulo extends java.io.OutputStream {
        private static final OutputStreamNulo INSTANCIA = new OutputStreamNulo();

        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    }
}
