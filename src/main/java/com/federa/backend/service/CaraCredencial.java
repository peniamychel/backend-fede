package com.federa.backend.service;

/** Qué lado de una credencial se incluye en el PDF solicitado. */
public enum CaraCredencial {
    COMPLETA,
    ANVERSO,
    REVERSO;

    /** Sufijo legible para distinguir los trabajos de impresión de una cara. */
    public String sufijo() {
        return switch (this) {
            case COMPLETA -> "";
            case ANVERSO -> "-anverso";
            case REVERSO -> "-reverso";
        };
    }
}
