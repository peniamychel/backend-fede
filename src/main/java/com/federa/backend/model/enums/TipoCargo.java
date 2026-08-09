package com.federa.backend.model.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Cargos del directorio.
 * <p>
 * Los comparten los tres niveles, pero no todos valen en todos: qué cargos
 * admite cada uno lo dice {@link Ambito}. Un sindicato tiene presidente y
 * secretario; una central suma haciendas; la federación suma además vocal.
 */
public enum TipoCargo {

    PRESIDENTE("Presidente"),
    SECRETARIO("Secretario"),
    HACIENDAS("Haciendas"),
    VOCAL("Vocal");

    private final String etiqueta;

    TipoCargo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Si este cargo puede tener firma y pie de firma cargados.
     * <p>
     * Solo presidente y secretario, en los tres niveles: son los que firman los
     * documentos que emite la organización. Al resto no se le suben imágenes
     * porque no las usa nadie.
     */
    public boolean puedeFirmar() {
        return this == PRESIDENTE || this == SECRETARIO;
    }

    public static TipoCargo desde(String valor) {
        for (TipoCargo c : values()) {
            if (c.name().equalsIgnoreCase(valor)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Cargo inválido: " + valor + ". Se espera "
                + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")) + ".");
    }
}
