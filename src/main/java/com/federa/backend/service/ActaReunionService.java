package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.almacen.TransaccionArchivos;
import com.federa.backend.dto.ReunionResponse;
import com.federa.backend.exception.ArchivoInvalidoException;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.HojaActa;
import com.federa.backend.model.Reunion;
import com.federa.backend.repository.ReunionRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * El acta de una reunión, hoja por hoja.
 * <p>
 * El acta casi nunca es un solo archivo. Lo habitual es el cuaderno de actas
 * fotografiado hoja por hoja con el teléfono, ahí mismo en la asamblea. Por eso
 * se suben de a una y quedan ordenadas, en vez de exigir un PDF armado antes,
 * que es un paso que nadie hace en el campo.
 * <p>
 * Las hojas <b>no se reducen</b>. Una foto en la credencial ocupa dos
 * centímetros y achicarla no se nota; un acta se lee, y bajarle la resolución la
 * vuelve inservible justo cuando alguien necesita verificar qué se decidió.
 */
@Service
public class ActaReunionService {

    /** Diez megas por hoja. Una foto de teléfono entra de sobra. */
    static final int PESO_MAXIMO = 10 * 1024 * 1024;

    /** Tope de hojas. Un acta de asamblea no pasa de unas pocas carillas. */
    static final int MAXIMO_HOJAS = 40;

    private static final Set<String> TIPOS = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");

    private final ReunionRepository reunionRepository;
    private final ReunionService reunionService;
    private final AlmacenObjetos almacen;

    public ActaReunionService(ReunionRepository reunionRepository,
                              ReunionService reunionService,
                              AlmacenObjetos almacen) {
        this.reunionRepository = reunionRepository;
        this.reunionService = reunionService;
        this.almacen = almacen;
    }

    /** Una hoja con su tipo, para mostrarla. */
    public record Descarga(String nombreArchivo, String tipoMime, byte[] contenido) {
    }

    /**
     * Agrega una hoja al final del acta.
     *
     * @param codigo el número del acta en el libro. Hace falta con la primera
     *               hoja; después se puede omitir —las siguientes son del mismo
     *               acta— o mandar otro para corregirlo.
     */
    @Transactional
    public ReunionResponse agregarHoja(Long reunionId, byte[] contenido, String nombreArchivo,
                                       String tipoMimeDeclarado, String codigo) {
        Reunion reunion = buscar(reunionId);
        String tipoMime = resolverTipo(tipoMimeDeclarado, nombreArchivo);
        verificar(contenido, tipoMime);
        aplicarCodigo(reunion, codigo);

        if (reunion.getHojasActa().size() >= MAXIMO_HOJAS) {
            throw new ReglaNegocioException(
                    "El acta ya tiene " + MAXIMO_HOJAS + " hojas, que es el tope.");
        }

        int orden = reunion.getHojasActa().size() + 1;
        String clave = "actas/" + reunionId + "/" + orden + "-"
                + UUID.randomUUID().toString().substring(0, 8)
                + extensionDe(tipoMime);

        almacen.guardar(clave, contenido);

        HojaActa hoja = new HojaActa();
        hoja.setReunion(reunion);
        hoja.setOrden(orden);
        hoja.setClave(clave);
        hoja.setNombre(nombreDe(nombreArchivo));
        hoja.setTipoMime(tipoMime);
        hoja.setTamanoBytes(contenido.length);
        reunion.getHojasActa().add(hoja);

        reunionRepository.flush();
        return reunionService.obtener(reunionId);
    }

    /**
     * Corrige el número del acta, sin tocar las hojas.
     * <p>
     * Que se haya tipeado mal no es motivo para volver a subir las fotos: el
     * número es un dato del acta, no de cada hoja.
     */
    @Transactional
    public ReunionResponse ponerCodigo(Long reunionId, String codigo) {
        Reunion reunion = buscar(reunionId);
        if (reunion.getHojasActa().isEmpty()) {
            throw new ReglaNegocioException(
                    "La reunión «" + reunion.getTitulo() + "» no tiene acta cargada. Un número "
                    + "de acta sin acta prometería un documento que no está.");
        }
        if (Textos.limpiar(codigo) == null) {
            throw new ReglaNegocioException(
                    "Falta el número del acta. Es el que está escrito en el libro.");
        }
        aplicarCodigo(reunion, codigo);
        reunionRepository.flush();
        return reunionService.obtener(reunionId);
    }

    public Descarga leerHoja(Long reunionId, Long hojaId) {
        HojaActa hoja = buscarHoja(buscar(reunionId), hojaId);
        return new Descarga(hoja.getNombre(), hoja.getTipoMime(),
                almacen.leer(hoja.getClave()));
    }

