package com.federa.backend.service;

import com.federa.backend.dto.InformeSindicato;
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
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

/**
 * Dibuja el informe de un sindicato, con el formato de la nómina que la
 * federación venía imprimiendo desde Excel.
 * <p>
 * Es un componente sin estado: recibe un {@link InformeSindicato} ya armado y
 * devuelve los bytes del PDF. No consulta nada, así que se puede probar con
 * datos inventados y sin base de datos.
 * <p>
 * Se imprime en carta apaisada porque son siete columnas y en vertical la de
 * observaciones queda inservible.
 */
@Component
public class InformeSindicatoPdf {

    /** Carta apaisada: 792 × 612 puntos. */
    private static final Rectangle HOJA = PageSize.LETTER.rotate();

    private static final float MARGEN_LATERAL = 30f;
    /** Deja sitio al encabezado, que se dibuja fuera del área de contenido. */
    private static final float MARGEN_SUPERIOR = 96f;
    /** Ídem para el pie de página. */
    private static final float MARGEN_INFERIOR = 44f;

    /** Ancho del hueco del pie donde después se estampa el total de páginas. */
    private static final float ANCHO_TOTAL = 16f;

    private static final Color GRIS_LINEA = new Color(150, 150, 150);
    private static final Color GRIS_ENCABEZADO = new Color(224, 224, 224);
    private static final Color GRIS_TEXTO = new Color(90, 90, 90);

    private static final Font TITULO = fuente(10.5f, Font.BOLD, Color.BLACK);
    private static final Font ETIQUETA = fuente(9f, Font.BOLD, Color.BLACK);
    private static final Font CAMPO = fuente(8.5f, Font.NORMAL, Color.BLACK);
    private static final Font COLUMNA = fuente(7.5f, Font.BOLD, Color.BLACK);
    private static final Font CELDA = fuente(7.5f, Font.NORMAL, Color.BLACK);
    private static final Font PIE = fuente(7.5f, Font.NORMAL, GRIS_TEXTO);
    private static final Font ACTA = fuente(8.5f, Font.NORMAL, Color.BLACK);
    private static final Font FIRMA = fuente(8f, Font.BOLD, Color.BLACK);

    /**
     * Encabezados de la tabla y ancho de cada columna en puntos. Suman los 732
     * que quedan entre los márgenes; si se toca uno hay que compensar en otro.
     */
    private static final String[] COLUMNAS = {
            "N°", "NOMBRE COMPLETO", "APELLIDOS", "C.I.",
            "N° LOTE", "N° C. PRODUCTOR", "OBSERVACIONES"};
    private static final float[] ANCHOS = {26f, 108f, 140f, 66f, 52f, 70f, 270f};

