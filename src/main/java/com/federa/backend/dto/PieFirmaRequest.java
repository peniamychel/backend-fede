package com.federa.backend.dto;

import jakarta.validation.constraints.Size;

/** Texto institucional que se muestra debajo de la firma de un dirigente. */
public record PieFirmaRequest(
        @Size(max = 200, message = "El pie de firma no puede superar 200 caracteres")
        String pieFirma
) {
}
