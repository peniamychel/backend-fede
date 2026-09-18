package com.federa.backend.service;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import java.awt.Color;
import java.io.InputStream;

/** Fuentes embebidas: igual resultado en Windows, Docker y sin Internet. */
final class FuentesInforme {
    private static final BaseFont REGULAR = cargar("Regular");
    private static final BaseFont NEGRITA = cargar("Bold");

    private FuentesInforme() {}

    static BaseFont regular() { return REGULAR; }

    static Font fuente(float tamano, int estilo, Color color) {
        return new Font((estilo & Font.BOLD) != 0 ? NEGRITA : REGULAR,
                tamano, estilo & ~Font.BOLD, color);
    }

    private static BaseFont cargar(String variante) {
        String recurso = "/fonts/informes/RobotoCondensed-" + variante + ".ttf";
        try (InputStream entrada = FuentesInforme.class.getResourceAsStream(recurso)) {
            if (entrada == null) throw new IllegalStateException("Falta la fuente " + recurso);
            return BaseFont.createFont(recurso, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, entrada.readAllBytes(), null);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo cargar " + recurso, error);
        }
    }
}
