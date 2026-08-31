package com.federa.backend.service;

import com.federa.backend.dto.InformeImpresionCentral;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Dibuja el informe imprimible del avance de una central. */
@Component
public class InformeImpresionCentralPdf {

    private static final Color VERDE = new Color(28, 104, 73);
    private static final Color VERDE_CLARO = new Color(222, 239, 231);
    private static final Color ROJO = new Color(165, 36, 36);
    private static final Color GRIS = new Color(100, 100, 100);
    private static final Font TITULO = fuente(16, Font.BOLD, VERDE);
    private static final Font SUBTITULO = fuente(9, Font.NORMAL, GRIS);
    private static final Font RESUMEN = fuente(10, Font.BOLD, Color.BLACK);
    private static final Font CABECERA = fuente(8, Font.BOLD, Color.BLACK);
    private static final Font CELDA = fuente(8, Font.NORMAL, Color.BLACK);

    public byte[] generar(InformeImpresionCentral informe) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.LETTER.rotate(), 32, 32, 32, 32);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            Paragraph titulo = new Paragraph("AVANCE DE IMPRESIÓN DE CREDENCIALES", TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            documento.add(titulo);
            Paragraph ubicacion = new Paragraph(
                    informe.federacion() + " · CENTRAL " + informe.central(), RESUMEN);
            ubicacion.setAlignment(Element.ALIGN_CENTER);
            ubicacion.setSpacingAfter(3);
            documento.add(ubicacion);
            Paragraph fecha = new Paragraph("Generado: " + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), SUBTITULO);
            fecha.setAlignment(Element.ALIGN_CENTER);
            fecha.setSpacingAfter(15);
            documento.add(fecha);

            documento.add(resumen(informe));
            documento.add(tabla(informe));
            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el informe de la central "
                    + informe.central(), e);
        }
        return salida.toByteArray();
    }

    private PdfPTable resumen(InformeImpresionCentral informe) throws DocumentException {
        PdfPTable tabla = new PdfPTable(7);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{1f, 1f, 1f, 1.15f, 1f, 1.15f, 1f});
        tabla.setSpacingAfter(16);
        resumen(tabla, "TOTAL", informe.total());
        resumen(tabla, "IMPRESOS", informe.impresos());
        resumen(tabla, "PENDIENTES", informe.pendientes());
        resumen(tabla, "CON FOTO", informe.pendientesConFoto());
        resumen(tabla, "SIN FOTO", informe.sinFoto());
        resumen(tabla, "SIND. SIN SELLO", informe.sindicatosSinSello());
        resumen(tabla, "AVANCE", formatoPorcentaje(informe.porcentajeAvance()));
        return tabla;
    }

    private void resumen(PdfPTable tabla, String etiqueta, Object valor) {
        PdfPCell celda = new PdfPCell(new Phrase(etiqueta + "\n" + valor, RESUMEN));
        celda.setBackgroundColor(VERDE_CLARO);
        celda.setBorderColor(Color.WHITE);
        celda.setBorderWidth(2);
        celda.setPadding(9);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabla.addCell(celda);
    }

    private PdfPTable tabla(InformeImpresionCentral informe) throws DocumentException {
        String[] titulos = {"SINDICATO", "SELLO", "TOTAL", "IMPRESOS", "PENDIENTES",
                "CON FOTO", "SIN FOTO", "LISTOS", "AVANCE"};
        PdfPTable tabla = new PdfPTable(titulos.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{3.35f, .85f, .7f, .8f, .95f, .85f, .8f, .7f, .85f});
        tabla.setHeaderRows(1);
        for (String titulo : titulos) tabla.addCell(cabecera(titulo));

        if (informe.detalle().isEmpty()) {
            PdfPCell vacia = dato("Esta central todavía no tiene sindicatos.", Element.ALIGN_CENTER);
            vacia.setColspan(titulos.length);
            vacia.setPadding(14);
            tabla.addCell(vacia);
            return tabla;
        }

        for (InformeImpresionCentral.FilaSindicato fila : informe.detalle()) {
            tabla.addCell(dato(fila.sindicato(), Element.ALIGN_LEFT));
            tabla.addCell(estadoSello(fila.selloCargado()));
            tabla.addCell(dato(fila.total(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.impresos(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.pendientes(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.pendientesConFoto(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.sinFoto(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.listosParaImprimir(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(formatoPorcentaje(fila.porcentajeAvance()), Element.ALIGN_RIGHT));
        }
        return tabla;
    }

    private PdfPCell estadoSello(boolean cargado) {
        PdfPCell celda = dato(cargado ? "CARGADO" : "FALTA", Element.ALIGN_CENTER);
        if (!cargado) {
            celda.setPhrase(new Phrase("FALTA", fuente(8, Font.BOLD, ROJO)));
            celda.setBackgroundColor(new Color(255, 235, 235));
        }
        return celda;
    }

    private PdfPCell cabecera(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, CABECERA));
        celda.setBackgroundColor(VERDE_CLARO);
        celda.setBorderColor(VERDE);
        celda.setPadding(6);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        return celda;
    }

    private PdfPCell dato(Object valor, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(String.valueOf(valor), CELDA));
        celda.setBorderColor(new Color(180, 180, 180));
        celda.setPadding(5);
        celda.setHorizontalAlignment(alineacion);
        return celda;
    }

    private static String formatoPorcentaje(double porcentaje) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", porcentaje);
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }
}
