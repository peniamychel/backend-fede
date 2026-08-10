package com.federa.backend.model.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Por qué un lote cambió de manos, o por qué un sistema se movió de lote.
 * <p>
 * Es una lista corta y a propósito: sirve para leer el historial de un vistazo
 * y para contar cuántos traspasos fueron ventas. Lo que no entra en estas
 * cinco se escribe en las observaciones.
 */
public enum MotivoTraspaso {

    VENTA("Venta"),
    HERENCIA("Herencia"),
    CESION("Cesión"),

    /**
     * El período anterior estaba mal cargado.
     * <p>
     * No es un traspaso de verdad, y por eso está separado: en un padrón que
     * se está saneando, corregir un error de carga es tan frecuente como una
     * venta, y mezclarlos haría que las cuentas de ventas mintieran.
     */
    CORRECCION("Corrección de datos"),

    OTRO("Otro");

    private final String etiqueta;

    MotivoTraspaso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static MotivoTraspaso desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        for (MotivoTraspaso m : values()) {
            if (m.name().equalsIgnoreCase(valor)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Motivo inválido: " + valor + ". Se espera "
                + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")) + ".");
    }
}
