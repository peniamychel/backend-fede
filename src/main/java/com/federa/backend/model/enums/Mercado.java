package com.federa.backend.model.enums;

import com.federa.backend.util.Textos;

/**
 * Columna "MERCADO" del padrón.
 * <p>
 * Hoy la planilla solo usa el valor DETALLISTA (55 registros, repartidos en las
 * centrales VALLE HERMOSO, E. MORALES, 1RO DE MAYO, VILLA VERDE, TAMBORADA y
 * VALLE IVIRZA). Se modela como enum para poder sumar otros mercados sin tocar
 * el esquema de la tabla.
 */
public enum Mercado {

    DETALLISTA;

    public static Mercado desde(String valor) {
        String v = Textos.normalizar(valor);
        if (v == null) {
            return null;
        }
        for (Mercado mercado : values()) {
            if (mercado.name().equals(v)) {
                return mercado;
            }
        }
        return null;
    }
}
