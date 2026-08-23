package com.federa.backend.backup;

import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

@Component
public class EjecutorDumpMariaDbProceso implements EjecutorDumpMariaDb {

    private static final int MAX_ERROR_BYTES = 16 * 1024;

    private final BackupPropiedades propiedades;
    private final AtomicReference<String> version = new AtomicReference<>();

    public EjecutorDumpMariaDbProceso(BackupPropiedades propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public ResultadoDump ejecutar(Path destinoTemporal) {
        validarConfiguracion();
        Process proceso = null;
        try {
            Files.createDirectories(destinoTemporal.getParent());
            ProcessBuilder constructor = new ProcessBuilder(comandoDump());
            // Evita poner la contraseña en la línea de comandos y, por tanto,
            // en listados de procesos. En producción se recomienda configurar
            // archivo-credenciales, protegido por permisos del sistema.
            if (vacio(propiedades.getArchivoCredenciales())) {
                constructor.environment().put("MYSQL_PWD", propiedades.getContrasena());
            }
            proceso = constructor.start();
            Process procesoEnCurso = proceso;

            CompletableFuture<Void> copiarSalida = CompletableFuture.runAsync(
                    () -> comprimir(procesoEnCurso.getInputStream(), destinoTemporal));
            CompletableFuture<String> leerError = CompletableFuture.supplyAsync(
                    () -> leerLimitado(procesoEnCurso.getErrorStream()));

            boolean termino = proceso.waitFor(
                    propiedades.getMaxDuracionMinutos(), TimeUnit.MINUTES);
            if (!termino) {
                proceso.destroyForcibly();
                proceso.waitFor(10, TimeUnit.SECONDS);
                esperarCopia(copiarSalida);
                throw new BackupFallidoException("El respaldo superó el tiempo máximo de "
                        + propiedades.getMaxDuracionMinutos() + " minutos.");
            }

            esperarCopia(copiarSalida);
            String error = esperarResultado(leerError);
            if (proceso.exitValue() != 0) {
                throw new BackupFallidoException("mariadb-dump terminó con código "
                        + proceso.exitValue() + detalle(error));
            }

            long tamano = Files.size(destinoTemporal);
            if (tamano == 0) {
                throw new BackupFallidoException("mariadb-dump produjo un archivo vacío.");
            }
            return new ResultadoDump(tamano, obtenerVersion());
        } catch (BackupFallidoException e) {
            borrarSilencioso(destinoTemporal);
            throw e;
        } catch (IOException e) {
            borrarSilencioso(destinoTemporal);
            throw new BackupFallidoException(
                    "No se pudo ejecutar mariadb-dump: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (proceso != null) proceso.destroyForcibly();
            borrarSilencioso(destinoTemporal);
            throw new BackupFallidoException("El respaldo fue interrumpido.", e);
        }
    }

    private List<String> comandoDump() {
        List<String> comando = new ArrayList<>();
        comando.add(propiedades.getEjecutable());
        if (!vacio(propiedades.getArchivoCredenciales())) {
            // MariaDB exige que defaults-extra-file sea la primera opción.
            comando.add("--defaults-extra-file=" + propiedades.getArchivoCredenciales());
        }
        comando.add("--protocol=tcp");
        comando.add("--host=" + propiedades.getHost());
        comando.add("--port=" + propiedades.getPuerto());
        if (vacio(propiedades.getArchivoCredenciales())) {
            comando.add("--user=" + propiedades.getUsuario());
        }
        comando.add("--single-transaction");
        comando.add("--quick");
        comando.add("--routines");
        comando.add("--triggers");
        comando.add("--events");
        comando.add("--hex-blob");
        comando.add("--default-character-set=utf8mb4");
        comando.add("--databases");
        comando.add(propiedades.getBaseDatos());
        return comando;
    }

    private void comprimir(InputStream entrada, Path destino) {
        try (InputStream origen = new BufferedInputStream(entrada);
             OutputStream archivo = Files.newOutputStream(destino,
                     StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             GZIPOutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(archivo))) {
            origen.transferTo(gzip);
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private String obtenerVersion() {
        String conocida = version.get();
        if (conocida != null) return conocida;

        try {
            Process proceso = new ProcessBuilder(propiedades.getEjecutable(), "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean termino = proceso.waitFor(Duration.ofSeconds(5).toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!termino) {
                proceso.destroyForcibly();
                return "desconocida";
            }
            String leida = leerLimitado(proceso.getInputStream()).trim();
            if (leida.isBlank()) leida = "desconocida";
            version.compareAndSet(null, leida);
            return version.get();
        } catch (IOException e) {
            return "desconocida";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "desconocida";
        }
    }

    private void validarConfiguracion() {
        if (vacio(propiedades.getEjecutable())) {
            throw new BackupFallidoException("No está configurado mariadb-dump.");
        }
        if (!propiedades.getBaseDatos().matches("[A-Za-z0-9_$]+")) {
            throw new BackupFallidoException("El nombre configurado de la base no es válido.");
        }
        if (!vacio(propiedades.getArchivoCredenciales())
                && !Files.isRegularFile(Path.of(propiedades.getArchivoCredenciales()))) {
            throw new BackupFallidoException(
                    "No existe el archivo de credenciales configurado para los respaldos.");
        }
    }

    private String leerLimitado(InputStream entrada) {
        try (InputStream flujo = entrada) {
            byte[] bytes = flujo.readNBytes(MAX_ERROR_BYTES);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String detalle(String error) {
        String limpio = error == null ? "" : error;
        String clave = propiedades.getContrasena();
        if (clave != null && !clave.isEmpty()) limpio = limpio.replace(clave, "***");
        limpio = limpio.replaceAll("\\s+", " ").trim();
        return limpio.isBlank() ? "." : ": " + limpio;
    }

    private void esperarCopia(CompletableFuture<Void> futuro) {
        try {
            futuro.join();
        } catch (CompletionException e) {
            Throwable causa = e.getCause();
            throw new BackupFallidoException("No se pudo escribir el respaldo comprimido: "
                    + (causa == null ? e.getMessage() : causa.getMessage()), causa);
        }
    }

    private <T> T esperarResultado(CompletableFuture<T> futuro) {
        return futuro.join();
    }

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private void borrarSilencioso(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignored) {
            // El fallo original es más útil que el de la limpieza.
        }
    }
}
