package com.federa.backend.model.enums;

/**
 * Las dos imágenes que acompañan a un cargo del directorio.
 * <p>
 * Van atadas al <b>período</b> y no a la persona: la firma con la que alguien
 * autorizó documentos siendo presidente pertenece a ese mandato. Si vuelve a
 * asumir años después, se carga la que corresponda entonces, y la anterior
 * queda con su período en el historial.
 */
public enum TipoImagenCargo {

    /** La firma manuscrita. */
    FIRMA("firmas", "Firma"),

    /** El pie de firma: el sello o la línea con nombre y cargo. */
    PIE_FIRMA("pies-firma", "Pie de firma");

    /**
     * Lado mayor en píxeles. Entran en 200×200 conservando la proporción: una
     * firma apaisada queda 200 de ancho y lo que corresponda de alto.
     */
    public static final int LADO_MAXIMO = 200;

    /** Tope de peso del PNG. A 200 píxeles cualquier firma queda muy por debajo. */
    public static final int PESO_MAXIMO = 200 * 1024;

    private final String directorio;
    private final String etiqueta;

    TipoImagenCargo(String directorio, String etiqueta) {
        this.directorio = directorio;
        this.etiqueta = etiqueta;
    }

    public String getDirectorio() {
        return directorio;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static TipoImagenCargo desde(String valor) {
        for (TipoImagenCargo t : values()) {
            if (t.name().equalsIgnoreCase(valor)
                    || t.name().replace("_", "-").equalsIgnoreCase(valor)) {
                return t;
            }
        }
        throw new IllegalArgumentException(
                "Tipo de imagen inválido: " + valor + ". Se espera FIRMA o PIE_FIRMA.");
    }
}
