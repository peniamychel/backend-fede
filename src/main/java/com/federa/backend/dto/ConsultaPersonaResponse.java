package com.federa.backend.dto;

public record ConsultaPersonaResponse(
        Estado estado,
        String nombres,
        String apellidos,
        String mensaje
) {
    public enum Estado {
        ENCONTRADA,
        NO_ENCONTRADA,
        NO_DISPONIBLE
    }
}
