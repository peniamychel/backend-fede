package com.federa.backend.dto;

import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.TipoCargo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Directorio vigente de un sindicato, una central o la federación.
 * <p>
 * Devuelve un puesto por cada cargo que admite el nivel, ocupado o vacante. Así
 * la pantalla dibuja el directorio sin saber de antemano qué cargos tiene cada
 * nivel: si mañana la federación suma uno, aparece solo.
 */
@Schema(name = "DirectorioResponse",
        description = "Directorio vigente de un nivel de la organización.")
public record DirectorioResponse(

        @Schema(description = "Nivel al que pertenece.", example = "CENTRAL")
        Ambito ambito,

        @Schema(description = "Id del sindicato, central o federación.", example = "17")
        Long ambitoId,

        @Schema(description = "Su nombre.", example = "1RO DE MAYO")
        String ambitoNombre,

        @Schema(description = "Un puesto por cargo del nivel, en orden.")
        List<Puesto> puestos
) {

    /**
     * Un cargo del directorio y quién lo ocupa.
     *
     * @param actual      período vigente, o null si el puesto está vacante.
     * @param puedeFirmar si a este cargo se le pueden cargar firma y pie de
     *                    firma. Solo presidente y secretario: al resto la
     *                    pantalla ni siquiera le ofrece subirlas.
     */
    public record Puesto(

            @Schema(description = "Qué cargo.", example = "HACIENDAS")
            TipoCargo cargo,

            @Schema(description = "Cómo se escribe.", example = "Haciendas")
            String etiqueta,

            @Schema(description = "Si admite firma y pie de firma.", example = "false")
            boolean puedeFirmar,

            @Schema(description = "Quién lo ocupa. Null si está vacante.")
            CargoResponse actual
    ) {
    }

    /** Si todos los puestos del nivel están ocupados. */
    public boolean estaCompleto() {
        return puestos.stream().allMatch(p -> p.actual() != null);
    }
}