    /**
     * Genera el PDF completo.
     *
     * @throws IllegalStateException si OpenPDF falla armando el documento. No
     *                               debería pasar con datos válidos, y no es
     *                               algo que quien llama pueda resolver.
     */
    public byte[] generar(InformeSindicato informe) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(HOJA,
                MARGEN_LATERAL, MARGEN_LATERAL, MARGEN_SUPERIOR, MARGEN_INFERIOR);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            escritor.setPageEvent(new EncabezadoYPie(informe));
            documento.open();
            documento.add(tablaDeProductores(informe));
            documento.add(acta(informe));
            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "No se pudo generar el informe del sindicato " + informe.sindicato(), e);
        }
        return salida.toByteArray();
    }

    // ---------------------------------------------------------------- tabla

    private PdfPTable tablaDeProductores(InformeSindicato informe) throws DocumentException {
        PdfPTable tabla = new PdfPTable(COLUMNAS.length);
        tabla.setTotalWidth(ANCHOS);
        tabla.setLockedWidth(true);
        // La fila de encabezados se repite sola en cada página nueva.
        tabla.setHeaderRows(1);

        for (String columna : COLUMNAS) {
            PdfPCell celda = new PdfPCell(new Phrase(columna, COLUMNA));
            celda.setBackgroundColor(GRIS_ENCABEZADO);
            celda.setBorderColor(GRIS_LINEA);
            celda.setBorderWidth(0.5f);
            celda.setPadding(3f);
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
            tabla.addCell(celda);
        }

        if (informe.filas().isEmpty()) {
            PdfPCell vacio = new PdfPCell(
                    new Phrase("Este sindicato todavía no tiene productores registrados.", CELDA));
            vacio.setColspan(COLUMNAS.length);
            vacio.setBorderColor(GRIS_LINEA);
            vacio.setBorderWidth(0.5f);
            vacio.setPadding(10f);
            vacio.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(vacio);
            return tabla;
        }

        for (InformeSindicato.Fila fila : informe.filas()) {
            tabla.addCell(dato(String.valueOf(fila.numero()), Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.nombres(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.apellidos(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.ci(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.lotes(), Element.ALIGN_CENTER));
            tabla.addCell(dato(fila.carnetProductor(), Element.ALIGN_CENTER));
            tabla.addCell(dato(fila.observaciones(), Element.ALIGN_LEFT));
        }
        return tabla;
    }

    private PdfPCell dato(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, CELDA));
        celda.setBorderColor(GRIS_LINEA);
        celda.setBorderWidth(0.4f);
        celda.setPaddingLeft(3f);
        celda.setPaddingRight(3f);
        celda.setPaddingTop(2.5f);
        celda.setPaddingBottom(2.5f);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    // ----------------------------------------------------------------- acta

    /**
     * Acta de entrega y bloque de firmas.
     * <p>
     * Va dentro de una tabla de una sola celda para poder pedir
     * {@code keepTogether}: un acta partida entre dos hojas, con el texto en
     * una y las firmas en la otra, no sirve como constancia.
     */
    private PdfPTable acta(InformeSindicato informe) throws DocumentException {
        PdfPTable contenedor = new PdfPTable(1);
        contenedor.setTotalWidth(sumaDeAnchos());
        contenedor.setLockedWidth(true);
        contenedor.setKeepTogether(true);
        contenedor.setSpacingBefore(16f);

        Phrase texto = new Phrase();
        texto.add(new Phrase("ACTA DE ENTREGA: ", ETIQUETA));
        texto.add(new Phrase("En fecha: ________________ de mes ________________ de "
                + informe.anio() + ". Se procedió a la entrega de la nómina actualizada de "
                + "N° ____________ afiliados con sistema de coca y N° ____________ afiliados "
                + "respetados orgánicamente.", ACTA));

        PdfPCell parrafo = new PdfPCell(texto);
        parrafo.setBorder(Rectangle.NO_BORDER);
        parrafo.setPaddingBottom(30f);
        contenedor.addCell(parrafo);

        PdfPCell firmas = new PdfPCell(bloqueDeFirmas(informe));
        firmas.setBorder(Rectangle.NO_BORDER);
        firmas.setPadding(0f);
        contenedor.addCell(firmas);
        return contenedor;
    }

    private PdfPTable bloqueDeFirmas(InformeSindicato informe) throws DocumentException {
        PdfPTable tabla = new PdfPTable(3);
        tabla.setTotalWidth(sumaDeAnchos());
        tabla.setLockedWidth(true);

        InformeSindicato.Dirigente dirigente = informe.dirigente();
        tabla.addCell(firma("DIRIGENTE/ENTREGUE", dirigente));
        tabla.addCell(firma("CENTRAL/ENTREGUE", null));
        tabla.addCell(firma("FEDERACIÓN/RECIBÍ", null));
        return tabla;
    }

    /**
     * Una casilla de firma: espacio arriba, línea, cargo, y debajo el pie de
     * firma o el nombre.
     * <p>
     * Cuando hay un presidente con la firma cargada se imprime; si no, queda el
     * mismo hueco en blanco de siempre para firmar a mano. Las otras dos
     * casillas van siempre vacías: las firma quien recibe, en el momento de
     * recibir.
     */
    private PdfPCell firma(String cargo, InformeSindicato.Dirigente dirigente) {
        PdfPTable casilla = new PdfPTable(1);

        PdfPCell espacio = new PdfPCell();
        espacio.setBorder(Rectangle.NO_BORDER);
        espacio.setFixedHeight(46f);
        espacio.setHorizontalAlignment(Element.ALIGN_CENTER);
        espacio.setVerticalAlignment(Element.ALIGN_BOTTOM);
        Image imagenFirma = imagen(dirigente == null ? null : dirigente.firma(), 130f, 42f);
        if (imagenFirma != null) {
            espacio.addElement(imagenFirma);
        }
        casilla.addCell(espacio);

        // El borde superior de esta celda es la línea sobre la que se firma.
        PdfPCell etiqueta = new PdfPCell(new Phrase(cargo, FIRMA));
        etiqueta.setBorder(Rectangle.TOP);
        etiqueta.setBorderColorTop(Color.BLACK);
        etiqueta.setBorderWidthTop(0.7f);
        etiqueta.setPaddingTop(4f);
        etiqueta.setHorizontalAlignment(Element.ALIGN_CENTER);
        casilla.addCell(etiqueta);

        PdfPCell debajo = new PdfPCell();
        debajo.setBorder(Rectangle.NO_BORDER);
        debajo.setHorizontalAlignment(Element.ALIGN_CENTER);
        debajo.setPaddingTop(3f);
        Image pie = imagen(dirigente == null ? null : dirigente.pieDeFirma(), 150f, 34f);
        if (pie != null) {
            debajo.addElement(pie);
        } else if (dirigente != null) {
            // Sin imagen de pie de firma se imprime el nombre, que es para lo
            // mismo: que se sepa quién firmó.
            Phrase nombre = new Phrase(dirigente.nombre(), CELDA);
            PdfPCell interna = new PdfPCell(nombre);
            interna.setBorder(Rectangle.NO_BORDER);
            interna.setHorizontalAlignment(Element.ALIGN_CENTER);
            PdfPTable envoltorio = new PdfPTable(1);
            envoltorio.addCell(interna);
            debajo.addElement(envoltorio);
        }
        casilla.addCell(debajo);

        PdfPCell celda = new PdfPCell(casilla);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPaddingLeft(12f);
        celda.setPaddingRight(12f);
        return celda;
    }

    /**
     * Convierte los bytes de una firma en una imagen escalada.
     * <p>
     * Devuelve null ante cualquier problema, y a propósito no lo propaga: que
     * una firma corrupta impida imprimir la nómina entera sería peor que
     * imprimirla con el espacio en blanco.
     */
    private Image imagen(byte[] contenido, float ancho, float alto) {
        if (contenido == null || contenido.length == 0) {
            return null;
        }
        try {
            Image imagen = Image.getInstance(contenido);
            imagen.scaleToFit(ancho, alto);
            imagen.setAlignment(Element.ALIGN_CENTER);
            return imagen;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------- encabezado/pie

    /**
     * Dibuja el encabezado y el pie en cada página.
     * <p>
     * Van por fuera del flujo del documento, escritos directamente sobre la
     * hoja, que es la única forma de que se repitan sin formar parte del
     * contenido que la tabla va paginando.
     */
    private final class EncabezadoYPie extends PdfPageEventHelper {

        private final InformeSindicato informe;
        /** Hueco reservado para el total de páginas, que recién se sabe al final. */
        private PdfTemplate total;
        private int paginas;

        private EncabezadoYPie(InformeSindicato informe) {
            this.informe = informe;
        }

        @Override
        public void onOpenDocument(PdfWriter escritor, Document documento) {
            total = escritor.getDirectContent().createTemplate(ANCHO_TOTAL, 10f);
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            paginas = escritor.getPageNumber();
            PdfContentByte lienzo = escritor.getDirectContent();
            float derecha = HOJA.getWidth() - MARGEN_LATERAL;
            float alto = HOJA.getHeight();

            centrado(lienzo, informe.federacion(), TITULO, alto - 34f);
            izquierda(lienzo, "CENTRAL: ", informe.central(), alto - 58f);
            izquierda(lienzo, "SINDICATO: ", informe.sindicato(), alto - 76f);

            // Los dos números que la federación todavía asigna a mano: se
            // imprime el rótulo con la raya y se llenan con lapicera.
            izquierdaEn(lienzo, "N° FEDERACIÓN: _______________", 545f, alto - 58f);
            izquierdaEn(lienzo, "N° CENTRAL: _______________", 545f, alto - 76f);

            centrado(lienzo, informe.sindicato(), PIE, 26f);
            // "Página N de" y el total se dibujan por separado, porque el
            // total todavía no se conoce. Se alinea el texto a la derecha y el
            // hueco se reserva a continuación, con un espacio de por medio:
            // pegados salía "de2".
            ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT,
                    new Phrase("Página " + paginas + " de", PIE),
                    derecha - ANCHO_TOTAL - 3f, 26f, 0f);
            lienzo.addTemplate(total, derecha - ANCHO_TOTAL, 26f);
        }

        @Override
        public void onCloseDocument(PdfWriter escritor, Document documento) {
            // El contador propio, y no getPageNumber(), porque al cerrar el
            // documento el escritor ya avanzó a una página que no existe.
            ColumnText.showTextAligned(total, Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(paginas), PIE), 0f, 0f, 0f);
        }

        private void centrado(PdfContentByte lienzo, String texto, Font fuente, float y) {
            ColumnText.showTextAligned(lienzo, Element.ALIGN_CENTER,
                    new Phrase(texto, fuente), HOJA.getWidth() / 2f, y, 0f);
        }

        private void izquierda(PdfContentByte lienzo, String rotulo, String valor, float y) {
            Phrase linea = new Phrase();
            linea.add(new Phrase(rotulo, ETIQUETA));
            linea.add(new Phrase(valor, CAMPO));
            ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT, linea, MARGEN_LATERAL, y, 0f);
        }

        private void izquierdaEn(PdfContentByte lienzo, String texto, float x, float y) {
            ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT,
                    new Phrase(texto, CAMPO), x, y, 0f);
        }
    }

    // ----------------------------------------------------------- auxiliares

    private static float sumaDeAnchos() {
        float suma = 0f;
        for (float ancho : ANCHOS) {
            suma += ancho;
        }
        return suma;
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }
}
