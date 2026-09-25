package com.federa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cambia el número y, opcionalmente, reserva una letra A-H para la tenencia. */
public record NumeroLoteRequest(@NotBlank @Size(max = 20) String numero, Long loteId,
                                String letra) {
    public NumeroLoteRequest(String numero, Long loteId) {
        this(numero, loteId, null);
    }
}
