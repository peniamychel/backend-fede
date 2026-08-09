package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo que se devuelve al escanear un carnet.
 * <p>
 * Devuelve 200 también cuando el carnet ya estaba registrado, en vez de un
 * error: para quien está pasando lista con el teléfono en la mano, "ya estaba"
 * no es un fallo sino información. Lo que distingue los casos es
 * {@link #resultado()}, y la pantalla lo usa para el color del aviso.
 *
 * @param resultado REGISTRADO la primera vez, REPETIDO si ya estaba.
 * @param presentes cuántos van, para mostrar el recuento sin otra consulta.
 */
@Schema(description = "Resultado de escanear un carnet.")
public record RegistroAsistenciaResponse(

        @Schema(description = "REGISTRADO o REPETIDO.", example = "REGISTRADO")
        Resultado resultado,

        @Schema(description = "Mensaje listo para mostrar.",
                example = "CANDIDO COLQUECHAMBI MAMANI quedó registrado.")
        String mensaje,

        @Schema(description = "Quién es, para confirmar de un vistazo que el "
                + "carnet escaneado es el que se tenía en la mano.")
        ConvocadoResponse persona,

        @Schema(description = "Presentes hasta ahora.", example = "32")
        int presentes,

        @Schema(description = "Total convocado.", example = "48")
        int convocados
) {

    public enum Resultado {
        /** Se sumó a la lista ahora. */
        REGISTRADO,
        /** Ya estaba: el carnet se escaneó dos veces. */
        REPETIDO
    }
}
