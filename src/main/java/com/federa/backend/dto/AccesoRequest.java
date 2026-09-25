package com.federa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccesoRequest(
        @NotBlank(message = "Ingresá el código de acceso")
        @Size(max = 100, message = "El código de acceso no es válido")
        String codigo) {
}
