package com.federa.backend.service;

import com.federa.backend.dto.InformeImpresionCentral;
import com.federa.backend.dto.InformeImpresionFederacion;
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

/** PDF del avance de impresión de toda la federación. */
@Component
public class InformeImpresionFederacionPdf {

    // Comparte la paleta del informe general por central/sindicatos.
    private static final Color VERDE = new Color(28, 104, 73);
    private static final Color VERDE_CLARO = new Color(222, 239, 231);
    private static final Color ROJO = new Color(165, 36, 36);
    private static final Color GRIS = new Color(100, 100, 100);
    private static final Font TITULO = fuente(16, Font.BOLD, VERDE);
    private static final Font SUBTITULO = fuente(9, Font.NORMAL, GRIS);
    private static final Font SECCION = fuente(11, Font.BOLD, VERDE);
    private static final Font RESUMEN = fuente(9, Font.BOLD, Color.BLACK);
    private static final Font CABECERA = fuente(7.5f, Font.BOLD, Color.BLACK);
    private static final Font CELDA = fuente(7.5f, Font.NORMAL, Color.BLACK);

    public byte[] generar(InformeImpresionFederacion informe) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.LETTER.rotate(), 32, 32, 30, 32);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();
            encabezado(documento, informe);
            documento.add(resumen(informe));

            Paragraph centrales = new Paragraph("RESUMEN POR CENTRAL", SECCION);
            centrales.setSpacingAfter(7);
            documento.add(centrales);
            documento.add(tablaCentrales(informe));

            Paragraph detalle = new Paragraph("DETALLE POR CENTRAL Y SINDICATO", SECCION);
            detalle.setSpacingBefore(18);
            detalle.setSpacingAfter(7);
            documento.add(detalle);
            if (informe.detalle().isEmpty()) {
                documento.add(new Paragraph(
                        "La federación todavía no tiene centrales registradas.", CELDA));
            } else {
                for (InformeImpresionCentral central : informe.detalle()) {
                    documento.add(detalleCentral(central));
                }
            }
            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el avance general de "
                    + informe.federacion(), e);
        }
        return salida.toByteArray();
    }

    private void encabezado(Document documento, InformeImpresionFederacion informe)
            throws DocumentException {
        Paragraph titulo = new Paragraph(
                "AVANCE GENERAL DE IMPRESIÓN DE CREDENCIALES", TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);
        Paragraph federacion = new Paragraph(informe.federacion(), RESUMEN);
        federacion.setAlignment(Element.ALIGN_CENTER);
        documento.add(federacion);
        Paragraph fecha = new Paragraph("Generado: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), SUBTITULO);
        fecha.setAlignment(Element.ALIGN_CENTER);
        fecha.setSpacingAfter(14);
        documento.add(fecha);
    }

    private PdfPTable resumen(InformeImpresionFederacion informe)
            throws DocumentException {
        PdfPTable tabla = new PdfPTable(9);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{.9f, .9f, 1f, 1f, 1f, 1f, 1f, 1.15f, .9f});
        tabla.setSpacingAfter(16);
        resumen(tabla, "CENTRALES", informe.centrales());
        resumen(tabla, "SINDICATOS", informe.sindicatos());
        resumen(tabla, "TOTAL", informe.total());
        resumen(tabla, "IMPRESOS", informe.impresos());
        resumen(tabla, "PENDIENTES", informe.pendientes());
        resumen(tabla, "CON FOTO", informe.pendientesConFoto());
        resumen(tabla, "SIN FOTO", informe.sinFoto());
        resumen(tabla, "SIND. SIN SELLO", informe.sindicatosSinSello());
        resumen(tabla, "AVANCE", porcentaje(informe.porcentajeAvance()));
        return tabla;
    }

    private void resumen(PdfPTable tabla, String etiqueta, Object valor) {
        PdfPCell celda = new PdfPCell(new Phrase(etiqueta + "\n" + valor, RESUMEN));
        celda.setBackgroundColor(VERDE_CLARO);
        celda.setBorderColor(Color.WHITE);
        celda.setBorderWidth(2);
        celda.setPadding(8);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabla.addCell(celda);
    }

    private PdfPTable tablaCentrales(InformeImpresionFederacion informe)
            throws DocumentException {
        String[] titulos = {"CENTRAL", "SINDICATOS", "PRODUCTORES", "IMPRESOS",
                "NO IMPRESOS", "CON FOTO", "SIN FOTO", "LISTOS", "AVANCE"};
        PdfPTable tabla = tabla(titulos,
                new float[]{3f, .9f, 1f, .9f, 1f, .9f, .8f, .8f, .85f});
        for (InformeImpresionCentral central : informe.detalle()) {
            tabla.addCell(dato(central.central(), Element.ALIGN_LEFT));
            tabla.addCell(dato(central.sindicatos(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(central.total(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(central.impresos(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(central.pendientes(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(central.pendientesConFoto(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(central.sinFoto(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(central.listosParaImprimir(), Element.ALIGN_RIGHT));
            tabla.addCell(dato(porcentaje(central.porcentajeAvance()), Element.ALIGN_RIGHT));
        }
        vacia(tabla, informe.detalle().isEmpty(), titulos.length,
                "No hay centrales registradas.");
        return tabla;
    }

    private PdfPTable detalleCentral(InformeImpresionCentral central)
            throws DocumentException {
        String[] titulos = {"SINDICATO", "SELLO", "TOTAL", "IMPRESOS", "PENDIENTES",
                "CON FOTO", "SIN FOTO", "LISTOS", "AVANCE"};
        PdfPCell nombre = new PdfPCell(new Phrase(
                "CENTRAL " + central.central() + " · " + central.impresos() + " DE "
                        + central.total() + " IMPRESOS · "
                        + porcentaje(central.porcentajeAvance()), SECCION));
        nombre.setColspan(titulos.length);
        nombre.setPadding(7);
        nombre.setBackgroundColor(VERDE_CLARO);
        nombre.setBorderColor(VERDE);

        // Inserta el encabezado de central antes de los títulos de columnas.
        PdfPTable resultado = new PdfPTable(titulos.length);
        resultado.setWidthPercentage(100);
        resultado.setWidths(new float[]{3.15f, .85f, .7f, .8f, .95f, .85f, .8f, .7f, .85f});
        resultado.setSpacingBefore(7);
        resultado.setSpacingAfter(9);
        resultado.addCell(nombre);
        for (String titulo : titulos) resultado.addCell(cabecera(titulo));
        resultado.setHeaderRows(2);

        for (InformeImpresionCentral.FilaSindicato fila : central.detalle()) {
            resultado.addCell(dato(fila.sindicato(), Element.ALIGN_LEFT));
            resultado.addCell(estadoSello(fila.selloCargado()));
            resultado.addCell(dato(fila.total(), Element.ALIGN_RIGHT));
            resultado.addCell(dato(fila.impresos(), Element.ALIGN_RIGHT));
            resultado.addCell(dato(fila.pendientes(), Element.ALIGN_RIGHT));
            resultado.addCell(dato(fila.pendientesConFoto(), Element.ALIGN_RIGHT));
            resultado.addCell(dato(fila.sinFoto(), Element.ALIGN_RIGHT));
            resultado.addCell(dato(fila.listosParaImprimir(), Element.ALIGN_RIGHT));
            resultado.addCell(dato(porcentaje(fila.porcentajeAvance()), Element.ALIGN_RIGHT));
        }
        vacia(resultado, central.detalle().isEmpty(), titulos.length,
                "Esta central todavía no tiene sindicatos.");
        return resultado;
    }

    private PdfPTable tabla(String[] titulos, float[] anchos) throws DocumentException {
        PdfPTable tabla = new PdfPTable(titulos.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(anchos);
        tabla.setHeaderRows(1);
        for (String titulo : titulos) tabla.addCell(cabecera(titulo));
        return tabla;
    }

    private PdfPCell cabecera(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, CABECERA));
        celda.setBackgroundColor(VERDE_CLARO);
        celda.setBorderColor(VERDE);
        celda.setPadding(5);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    private PdfPCell dato(Object valor, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(String.valueOf(valor), CELDA));
        celda.setBorderColor(new Color(180, 180, 180));
        celda.setPadding(4.5f);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    private PdfPCell estadoSello(boolean cargado) {
        PdfPCell celda = dato(cargado ? "CARGADO" : "FALTA", Element.ALIGN_CENTER);
        if (!cargado) {
            celda.setPhrase(new Phrase("FALTA", fuente(7.5f, Font.BOLD, ROJO)));
            celda.setBackgroundColor(new Color(255, 235, 235));
        }
        return celda;
    }

    private void vacia(PdfPTable tabla, boolean vacia, int columnas, String mensaje) {
        if (!vacia) return;
        PdfPCell celda = dato(mensaje, Element.ALIGN_CENTER);
        celda.setColspan(columnas);
        celda.setPadding(12);
        tabla.addCell(celda);
    }

    private static String porcentaje(double valor) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", valor);
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }
}
