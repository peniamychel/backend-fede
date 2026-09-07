package com.federa.backend.dto;

import jakarta.validation.constraints.NotNull;

/** Datos mostrados al usuario para impedir confirmar una ficha que ya cambió. */
public record ConfirmacionSieRequest(
        @NotNull Boolean aceptar,
        @NotNull RevisionSieProductorResponse.Datos actuales,
        @NotNull RevisionSieProductorResponse.Datos propuestos
) {}
