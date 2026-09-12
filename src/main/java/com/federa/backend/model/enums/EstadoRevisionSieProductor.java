package com.federa.backend.model.enums;

/** Resultado persistente de la revisión de identidad de un productor. */
public enum EstadoRevisionSieProductor {
    VERIFICADO(false),
    CORREGIDO_SIE(false),
    CORREGIDO_MANUAL(false),
    APROBADO_MANUAL(false),
    DIFERENCIA_PENDIENTE(true),
    NO_ENCONTRADO(true),
    SIN_CEDULA(true);

    private final boolean bloqueaImpresion;

    EstadoRevisionSieProductor(boolean bloqueaImpresion) {
        this.bloqueaImpresion = bloqueaImpresion;
    }

    public boolean bloqueaImpresion() {
        return bloqueaImpresion;
    }
}
