package com.federa.backend.service;

import com.federa.backend.dto.InformeNominalImpresionCentral;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** PDF nominal con formato de nómina y constancia de recepción. */
@Component
public class InformeNominalImpresionCentralPdf {

    private static final Color GRIS_LINEA = new Color(150, 150, 150);
    private static final Color GRIS_ENCABEZADO = new Color(224, 224, 224);
    private static final Font TITULO = fuente(13, Font.BOLD, Color.BLACK);
    private static final Font SUBTITULO = fuente(9, Font.NORMAL, new Color(80, 80, 80));
    private static final Font SECCION = fuente(10, Font.BOLD, Color.BLACK);
    private static final Font CABECERA = fuente(7.2f, Font.BOLD, Color.BLACK);
    private static final Font CELDA = fuente(7.2f, Font.NORMAL, Color.BLACK);
    private static final Font FIRMA = fuente(8.5f, Font.NORMAL, Color.BLACK);
    private static final Font PIE_PAGINA = fuente(8, Font.NORMAL, new Color(90, 90, 90));

    public byte[] generar(InformeNominalImpresionCentral informe) {
        try {
            if (informe.sindicatos().isEmpty()) {
                return numerar(generarSinSindicatos(informe));
            }
            List<byte[]> documentos = new ArrayList<>();
            for (InformeNominalImpresionCentral.SeccionSindicato seccion
                    : informe.sindicatos()) {
                // Cada sindicato se genera aparte para que su numeración reinicie en 1.
                documentos.add(numerar(generarSindicato(informe, seccion)));
            }
            return combinar(documentos);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el informe nominal de "
                    + informe.central(), e);
        }
    }

    private byte[] generarSinSindicatos(InformeNominalImpresionCentral informe)
            throws DocumentException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = nuevoDocumento();
        PdfWriter.getInstance(documento, salida);
        documento.open();
        encabezado(documento, informe);
        documento.add(new Paragraph("No se seleccionaron sindicatos.", CELDA));
        documento.close();
        return salida.toByteArray();
    }

    private byte[] generarSindicato(InformeNominalImpresionCentral informe,
                                    InformeNominalImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = nuevoDocumento();
        PdfWriter.getInstance(documento, salida);
        documento.open();
        encabezado(documento, informe);
        sindicato(documento, seccion);
        documento.close();
        return salida.toByteArray();
    }

    private Document nuevoDocumento() {
        // Carta vertical, con espacio inferior reservado para la paginación.
        return new Document(PageSize.LETTER, 34, 34, 28, 42);
    }

    private void encabezado(Document documento, InformeNominalImpresionCentral informe)
            throws DocumentException {
        Paragraph titulo = new Paragraph(
                "INFORME NOMINAL DE IMPRESIÓN DE CARNET DE PRODUCTOR", TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);
        Paragraph organizacion = new Paragraph(
                informe.federacion() + " · CENTRAL " + informe.central(), SECCION);
        organizacion.setAlignment(Element.ALIGN_CENTER);
        documento.add(organizacion);
        Paragraph resumen = new Paragraph(
                "Impresos: " + informe.totalImpresos()
                        + "   ·   No impresos por datos faltantes: "
                        + informe.totalFaltantesDatos()
                        + "   ·   Generado: " + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), SUBTITULO);
        resumen.setAlignment(Element.ALIGN_CENTER);
        resumen.setSpacingAfter(14);
        documento.add(resumen);
    }

    private void sindicato(Document documento,
                           InformeNominalImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        // El total va antes del nombre del sindicato en la primera hoja.
        Paragraph impresos = new Paragraph(
                "CARNETS IMPRESOS (" + seccion.impresos().size() + ")", SECCION);
        impresos.setSpacingAfter(5);
        documento.add(impresos);
        documento.add(tablaImpresos(seccion));
        if (!seccion.impresos().isEmpty()) documento.add(constancia(seccion));

        Paragraph faltantes = new Paragraph(
                "NO IMPRESOS POR DATOS FALTANTES (" + seccion.faltantesDatos().size() + ")",
                SECCION);
        faltantes.setSpacingBefore(18);
        faltantes.setSpacingAfter(5);
        documento.add(faltantes);
        documento.add(tablaFaltantes(seccion));
    }

    private PdfPTable tablaImpresos(InformeNominalImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        String[] titulos = {"N°", "NOMBRES", "APELLIDOS", "C.I.", "N° LOTE", "CÓDIGO"};
        PdfPTable tabla = tabla(seccion.sindicato(), titulos,
                new float[]{.45f, 1.65f, 1.95f, 1.05f, 1.05f, 1.15f});
        int numero = 1;
        for (InformeNominalImpresionCentral.Fila fila : seccion.impresos()) {
            tabla.addCell(dato(numero++, Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.nombres(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.apellidos(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.ci(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.lotes(), Element.ALIGN_CENTER));
            tabla.addCell(dato(fila.codigoPadron(), Element.ALIGN_CENTER));
        }
        vacia(tabla, seccion.impresos(), titulos.length, "No hay carnets impresos.");
        return tabla;
    }

    private PdfPTable tablaFaltantes(InformeNominalImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        String[] titulos = {"N°", "NOMBRES", "APELLIDOS", "C.I.", "N° LOTE",
                "CÓDIGO", "DATOS FALTANTES"};
        PdfPTable tabla = tabla(seccion.sindicato(), titulos,
                new float[]{.4f, 1.25f, 1.45f, .8f, .8f, .9f, 2.15f});
        int numero = 1;
        for (InformeNominalImpresionCentral.Fila fila : seccion.faltantesDatos()) {
            tabla.addCell(dato(numero++, Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.nombres(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.apellidos(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.ci(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.lotes(), Element.ALIGN_CENTER));
            tabla.addCell(dato(fila.codigoPadron(), Element.ALIGN_CENTER));
            tabla.addCell(dato(String.join(", ", fila.datosFaltantes()), Element.ALIGN_LEFT));
        }
        vacia(tabla, seccion.faltantesDatos(), titulos.length,
                "No hay productores bloqueados por datos faltantes.");
        return tabla;
    }

    private PdfPTable tabla(String sindicato, String[] titulos, float[] anchos)
            throws DocumentException {
        PdfPTable tabla = new PdfPTable(titulos.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(anchos);

        // Esta fila y los títulos de columnas se repiten cuando la lista ocupa más hojas.
        PdfPCell nombre = new PdfPCell(new Phrase("SINDICATO: " + sindicato, SECCION));
        nombre.setColspan(titulos.length);
        nombre.setBorderColor(GRIS_LINEA);
        nombre.setPadding(5);
        tabla.addCell(nombre);
        for (String titulo : titulos) {
            PdfPCell celda = new PdfPCell(new Phrase(titulo, CABECERA));
            celda.setBackgroundColor(GRIS_ENCABEZADO);
            celda.setBorderColor(GRIS_LINEA);
            celda.setPadding(4);
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(celda);
        }
        tabla.setHeaderRows(2);
        return tabla;
    }

    private PdfPCell dato(Object valor, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(valor == null ? "" : String.valueOf(valor), CELDA));
        celda.setBorderColor(GRIS_LINEA);
        celda.setBorderWidth(.4f);
        celda.setPadding(3);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    private void vacia(PdfPTable tabla, List<?> filas, int columnas, String mensaje) {
        if (!filas.isEmpty()) return;
        PdfPCell celda = dato(mensaje, Element.ALIGN_CENTER);
        celda.setColspan(columnas);
        celda.setPadding(9);
        tabla.addCell(celda);
    }

    /** Pie en blanco para identificar a quien recibe y a quien entrega los carnets. */
    private PdfPTable constancia(InformeNominalImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        PdfPTable contenido = new PdfPTable(2);
        contenido.setWidths(new float[]{1.45f, 1f});

        PdfPCell titulo = sinBorde("CONSTANCIA DE RECEPCIÓN", FIRMA);
        titulo.setColspan(2);
        contenido.addCell(titulo);
        PdfPCell detalle = sinBorde(
                "\nRecibí conforme " + seccion.impresos().size()
                        + " carnet(s) del sindicato " + seccion.sindicato() + ".\n\n",
                FIRMA);
        detalle.setColspan(2);
        contenido.addCell(detalle);
        contenido.addCell(sinBorde(
                "Nombre de quien recibe: ______________________________\n\n"
                        + "C.I.: ____________________\n\n"
                        + "Firma: _________________________________    "
                        + "Fecha: ____ / ____ / ______", FIRMA));
        PdfPCell entrega = sinBorde(
                "\n\nEntregado por: ______________________________", FIRMA);
        entrega.setVerticalAlignment(Element.ALIGN_BOTTOM);
        entrega.setHorizontalAlignment(Element.ALIGN_RIGHT);
        contenido.addCell(entrega);

        PdfPTable bloque = new PdfPTable(1);
        bloque.setWidthPercentage(100);
        bloque.setKeepTogether(true);
        bloque.setSpacingBefore(13);
        PdfPCell marco = new PdfPCell(contenido);
        marco.setBorder(Rectangle.BOX);
        marco.setBorderColor(GRIS_LINEA);
        marco.setPadding(9);
        bloque.addCell(marco);
        return bloque;
    }

    private PdfPCell sinBorde(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0);
        return celda;
    }

    /** Agrega "Página X de N" cuando ya se conoce el total del sindicato. */
    private byte[] numerar(byte[] original) throws IOException, DocumentException {
        PdfReader lector = new PdfReader(original);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfStamper sello = new PdfStamper(lector, salida);
        BaseFont fuentePie = BaseFont.createFont(
                BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        int total = lector.getNumberOfPages();
        for (int pagina = 1; pagina <= total; pagina++) {
            Rectangle hoja = lector.getPageSizeWithRotation(pagina);
            String texto = "Página " + pagina + " de " + total;
            float ancho = fuentePie.getWidthPoint(texto, PIE_PAGINA.getSize());
            float x = (hoja.getLeft() + hoja.getRight() - ancho) / 2;
            PdfContentByte lienzo = sello.getOverContent(pagina);
            lienzo.saveState();
            lienzo.beginText();
            lienzo.setColorFill(PIE_PAGINA.getColor());
            lienzo.setFontAndSize(fuentePie, PIE_PAGINA.getSize());
            lienzo.setTextMatrix(x, 20);
            lienzo.showText(texto);
            lienzo.endText();
            lienzo.restoreState();
        }
        sello.close();
        lector.close();
        return salida.toByteArray();
    }

    /** Une los sindicatos sin alterar la numeración ya dibujada en cada bloque. */
    private byte[] combinar(List<byte[]> documentos) throws DocumentException, IOException {
        if (documentos.size() == 1) return documentos.get(0);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document();
        PdfCopy copia = new PdfCopy(documento, salida);
        documento.open();
        for (byte[] bytes : documentos) {
            PdfReader lector = new PdfReader(bytes);
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                copia.addPage(copia.getImportedPage(lector, pagina));
            }
            copia.freeReader(lector);
            lector.close();
        }
        documento.close();
        return salida.toByteArray();
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }
}
