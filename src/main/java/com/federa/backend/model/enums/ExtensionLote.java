package com.federa.backend.model.enums;

import com.federa.backend.util.Textos;

/**
 * Columna "EXT" del padrón: letra que acompaña al número de lote cuando el
 * lote fue subdividido (66, 66-A, 66-B...).
 * <p>
 * En la planilla aparecen 416 registros con extensión: A (191), B (194),
 * C (23), D (7) y E (1).
 */
public enum ExtensionLote {

    A, B, C, D, E;

    public static ExtensionLote desde(String valor) {
        String v = Textos.normalizar(valor);
        if (v == null) {
            return null;
        }
        for (ExtensionLote ext : values()) {
            if (ext.name().equals(v)) {
                return ext;
            }
        }
        return null;
    }
}
