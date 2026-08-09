package com.federa.backend.controller;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.config.ApiRutas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

import java.util.concurrent.TimeUnit;

/**
 * Sirve los archivos del almacén de objetos.
 * <p>
 * Va por un controlador y no por recursos estáticos de Spring por dos motivos:
 * el almacén puede dejar de ser el disco local sin que cambie nada de acá, y
 * este es el punto donde más adelante entra el control de acceso —hoy el padrón
 * está abierto, pero las fotos de personas no deberían quedar públicas para
 * siempre.
 */
@RestController
@RequestMapping(ApiRutas.V1 + "/archivos")
@Tag(name = "Archivos", description =
        "Entrega los archivos guardados en el servidor. Las URL las devuelve la API en cada "
        + "imagen; no hace falta armarlas a mano.")
public class ArchivoController {

    private final AlmacenObjetos almacen;

    public ArchivoController(AlmacenObjetos almacen) {
        this.almacen = almacen;
    }

    @GetMapping("/**")
    @Operation(summary = "Descarga un archivo por su clave",
            description = "La clave es el resto de la ruta. Responde con un ETag y permite "
                    + "cachear por mucho tiempo: como la clave incluye un identificador "
                    + "aleatorio, al reemplazar una imagen cambia la URL y nunca se sirve una "
                    + "versión vieja.")
    public ResponseEntity<byte[]> descargar(HttpServletRequest peticion) {
        String clave = extraerClave(peticion);
        byte[] contenido = almacen.leer(clave);

        return ResponseEntity.ok()
                // La clave es única por subida, así que el contenido de una URL
                // nunca cambia: se puede cachear agresivamente sin riesgo de
                // mostrar una foto desactualizada.
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .eTag("\"" + clave.hashCode() + "-" + contenido.length + "\"")
                .contentType(tipoPorExtension(clave))
                .contentLength(contenido.length)
                .body(contenido);
    }

    /**
     * Saca la clave de la ruta pedida.
     * <p>
     * Con {@code /**} Spring no entrega el comodín como variable, hay que
     * leerlo del atributo de la petición. La validación de la clave —que no se
     * salga del almacén— la hace el propio almacén, que es donde tiene que
     * estar.
     */
    private String extraerClave(HttpServletRequest peticion) {
        String ruta = (String) peticion.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefijo = ApiRutas.V1 + "/archivos/";
        return ruta.startsWith(prefijo) ? ruta.substring(prefijo.length()) : ruta;
    }

    private MediaType tipoPorExtension(String clave) {
        String minuscula = clave.toLowerCase();
        if (minuscula.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (minuscula.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        // Todo lo que guarda el procesador de imágenes sale como JPEG.
        return MediaType.IMAGE_JPEG;
    }
}
