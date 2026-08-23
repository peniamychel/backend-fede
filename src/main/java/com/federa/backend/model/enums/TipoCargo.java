package com.federa.backend.model.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Cargos del directorio.
 * <p>
 * Los comparten los tres niveles, pero no todos valen en todos: qué cargos
 * admite cada uno y en qué orden lo dice {@link Ambito}.
 */
public enum TipoCargo {

    EJECUTIVO("Ejecutivo"),
    SECRETARIO_GENERAL("Secretario General"),
    SECRETARIO_RELACIONES("Secretario Relaciones"),
    HACIENDAS("Haciendas"),
    VOCAL("Vocal"),

    /** Valores históricos, conservados para poder migrar bases anteriores. */
    @Deprecated PRESIDENTE("Presidente (histórico)"),
    @Deprecated SECRETARIO("Secretario (histórico)");

    private final String etiqueta;

    TipoCargo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean esHistorico() {
        return this == PRESIDENTE || this == SECRETARIO;
    }

    public static TipoCargo desde(String valor) {
        for (TipoCargo c : values()) {
            if (!c.esHistorico() && c.name().equalsIgnoreCase(valor)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Cargo inválido: " + valor + ". Se espera "
                + Arrays.stream(values()).filter(c -> !c.esHistorico())
                .map(Enum::name).collect(Collectors.joining(", ")) + ".");
    }
}
