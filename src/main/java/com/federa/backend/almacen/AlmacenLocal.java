package com.federa.backend.almacen;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.exception.RecursoNoEncontradoException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Almacén de objetos sobre el disco del servidor.
 * <p>
 * La raíz se configura con {@code federa.almacenamiento.raiz}. Conviene que
 * quede <b>fuera</b> del directorio de la aplicación en producción: así el
 * redespliegue no se lleva las fotos por delante.
 */
@Component
public class AlmacenLocal implements AlmacenObjetos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenLocal.class);

    /** Ruta pública bajo la que se sirven los objetos. */
    public static final String RUTA_PUBLICA = ApiRutas.V1 + "/archivos/";

    /**
     * Claves admitidas: segmentos de letras, números, guiones y puntos,
     * separados por barras.
     * <p>
     * Es una lista blanca y no una lista negra a propósito. Prohibir
     * {@code ".."} deja pasar variantes codificadas; exigir que la clave
     * <i>solo</i> tenga estos caracteres cierra la puerta a salir del
     * directorio raíz, que es la forma clásica de leer archivos del servidor
     * que no deberían verse.
     */
    private static final Pattern CLAVE_VALIDA =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*(/[A-Za-z0-9][A-Za-z0-9._-]*)*");

    private final Path raiz;

    public AlmacenLocal(
            @Value("${federa.almacenamiento.raiz:./almacenamiento}") String raizConfigurada) {
        this.raiz = Path.of(raizConfigurada).toAbsolutePath().normalize();
    }

    @PostConstruct
    void prepararDirectorio() {
        try {
            Files.createDirectories(raiz);
            log.info("Almacén de imágenes en {}", raiz);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudo crear el directorio del almacén: " + raiz, e);
        }
    }

    /** Dónde está la raíz, para poder mostrarlo en diagnósticos. */
    public Path getRaiz() {
        return raiz;
    }

    @Override
    public void guardar(String clave, byte[] contenido) {
        Path destino = resolver(clave);
        try {
            Files.createDirectories(destino.getParent());
            // Se escribe a un temporal y se mueve: si el proceso muere a mitad
            // de la escritura, no queda un archivo a medio grabar ocupando la
            // clave definitiva.
            Path temporal = Files.createTempFile(destino.getParent(), "tmp-", ".part");
            Files.write(temporal, contenido);
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el objeto " + clave, e);
        }
    }

    @Override
    public byte[] leer(String clave) {
        Path origen = resolver(clave);
        if (!Files.isRegularFile(origen)) {
            throw new RecursoNoEncontradoException("No existe el archivo " + clave);
        }
        try {
            return Files.readAllBytes(origen);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el objeto " + clave, e);
        }
    }

    @Override
    public void borrar(String clave) {
        try {
            Files.deleteIfExists(resolver(clave));
        } catch (IOException e) {
            // Un borrado fallido deja un archivo huérfano, molesto pero
            // inofensivo. Tumbar la operación por eso sería peor: el registro
            // ya se borró y el usuario no puede hacer nada al respecto.
            log.warn("No se pudo borrar el objeto {}: {}", clave, e.getMessage());
        }
    }

    @Override
    public boolean existe(String clave) {
        return Files.isRegularFile(resolver(clave));
    }

    @Override
    public String urlPublica(String clave) {
        return RUTA_PUBLICA + clave;
    }

    /**
     * Convierte una clave en una ruta real, verificando que no se salga de la
     * raíz.
     * <p>
     * La comprobación final contra {@code raiz} es una segunda barrera: aunque
     * el patrón ya impide los {@code ".."}, un error futuro en esa expresión no
     * debería alcanzar para exponer el disco entero.
     */
    private Path resolver(String clave) {
        if (clave == null || !CLAVE_VALIDA.matcher(clave).matches()) {
            throw new IllegalArgumentException("Clave de objeto inválida: " + clave);
        }
        Path destino = raiz.resolve(clave).normalize();
        if (!destino.startsWith(raiz)) {
            throw new IllegalArgumentException(
                    "La clave apunta fuera del almacén: " + clave);
        }
        return destino;
    }
}
