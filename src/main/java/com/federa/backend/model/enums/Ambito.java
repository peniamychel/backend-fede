package com.federa.backend.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Nivel de la organización al que pertenece un cargo del directorio.
 * <p>
 * Cada nivel tiene su propio directorio, su lista de cargos y su orden.
 * <p>
 * De acá sale también de dónde salen los candidatos: del sindicato, de todos
 * los sindicatos de la central, o de toda la federación.
 */
public enum Ambito {

    SINDICATO("Sindicato", List.of(
            TipoCargo.SECRETARIO_GENERAL,
            TipoCargo.SECRETARIO_RELACIONES,
            TipoCargo.HACIENDAS,
            TipoCargo.VOCAL)),

    CENTRAL("Central", List.of(
            TipoCargo.SECRETARIO_GENERAL,
            TipoCargo.SECRETARIO_RELACIONES,
            TipoCargo.HACIENDAS,
            TipoCargo.VOCAL)),

    FEDERACION("Federación", List.of(
            TipoCargo.EJECUTIVO,
            TipoCargo.SECRETARIO_GENERAL,
            TipoCargo.SECRETARIO_RELACIONES,
            TipoCargo.HACIENDAS,
            TipoCargo.VOCAL));

    private final String etiqueta;
    private final List<TipoCargo> cargos;

    Ambito(String etiqueta, List<TipoCargo> cargos) {
        this.etiqueta = etiqueta;
        this.cargos = List.copyOf(cargos);
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Los cargos de este nivel, en el orden en que se muestran. */
    public List<TipoCargo> getCargos() {
        return cargos;
    }

    public boolean admite(TipoCargo cargo) {
        return cargos.contains(cargo);
    }

    /** El cargo cuya firma aparece en el reverso de la credencial. */
    public boolean puedeFirmar(TipoCargo cargo) {
        return switch (this) {
            case SINDICATO, CENTRAL -> cargo == TipoCargo.SECRETARIO_GENERAL;
            case FEDERACION -> cargo == TipoCargo.EJECUTIVO;
        };
    }

    public static Ambito desde(String valor) {
        for (Ambito a : values()) {
            if (a.name().equalsIgnoreCase(valor)) {
                return a;
            }
        }
        throw new IllegalArgumentException("Ámbito inválido: " + valor + ". Se espera "
                + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")) + ".");
    }
}
