package com.federa.backend.service;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;

import java.awt.Color;

/**
 * Primitivas para dibujar tarjetas del tamaño de una cédula.
 * <p>
 * Las comparten la credencial del productor y la del dirigente, que son la
 * misma técnica con otra disposición: coordenadas absolutas sobre la hoja, y no
 * tablas, porque a este tamaño cada punto cuenta y una tabla decide por su
 * cuenta dónde parte el texto.
 */
final class DibujoTarjeta {

    /** 85,6 mm en puntos: el lado largo de una tarjeta CR80. */
    static final float LADO_LARGO = 242.65f;
    /** 53,98 mm: el lado corto. */
    static final float LADO_CORTO = 153.01f;

    static final Color VERDE = new Color(20, 74, 58);
    static final Color VERDE_CLARO = new Color(224, 236, 231);
    static final Color GRIS_FONDO = new Color(238, 238, 238);
    static final Color GRIS_LINEA = new Color(170, 170, 170);
    static final Color GRIS_ROTULO = new Color(115, 115, 115);

    private DibujoTarjeta() {
    }

    static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }

    /**
     * Devuelve la fuente encogida lo necesario para que el texto entre.
     * <p>
     * Un apellido largo en una credencial no se puede cortar: el documento
     * tiene que decir el nombre entero. Por eso se achica la letra en vez de
     * recortar el texto, con un piso para que siga siendo legible.
     */
    static Font ajustar(String texto, Font base, float anchoMaximo) {
        if (texto == null || texto.isBlank()) {
            return base;
        }
        float ancho = ColumnText.getWidth(new Phrase(texto, base));
        if (ancho <= anchoMaximo || ancho <= 0f) {
            return base;
        }
        float tamano = Math.max(base.getSize() * anchoMaximo / ancho, 4.5f);
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, base.getStyle(),
                base.getColor());
    }

    /**
     * Convierte bytes en una imagen escalada, o null si no se pudo.
     * <p>
     * No se propaga el error: una imagen rota no puede impedir emitir la
     * credencial. Sin ella la tarjeta sigue sirviendo; sin credencial la
     * persona no tiene nada.
     */
    static Image imagen(byte[] contenido, float ancho, float alto) {
        if (contenido == null || contenido.length == 0) {
            return null;
        }
        try {
            Image imagen = Image.getInstance(contenido);
            imagen.scaleToFit(ancho, alto);
            return imagen;
        } catch (Exception e) {
            return null;
        }
    }

    /** Coloca la imagen centrada dentro del rectángulo dado. */
    static void colocarCentrada(PdfContentByte lienzo, Image imagen,
                                float x, float y, float ancho, float alto) {
        imagen.setAbsolutePosition(
                x + (ancho - imagen.getScaledWidth()) / 2f,
                y + (alto - imagen.getScaledHeight()) / 2f);
        try {
            lienzo.addImage(imagen);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo colocar una imagen en la tarjeta", e);
        }
    }

    static void centrado(PdfContentByte lienzo, String texto, Font fuente, float x, float y) {
        ColumnText.showTextAligned(lienzo, Element.ALIGN_CENTER,
                new Phrase(texto == null ? "" : texto, fuente), x, y, 0f);
    }

    static void izquierda(PdfContentByte lienzo, String texto, Font fuente, float x, float y) {
        ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT,
                new Phrase(texto == null ? "" : texto, fuente), x, y, 0f);
    }

    static void relleno(PdfContentByte lienzo, Color color,
                        float x, float y, float ancho, float alto) {
        lienzo.setColorFill(color);
        lienzo.rectangle(x, y, ancho, alto);
        lienzo.fill();
    }

    /**
     * Contorno de la tarjeta, que además marca por dónde recortar.
     * <p>
     * Se dibuja al final, encima de las bandas de color, y es un rectángulo
     * recto: las bandas llegan hasta el borde, así que unas esquinas curvas
     * dejarían ver el color por fuera del contorno. Además, una línea recta es
     * mejor guía para la tijera.
     */
    static void marco(PdfContentByte lienzo, float x, float y, float ancho, float alto) {
        lienzo.setColorStroke(GRIS_LINEA);
        lienzo.setLineWidth(0.5f);
        lienzo.rectangle(x + 0.25f, y + 0.25f, ancho - 0.5f, alto - 0.5f);
        lienzo.stroke();
    }
}
