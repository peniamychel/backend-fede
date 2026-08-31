package com.federa.backend.util;

import com.federa.backend.model.Lote;
import com.federa.backend.model.enums.ExtensionLote;

/** Arma la identificación visible de una parcela. */
public final class CodigoLote {

    private CodigoLote() {
    }

    /**
     * Conserva una subdivisión histórica como {@code 22-A} y agrega después,
     * separada por espacio, la letra automática del tenedor: {@code 22-A B}.
     * En el caso habitual, sin subdivisión histórica, devuelve {@code 22 B}.
     */
    public static String de(Lote lote, String letraCompartida) {
        if (lote == null) {
            return null;
        }
        return de(lote.getNumero(), lote.getExtension(), letraCompartida);
    }

    public static String de(String numero, ExtensionLote extension, String letraCompartida) {
        if (numero == null || numero.isBlank()) {
            return null;
        }
        String base = extension == null
                ? numero.trim()
                : numero.trim() + "-" + extension.name();
        return letraCompartida == null || letraCompartida.isBlank()
                ? base
                : base + " " + letraCompartida.trim().toUpperCase();
    }
}
