package com.federa.backend.service;

import com.federa.backend.dto.CredencialProductor;
import com.federa.backend.dto.DisenoCredencial;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Dibuja las credenciales de los productores, del tamaño de una cédula.
 * <p>
 * La medida es la del estándar CR80, la misma de una cédula o una tarjeta de
 * banco: 85,6 × 54 mm, apaisada. Se respeta al punto para que salga de una
 * impresora de tarjetas, y para que impresa en hoja y recortada entre igual en
 * cualquier portacredencial.
 * <p>
 * Todo se dibuja con coordenadas absolutas sobre la hoja, y no con tablas: a
 * este tamaño cada punto cuenta, y una tabla decide por su cuenta dónde parte
 * el texto. Los dos métodos de dibujo reciben la esquina de la tarjeta, así que
 * sirven tanto para una credencial sola como para una hoja llena de ellas.
 */
@Component
public class CredencialProductorPdf {

    /** 85,6 mm en puntos. */
    public static final float ANCHO = 242.65f;
    /** 53,98 mm en puntos. */
    public static final float ALTO = 153.01f;

    private static final float MARGEN = 8f;

    /**
     * Rejilla del pliego: dos columnas por cuatro filas, ocho por hoja.
     * <p>
     * No entran cinco filas. Cinco tarjetas son 765 puntos y una carta tiene
     * 792: con la separación para recortar se pasa, y la fila de arriba sale
     * cortada. Con cuatro sobran 72 puntos de margen arriba y abajo, que
     * además es zona que ninguna impresora se come.
     */
    static final int COLUMNAS = 2;
    static final int FILAS = 4;
    static final int POR_HOJA = COLUMNAS * FILAS;
    /** Separación entre tarjetas, para que la tijera tenga por dónde entrar. */
    static final float AIRE = 12f;

    private static final Color GRIS_LINEA = new Color(170, 170, 170);
    private static final Font ROTULO = fuente(5.5f, Font.NORMAL, new Color(115, 115, 115));
    private static final Font VALOR = fuente(8f, Font.BOLD, Color.BLACK);
    private static final Font DATO_PLANTILLA = fuente(8f, Font.BOLD, Color.BLACK);
    private static final Font NUMERO_PLANTILLA = fuente(10f, Font.BOLD, new Color(90, 15, 10));
    private static final Font FIRMA_NOMBRE = fuente(4.2f, Font.BOLD, Color.BLACK);
    private static final Font FIRMA_CARGO = fuente(4f, Font.BOLD, Color.BLACK);
    private static final Font FIRMA_ORGANIZACION = fuente(3.8f, Font.NORMAL, Color.BLACK);

    private static final String PLANTILLA_CARA = "/plantillas/credencial/cara.jpg";
    private static final String PLANTILLA_REVERSO = "/plantillas/credencial/reverso.jpg";

    /** Las copias conservan el mismo serial y OpenPDF incrusta cada fondo una sola vez. */
    private final Image plantillaCara = cargarPlantilla(PLANTILLA_CARA);
    private final Image plantillaReverso = cargarPlantilla(PLANTILLA_REVERSO);

    // -------------------------------------------------------- documentos

    /**
     * Una credencial suelta: dos páginas del tamaño exacto de la tarjeta,
     * anverso y reverso.
     * <p>
     * Sirve para una impresora de tarjetas, y también para imprimir a doble
     * cara en tamaño real y recortar.
     */
    public byte[] generar(CredencialProductor credencial) {
        return generar(credencial, DisenoCredencial.porDefecto());
    }

    public byte[] generar(CredencialProductor credencial, DisenoCredencial diseno) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(new Rectangle(ANCHO, ALTO), 0, 0, 0, 0);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            documento.open();
            PdfContentByte lienzo = escritor.getDirectContent();

