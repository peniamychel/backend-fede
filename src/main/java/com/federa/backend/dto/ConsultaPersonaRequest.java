package com.federa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConsultaPersonaRequest(
        @NotBlank(message = "la cédula es obligatoria")
        @Size(max = 20, message = "la cédula no puede superar los 20 caracteres")
        String ci
) {
}
