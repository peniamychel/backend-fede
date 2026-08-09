package com.federa.backend.model.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Los cuatro tipos de reunión, con quién las convoca y a quién convocan.
 * <p>
 * Son las cuatro que existen en la organización, y cada una tiene su propia
 * lista de convocados. De acá sale contra qué lista se contrasta un carnet al
 * pasar lista: escanear el de un afiliado cualquiera en una reunión de
 * dirigentes tiene que dar error, no sumar un presente.
 */
public enum TipoReunion {

    /** Del sindicato. Asisten sus propios productores. */
    SINDICATO(Ambito.SINDICATO, "Reunión de sindicato",
            "Asisten los productores del sindicato.", false),

    /**
     * Ampliado de la central: el sindicato entero, todos los sindicatos.
     * <p>
     * Es la reunión más grande, y por eso la lista puede tener cientos de
     * personas.
     */
    AMPLIADO(Ambito.CENTRAL, "Ampliado de central",
            "Asisten todos los productores de los sindicatos de la central.", false),

    /** Convocada por la central, solo para los dirigentes de sus sindicatos. */
    DIRIGENTES_CENTRAL(Ambito.CENTRAL, "Dirigentes de la central",
            "Asisten los presidentes y secretarios de los sindicatos de la central.", true),

    /**
     * Convocada por la federación, para los dirigentes de arriba y de abajo.
     * <p>
     * Incluye a los de las centrales y a los de los sindicatos: es la que reúne
     * a toda la dirigencia.
     */
    DIRIGENTES_FEDERACION(Ambito.FEDERACION, "Dirigentes de la federación",
            "Asisten los presidentes y secretarios de las centrales y de los sindicatos.",
            true);

    private final Ambito convoca;
    private final String etiqueta;
    private final String detalle;
    private final boolean soloDirigentes;

    TipoReunion(Ambito convoca, String etiqueta, String detalle, boolean soloDirigentes) {
        this.convoca = convoca;
        this.etiqueta = etiqueta;
        this.detalle = detalle;
        this.soloDirigentes = soloDirigentes;
    }

    /** Qué nivel la convoca, y por lo tanto de dónde cuelga la reunión. */
    public Ambito getConvoca() {
        return convoca;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getDetalle() {
        return detalle;
    }

    /**
     * Si la convocatoria es solo para quienes ocupan un cargo.
     * <p>
     * Cambia por completo la lista: en las de dirigentes son unas pocas
     * personas y en las otras es el padrón del nivel.
     */
    public boolean esSoloDirigentes() {
        return soloDirigentes;
    }

    public static TipoReunion desde(String valor) {
        for (TipoReunion t : values()) {
            if (t.name().equalsIgnoreCase(valor)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de reunión inválido: " + valor + ". Se espera "
                + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")) + ".");
    }
}
