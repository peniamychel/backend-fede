package com.federa.backend.dto;

import com.federa.backend.model.enums.DecisionConflictoUdestro;
import jakarta.validation.constraints.NotNull;

public record DecisionUdestroRequest(
        @NotNull(message = "la decisión es obligatoria")
        DecisionConflictoUdestro decision,
        Long productorId
) {
}
