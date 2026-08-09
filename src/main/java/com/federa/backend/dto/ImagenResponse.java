package com.federa.backend.dto;

import com.federa.backend.almacen.AlmacenLocal;
import com.federa.backend.model.ImagenProductor;
import com.federa.backend.model.enums.TipoImagen;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Datos de una imagen guardada. Nunca lleva los bytes: lleva la URL desde la
 * que se piden.
 */
@Schema(name = "ImagenResponse",
        description = "Metadata de una imagen y la URL donde está el archivo.")
public record ImagenResponse(

        @Schema(description = "Cuál de las dos variantes es.", example = "MINIATURA")
        TipoImagen tipo,

        @Schema(description = "Dirección del archivo, relativa al servidor. Es lo que se pone "
                + "en un <img>. Cambia al reemplazar la foto, así que el navegador nunca "
                + "muestra la anterior por caché.",
                example = "/api/v1/archivos/productores/15/MINIATURA-a1b2c3d4.jpg")
        String url,

        @Schema(description = "Tipo MIME, verificado al procesar la imagen.",
                example = "image/jpeg")
        String tipoMime,

        @Schema(description = "Peso del archivo en bytes.", example = "37769")
        int tamanoBytes,

        @Schema(description = "Ancho en píxeles.", example = "320")
        int ancho,

        @Schema(description = "Alto en píxeles.", example = "240")
        int alto,

        @Schema(description = "Nombre con el que se subió.", example = "constantina.jpg")
        String nombreOriginal,

        @Schema(description = "Última vez que se reemplazó.", example = "2026-08-08T10:12:03")
        LocalDateTime actualizadaEn
) {

    public static ImagenResponse desde(ImagenProductor imagen) {
        return new ImagenResponse(
                imagen.getTipo(),
                AlmacenLocal.RUTA_PUBLICA + imagen.getClave(),
                imagen.getTipoMime(),
                imagen.getTamanoBytes(),
                imagen.getAncho(),
                imagen.getAlto(),
                imagen.getNombreOriginal(),
                imagen.getActualizadaEn());
    }
}