            dibujarAnverso(lienzo, 0, 0, credencial, diseno);
            // Sin esto la página se descarta: para el documento está vacía,
            // porque todo se pintó directamente sobre la hoja.
            escritor.setPageEmpty(false);
            documento.newPage();
            dibujarReverso(lienzo, 0, 0, credencial, diseno);
            escritor.setPageEmpty(false);

            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "No se pudo generar la credencial de " + credencial.apellidos(), e);
        }
        return salida.toByteArray();
    }

    /**
     * Varias credenciales en hojas carta, para imprimir de a tandas.
     * <p>
     * Van dos por fila y cuatro filas, ocho por hoja. Primero la hoja de
     * anversos y después la de sus reversos, con las columnas invertidas: al
     * dar vuelta el papel por el lado largo, cada reverso cae detrás de su
     * anverso. Si se imprime a una sola cara, las hojas de reverso se pueden
     * descartar.
     */
    public byte[] generarPliego(List<CredencialProductor> credenciales) {
        return generarPliego(credenciales, DisenoCredencial.porDefecto());
    }

    public byte[] generarPliego(List<CredencialProductor> credenciales,
                                DisenoCredencial diseno) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.LETTER, 0, 0, 0, 0);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            documento.open();
            PdfContentByte lienzo = escritor.getDirectContent();

            for (int desde = 0; desde < credenciales.size(); desde += POR_HOJA) {
                List<CredencialProductor> tanda = credenciales.subList(
                        desde, Math.min(desde + POR_HOJA, credenciales.size()));

                if (desde > 0) {
                    documento.newPage();
                }
                dibujarHoja(lienzo, tanda, false, diseno);
                escritor.setPageEmpty(false);

                documento.newPage();
                dibujarHoja(lienzo, tanda, true, diseno);
                escritor.setPageEmpty(false);
            }
            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el pliego de credenciales", e);
        }
        return salida.toByteArray();
    }

    private void dibujarHoja(PdfContentByte lienzo, List<CredencialProductor> tanda,
                             boolean reverso, DisenoCredencial diseno) {
        for (int i = 0; i < tanda.size(); i++) {
            float[] esquina = posicionEnHoja(i, reverso);
            if (reverso) {
                dibujarReverso(lienzo, esquina[0], esquina[1], tanda.get(i), diseno);
            } else {
                dibujarAnverso(lienzo, esquina[0], esquina[1], tanda.get(i), diseno);
            }
        }
    }

    /**
     * Esquina inferior izquierda de la tarjeta número {@code indice} dentro de
     * la hoja, como {x, y}.
     * <p>
     * La rejilla va centrada en la carta, y la fila arranca siempre en la misma
     * altura aunque la hoja quede a medio llenar: así todas las hojas se
     * recortan igual.
     * <p>
     * En el reverso la columna se invierte. Ese es el detalle del que depende
     * la impresión a doble cara: al voltear el papel por el lado largo, lo que
     * se imprimió a la izquierda queda a la derecha, así que hay que
     * imprimirlo del otro lado para que caiga detrás de su anverso.
     */
    static float[] posicionEnHoja(int indice, boolean reverso) {
        float anchoBloque = COLUMNAS * ANCHO + (COLUMNAS - 1) * AIRE;
        float altoBloque = FILAS * ALTO + (FILAS - 1) * AIRE;
        float izquierda = (PageSize.LETTER.getWidth() - anchoBloque) / 2f;
        float arriba = (PageSize.LETTER.getHeight() + altoBloque) / 2f;

        int fila = (indice % POR_HOJA) / COLUMNAS;
        int columna = indice % COLUMNAS;
        int columnaImpresa = reverso ? COLUMNAS - 1 - columna : columna;

        return new float[]{
                izquierda + columnaImpresa * (ANCHO + AIRE),
                arriba - (fila + 1) * ALTO - fila * AIRE};
    }

    // ---------------------------------------------------------- anverso

    /** Pinta el anverso con la esquina inferior izquierda en (x, y). */
    public void dibujarAnverso(PdfContentByte lienzo, float x, float y,
                               CredencialProductor c) {
        dibujarAnverso(lienzo, x, y, c, DisenoCredencial.porDefecto());
    }

    public void dibujarAnverso(PdfContentByte lienzo, float x, float y,
                               CredencialProductor c, DisenoCredencial diseno) {
        fondo(lienzo, x, y, plantillaCara);
        dibujarElementos(lienzo, x, y, c, diseno, DisenoCredencial.Cara.CARA);
        marco(lienzo, x, y);
    }

    // ---------------------------------------------------------- reverso

    /** Pinta el reverso con la esquina inferior izquierda en (x, y). */
    public void dibujarReverso(PdfContentByte lienzo, float x, float y,
                               CredencialProductor c) {
        dibujarReverso(lienzo, x, y, c, DisenoCredencial.porDefecto());
    }

    public void dibujarReverso(PdfContentByte lienzo, float x, float y,
                               CredencialProductor c, DisenoCredencial diseno) {
        fondo(lienzo, x, y, plantillaReverso);
        dibujarElementos(lienzo, x, y, c, diseno, DisenoCredencial.Cara.REVERSO);
        marco(lienzo, x, y);
    }

    private void dibujarElementos(PdfContentByte lienzo, float origenX, float origenY,
                                  CredencialProductor c, DisenoCredencial diseno,
                                  DisenoCredencial.Cara cara) {
        for (DisenoCredencial.Elemento e : diseno.elementos()) {
            if (e.cara() != cara) continue;
            if (e.tipo() == DisenoCredencial.Tipo.TEXTO) {
                dibujarTexto(lienzo, origenX, origenY, c, e);
            } else if (e.tipo() == DisenoCredencial.Tipo.IMAGEN) {
                dibujarImagen(lienzo, origenX, origenY, c, e);
            } else if (e.tipo() == DisenoCredencial.Tipo.PIE_FIRMA) {
                dibujarPie(lienzo, origenX, origenY, firmante(c, e.campo()), e);
            }
        }
    }

    private void dibujarTexto(PdfContentByte lienzo, float origenX, float origenY,
                              CredencialProductor c, DisenoCredencial.Elemento e) {
        String texto = valorCampo(c, e);
        Font base = fuente(e.tamanoFuente(), e.negrita() ? Font.BOLD : Font.NORMAL,
                color(e.color()));
        Font ajustada = ajustar(texto, base, e.ancho());
        float px = origenX + e.x();
        float py = origenY + e.y();
        if (e.alineacion() == DisenoCredencial.Alineacion.CENTRO) {
            centrado(lienzo, texto, ajustada, px + e.ancho() / 2f, py);
        } else if (e.alineacion() == DisenoCredencial.Alineacion.DERECHA) {
            derecha(lienzo, texto, ajustada, px + e.ancho(), py);
        } else {
            izquierda(lienzo, texto, ajustada, px, py);
        }
    }

    private void dibujarImagen(PdfContentByte lienzo, float origenX, float origenY,
                               CredencialProductor c, DisenoCredencial.Elemento e) {
        float px = origenX + e.x();
        float py = origenY + e.y();
        Image imagen = imagen(bytesImagen(c, e.campo()), e.ancho(), e.alto());
        if (imagen != null) {
            imagen.setAbsolutePosition(px + (e.ancho() - imagen.getScaledWidth()) / 2f,
                    py + (e.alto() - imagen.getScaledHeight()) / 2f);
            agregar(lienzo, imagen);
        } else if ("FOTO".equals(e.campo())) {
            Font aviso = fuente(Math.min(5.5f, e.tamanoFuente()), Font.BOLD, Color.BLACK);
            centrado(lienzo, "SIN FOTO", aviso, px + e.ancho() / 2f,
                    py + e.alto() / 2f - 3f);
        }
    }

    private void dibujarPie(PdfContentByte lienzo, float origenX, float origenY,
                            CredencialProductor.Firmante firmante,
                            DisenoCredencial.Elemento e) {
        float x = origenX + e.x();
        float y = origenY + e.y();
        float ancho = e.ancho();
        float alto = e.alto();
        lienzo.setColorFill(Color.WHITE);
        lienzo.rectangle(x, y, ancho, alto);
        lienzo.fill();
        lienzo.setColorStroke(Color.BLACK);
        lienzo.setLineWidth(0.6f);
        lienzo.moveTo(x, y + alto);
        lienzo.lineTo(x + ancho, y + alto);
        lienzo.stroke();

        if (firmante == null) {
            Font aviso = fuente(e.tamanoFuente(), Font.BOLD, color(e.color()));
            centrado(lienzo, "SIN FIRMANTE", aviso, x + ancho / 2f, y + alto * .48f);
            return;
        }
        float factor = alto / 21f;
        Font nombre = fuente(e.tamanoFuente(), Font.BOLD, color(e.color()));
        Font cargo = fuente(Math.max(3f, e.tamanoFuente() - .2f), Font.BOLD, color(e.color()));
        Font organizacion = fuente(Math.max(3f, e.tamanoFuente() - .4f), Font.NORMAL,
                color(e.color()));
        centrado(lienzo, firmante.nombre(),
                ajustar(firmante.nombre(), nombre, ancho),
                x + ancho / 2f, y + 15f * factor);
        centrado(lienzo, firmante.cargo(),
                ajustar(firmante.cargo(), cargo, ancho),
                x + ancho / 2f, y + 10f * factor);
        centrado(lienzo, firmante.organizacion(),
                ajustar(firmante.organizacion(), organizacion, ancho),
                x + ancho / 2f, y + 5f * factor);
    }

    private String valorCampo(CredencialProductor c, DisenoCredencial.Elemento e) {
        String texto = switch (e.campo()) {
            case "CODIGO_PADRON" -> c.codigoPadron();
            case "NOMBRE_COMPLETO" -> unirNombre(c.nombres(), c.apellidos());
            case "NOMBRES" -> c.nombres();
            case "APELLIDOS" -> c.apellidos();
            case "CI" -> c.ci();
            case "SINDICATO" -> c.sindicato();
            case "CENTRAL" -> c.central();
            case "FEDERACION" -> sinPrefijoFederacion(c.federacion());
            case "LOTES" -> c.lotes();
            case "FECHA_EMISION" -> c.emitidaEl();
            case "CODIGO_CREDENCIAL" -> c.codigo();
            case "TEXTO_FIJO" -> e.texto();
            default -> "";
        };
        return valor(texto);
    }

    private byte[] bytesImagen(CredencialProductor c, String campo) {
        return switch (campo) {
            case "FOTO" -> c.foto();
            case "QR" -> c.qr();
            case "SELLO_FEDERACION" -> c.selloFederacion();
            case "SELLO_CENTRAL" -> c.selloCentral();
            case "SELLO_SINDICATO" -> c.selloSindicato();
            case "FIRMA_FEDERACION" -> firma(c.ejecutivoFederacion());
            case "FIRMA_CENTRAL" -> firma(c.secretarioGeneralCentral());
            case "FIRMA_SINDICATO" -> firma(c.secretarioGeneralSindicato());
            default -> null;
        };
    }

    private byte[] firma(CredencialProductor.Firmante firmante) {
        return firmante == null ? null : firmante.firma();
    }

    private CredencialProductor.Firmante firmante(CredencialProductor c, String campo) {
        return switch (campo) {
            case "PIE_FEDERACION" -> c.ejecutivoFederacion();
            case "PIE_CENTRAL" -> c.secretarioGeneralCentral();
            case "PIE_SINDICATO" -> c.secretarioGeneralSindicato();
            default -> null;
        };
    }

    private void fondo(PdfContentByte lienzo, float x, float y, Image plantilla) {
        Image fondo = Image.getInstance(plantilla);
        fondo.scaleAbsolute(ANCHO, ALTO);
        fondo.setAbsolutePosition(x, y);
        agregar(lienzo, fondo);
    }

    private static Image cargarPlantilla(String recurso) {
        try (InputStream entrada = CredencialProductorPdf.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontró la plantilla " + recurso);
            }
            return Image.getInstance(entrada.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la plantilla " + recurso, e);
        }
    }

    private static String unirNombre(String nombres, String apellidos) {
        String primero = nombres == null ? "" : nombres.trim();
        String segundo = apellidos == null ? "" : apellidos.trim();
        String completo = (primero + " " + segundo).trim();
        return completo.isBlank() ? "—" : completo;
    }

    private static String sinPrefijoFederacion(String nombre) {
        if (nombre == null) return null;
        return nombre.replaceFirst("(?i)^FEDERACI[ÓO]N\\s+", "").trim();
    }

    private static String valor(String texto) {
        return texto == null || texto.isBlank() ? "—" : texto.trim();
    }

    // -------------------------------------------------------- auxiliares

    /**
     * Contorno de la tarjeta, que además marca por dónde recortar.
     * <p>
     * Se dibuja al final, encima de las bandas de color. Y es un rectángulo
     * recto y no redondeado: las bandas llegan hasta el borde, así que unas
     * esquinas curvas dejaban ver el color por fuera del contorno. Además, una
     * línea recta es mejor guía para la tijera.
     */
    private void marco(PdfContentByte lienzo, float x, float y) {
        lienzo.setColorStroke(GRIS_LINEA);
        lienzo.setLineWidth(0.5f);
        lienzo.rectangle(x + 0.25f, y + 0.25f, ANCHO - 0.5f, ALTO - 0.5f);
        lienzo.stroke();
    }

    /**
     * Devuelve la fuente encogida lo necesario para que el texto entre.
     * <p>
     * Un apellido largo en una credencial no se puede cortar: el documento
     * tiene que decir el nombre entero. Por eso se achica la letra en vez de
     * recortar el texto, con un piso de 4,5 puntos para que siga siendo legible.
     */
    private Font ajustar(String texto, Font base, float anchoMaximo) {
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

    private Image imagen(byte[] contenido, float ancho, float alto) {
        if (contenido == null || contenido.length == 0) {
            return null;
        }
        try {
            Image imagen = Image.getInstance(contenido);
            imagen.scaleToFit(ancho, alto);
            return imagen;
        } catch (Exception e) {
            // Una imagen rota no puede impedir imprimir la credencial: sin ella
            // la tarjeta sigue sirviendo, sin credencial el productor no tiene
            // nada.
            return null;
        }
    }

    private void agregar(PdfContentByte lienzo, Image imagen) {
        try {
            lienzo.addImage(imagen);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo colocar una imagen en la credencial", e);
        }
    }

    private void centrado(PdfContentByte lienzo, String texto, Font fuente, float x, float y) {
        ColumnText.showTextAligned(lienzo, Element.ALIGN_CENTER,
                new Phrase(texto == null ? "" : texto, fuente), x, y, 0f);
    }

    private void izquierda(PdfContentByte lienzo, String texto, Font fuente, float x, float y) {
        ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT,
                new Phrase(texto == null ? "" : texto, fuente), x, y, 0f);
    }

    private void derecha(PdfContentByte lienzo, String texto, Font fuente, float x, float y) {
        ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT,
                new Phrase(texto == null ? "" : texto, fuente), x, y, 0f);
    }

    private Color color(String hexadecimal) {
        try {
            return Color.decode(hexadecimal == null ? "#000000" : hexadecimal);
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }
}
