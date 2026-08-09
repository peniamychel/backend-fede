package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Región de la imagen que hay que conservar, en píxeles de la imagen
 * <b>original</b> tal como se subió.
 * <p>
 * Se manda el rectángulo y no la imagen ya recortada por el cliente para que el
 * corte se aplique sobre la foto en su resolución completa: recortar primero en
 * el navegador obligaría a trabajar sobre una versión ya reducida y se perdería
 * detalle justo en la parte que interesa.
 */
@Schema(name = "RecorteRequest", description = "Rectángulo a conservar de la imagen subida.")
public record RecorteRequest(

        @Schema(description = "Borde izquierdo, en píxeles desde la izquierda.", example = "820")
        int x,

        @Schema(description = "Borde superior, en píxeles desde arriba.", example = "410")
        int y,

        @Schema(description = "Ancho del recorte en píxeles.", example = "1800")
        int ancho,

        @Schema(description = "Alto del recorte en píxeles.", example = "2400")
        int alto
) {

    /** Si los cuatro valores describen un rectángulo utilizable. */
    public boolean esValido() {
        return ancho > 0 && alto > 0 && x >= 0 && y >= 0;
    }

    /** Si el rectángulo entra dentro de una imagen de ese tamaño. */
    public boolean entraEn(int anchoImagen, int altoImagen) {
        return x + ancho <= anchoImagen && y + alto <= altoImagen;
    }
}
