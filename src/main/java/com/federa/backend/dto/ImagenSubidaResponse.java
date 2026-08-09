package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Qué pasó al subir una foto.
 * <p>
 * Incluye el tamaño de lo que llegó además del de lo guardado, para que la app
 * pueda mostrar la reducción: es la forma de que el usuario entienda por qué no
 * hizo falta que comprimiera nada antes de subir.
 */
@Schema(name = "ImagenSubidaResponse",
        description = "Resultado de subir una foto: las dos variantes generadas y cuánto se "
                + "redujo respecto del archivo original.")
public record ImagenSubidaResponse(

        @Schema(description = "Peso del archivo tal como lo subió el usuario.",
                example = "4194304")
        int tamanoSubidoBytes,

        @Schema(description = "Ancho de la imagen subida, antes de procesar.", example = "4032")
        int anchoSubido,

        @Schema(description = "Alto de la imagen subida, antes de procesar.", example = "3024")
        int altoSubido,

        @Schema(description = "La foto ya reducida y comprimida, que es la que se guarda.")
        ImagenResponse original,

        @Schema(description = "La miniatura derivada, para los listados.")
        ImagenResponse miniatura
) {

    /** Cuánto se ahorró, en porcentaje, sumando las dos variantes. */
    @Schema(description = "Porcentaje de reducción respecto del archivo subido.", example = "88")
    public int porcentajeReduccion() {
        if (tamanoSubidoBytes <= 0) {
            return 0;
        }
        int guardado = original.tamanoBytes() + miniatura.tamanoBytes();
        return Math.max(0, 100 - (guardado * 100 / tamanoSubidoBytes));
    }
}
