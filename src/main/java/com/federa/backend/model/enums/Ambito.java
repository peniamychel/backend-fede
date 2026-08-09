package com.federa.backend.model.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nivel de la organización al que pertenece un cargo del directorio.
 * <p>
 * Cada nivel tiene su propio directorio y su propia lista de cargos. La lista
 * es acumulativa hacia arriba, que es como está organizada la federación: el
 * sindicato tiene lo mínimo y cada nivel superior suma responsabilidades.
 * <p>
 * De acá sale también de dónde salen los candidatos: del sindicato, de todos
 * los sindicatos de la central, o de toda la federación.
 */
public enum Ambito {

    SINDICATO("Sindicato", EnumSet.of(TipoCargo.PRESIDENTE, TipoCargo.SECRETARIO)),

    CENTRAL("Central", EnumSet.of(TipoCargo.PRESIDENTE, TipoCargo.SECRETARIO,
            TipoCargo.HACIENDAS)),

    FEDERACION("Federación", EnumSet.of(TipoCargo.PRESIDENTE, TipoCargo.SECRETARIO,
            TipoCargo.HACIENDAS, TipoCargo.VOCAL));

    private final String etiqueta;
    private final Set<TipoCargo> cargos;

    Ambito(String etiqueta, Set<TipoCargo> cargos) {
        this.etiqueta = etiqueta;
        this.cargos = Collections.unmodifiableSet(cargos);
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Los cargos de este nivel, en el orden en que se muestran. */
    public Set<TipoCargo> getCargos() {
        return cargos;
    }

    public boolean admite(TipoCargo cargo) {
        return cargos.contains(cargo);
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