    /**
     * Quita una hoja y renumera las que quedan.
     * <p>
     * Se bloquea si el acta quedaría vacía y en esa reunión se decidió algún
     * veto: el acta es el respaldo de esa decisión, y dejarla sin documento
     * convertiría una sanción en la palabra de quien la cargó.
     */
    @Transactional
    public ReunionResponse quitarHoja(Long reunionId, Long hojaId, long vetosDecididos) {
        Reunion reunion = buscar(reunionId);
        HojaActa hoja = buscarHoja(reunion, hojaId);

        if (reunion.getHojasActa().size() == 1 && vetosDecididos > 0) {
            throw new ReglaNegocioException(String.format(
                    "Es la última hoja del acta, y en esta reunión se decidieron %d veto(s) "
                    + "que quedarían sin respaldo. Subí otra hoja antes de quitar esta.",
                    vetosDecididos));
        }

        String clave = hoja.getClave();
        reunion.getHojasActa().remove(hoja);

        // Renumerar: si se quita la hoja 2 de cuatro, las que siguen tienen que
        // pasar a 2 y 3, o el orden queda con un hueco y la siguiente que se
        // suba pisaría un número existente.
        List<HojaActa> quedan = reunion.getHojasActa();
        for (int i = 0; i < quedan.size(); i++) {
            quedan.get(i).setOrden(i + 1);
        }
        // Sin hojas no hay acta, y un número de acta sin acta no identifica
        // nada: quedaría prometiendo un documento que no está.
        if (quedan.isEmpty()) {
            reunion.setCodigoActa(null);
        }
        reunionRepository.flush();

        TransaccionArchivos.alConfirmar(() -> almacen.borrar(clave));
        return reunionService.obtener(reunionId);
    }

    /**
     * Deja anotado el número del acta.
     * <p>
     * Con la primera hoja es obligatorio: el archivo que se sube es una foto de
     * una hoja del libro, y sin el número de esa acta nadie puede volver al
     * libro a cotejar lo que se decidió. Después es opcional, porque las hojas
     * que siguen son del mismo acta; si igual llega uno, corrige al anterior
     * —que se haya tipeado mal el número no es motivo para volver a subir todo.
     */
    private void aplicarCodigo(Reunion reunion, String codigo) {
        String limpio = Textos.limpiar(codigo);
        if (limpio == null) {
            if (Textos.limpiar(reunion.getCodigoActa()) == null) {
                throw new ReglaNegocioException(
                        "Falta el número del acta. Es el que está escrito en el libro, y sin él "
                        + "la foto no se puede cotejar con el original.");
            }
            return;
        }
        if (limpio.length() > 40) {
            throw new ReglaNegocioException(
                    "El número del acta no puede pasar de 40 caracteres.");
        }
        reunion.setCodigoActa(limpio);
    }

    private HojaActa buscarHoja(Reunion reunion, Long hojaId) {
        return reunion.getHojasActa().stream()
                .filter(h -> h.getId().equals(hojaId))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("hoja del acta", hojaId));
    }

    /**
     * De qué tipo es el archivo.
     * <p>
     * Se prefiere lo que declara quien sube, pero muchos selectores de archivo
     * mandan {@code application/octet-stream} —"unos bytes"— sin mirar nada.
     * Rechazar eso sería rechazar actas perfectamente válidas por un detalle
     * del cliente, así que en ese caso se deduce de la extensión.
     */
    private static String resolverTipo(String declarado, String nombreArchivo) {
        if (declarado != null
                && !declarado.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(declarado.trim())) {
            return declarado.trim().toLowerCase();
        }
        String nombre = nombreArchivo == null ? "" : nombreArchivo.toLowerCase();
        if (nombre.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (nombre.endsWith(".png")) {
            return "image/png";
        }
        if (nombre.endsWith(".webp")) {
            return "image/webp";
        }
        if (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return declarado == null ? "desconocido" : declarado;
    }

    private void verificar(byte[] contenido, String tipoMime) {
        if (contenido == null || contenido.length == 0) {
            throw new ArchivoInvalidoException("La hoja llegó vacía.");
        }
        if (contenido.length > PESO_MAXIMO) {
            throw new ArchivoInvalidoException(String.format(
                    "La hoja pesa %.1f MB y el tope son %d MB.",
                    contenido.length / 1024d / 1024d, PESO_MAXIMO / 1024 / 1024));
        }
        if (tipoMime == null || !TIPOS.contains(tipoMime.toLowerCase())) {
            throw new ArchivoInvalidoException(
                    "El acta tiene que ser un PDF o una imagen. Llegó: " + tipoMime);
        }
    }

    private static String nombreDe(String nombreArchivo) {
        String limpio = Textos.limpiar(nombreArchivo);
        return limpio == null ? "hoja" : limpio;
    }

    private static String extensionDe(String tipoMime) {
        return switch (tipoMime.toLowerCase()) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    Reunion buscar(Long id) {
        return reunionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("reunión", id));
    }
}
