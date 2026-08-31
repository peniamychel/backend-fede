package com.federa.backend.service;

import com.federa.backend.dto.PlanillaRecoleccionDirectorio;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Dibuja la planilla que se imprime para recolectar sellos y firmas. */
@Component
public class PlanillaRecoleccionDirectorioPdf {

    private static final Color VERDE = new Color(28, 104, 73);
    private static final Color VERDE_CLARO = new Color(222, 239, 231);
    private static final Color GRIS = new Color(95, 95, 95);
    private static final float ANCHO_CUADROS = 500f;
    private static final float LADO_CUADRO = 250f;
    private static final Font TITULO = fuente(14, Font.BOLD, VERDE);
    private static final Font SUBTITULO = fuente(9, Font.NORMAL, GRIS);
    private static final Font SECCION = fuente(11, Font.BOLD, VERDE);
    private static final Font CABECERA = fuente(8, Font.BOLD, Color.BLACK);
    private static final Font TEXTO = fuente(8, Font.NORMAL, Color.BLACK);

    public byte[] generar(PlanillaRecoleccionDirectorio planilla) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.LETTER, 32, 32, 30, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, salida);
            writer.setPageEvent(new NumeracionPaginas());
            documento.open();
            encabezado(documento, planilla);
            documento.add(seccion("RECOLECCIÓN DE LA CENTRAL"));
            documento.add(datosCentral(planilla));
            documento.newPage();
            documento.add(sindicatos(planilla));
            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "No se pudo generar la planilla de recolección de " + planilla.central(), e);
        }
        return salida.toByteArray();
    }

    private void encabezado(Document documento, PlanillaRecoleccionDirectorio planilla)
            throws DocumentException {
        Paragraph titulo = new Paragraph(
                "PLANILLA DE RECOLECCIÓN DE SELLOS, FIRMAS Y PIES DE FIRMA", TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);
        Paragraph ubicacion = new Paragraph(
                planilla.federacion() + " · CENTRAL " + planilla.central(), CABECERA);
        ubicacion.setAlignment(Element.ALIGN_CENTER);
        documento.add(ubicacion);
        Paragraph fecha = new Paragraph(
                "Fecha: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                SUBTITULO);
        fecha.setAlignment(Element.ALIGN_CENTER);
        fecha.setSpacingAfter(8);
        documento.add(fecha);
        Paragraph ayuda = new Paragraph(
                "Use los espacios en blanco para estampar los sellos y registrar la firma "
                        + "y el pie de firma que luego serán digitalizados.", SUBTITULO);
        ayuda.setAlignment(Element.ALIGN_CENTER);
        ayuda.setSpacingAfter(14);
        documento.add(ayuda);
    }

    private Paragraph seccion(String texto) {
        Paragraph seccion = new Paragraph(texto, SECCION);
        seccion.setSpacingBefore(4);
        seccion.setSpacingAfter(7);
        return seccion;
    }

    private PdfPTable datosCentral(PlanillaRecoleccionDirectorio planilla)
            throws DocumentException {
        PdfPTable tabla = tablaDeCuadros();
        tabla.addCell(espacioRecoleccion(
                "SELLO DE LA CENTRAL", planilla.selloCentralCargado(), null));
        tabla.addCell(espacioRecoleccion(
                "FIRMA DEL SECRETARIO GENERAL", planilla.firmaCentralCargada(),
                planilla.secretarioGeneral()));
        tabla.addCell(espacioRecoleccion(
                "PIE DE FIRMA", planilla.pieFirmaCentralCargado(),
                planilla.secretarioGeneral()));
        tabla.addCell(cuadroVacio());
        return tabla;
    }

    private PdfPTable sindicatos(PlanillaRecoleccionDirectorio planilla)
            throws DocumentException {
        PdfPTable tabla = tablaDeCuadros();
        PdfPCell titulo = cabecera("SELLOS DE LOS SINDICATOS");
        titulo.setColspan(2);
        tabla.addCell(titulo);
        tabla.setHeaderRows(1);

        if (planilla.sindicatos().isEmpty()) {
            PdfPCell vacia = dato("Esta central todavía no tiene sindicatos.");
            vacia.setColspan(2);
            vacia.setHorizontalAlignment(Element.ALIGN_CENTER);
            vacia.setPadding(14);
            tabla.addCell(vacia);
            return tabla;
        }

        int numero = 1;
        for (PlanillaRecoleccionDirectorio.FilaSindicato sindicato : planilla.sindicatos()) {
            tabla.addCell(espacioRecoleccion(
                    numero++ + ". " + sindicato.sindicato() + "\nSELLO DEL SINDICATO",
                    sindicato.selloCargado(), null));
        }
        if (planilla.sindicatos().size() % 2 != 0) tabla.addCell(cuadroVacio());
        return tabla;
    }

    private PdfPTable tablaDeCuadros() throws DocumentException {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setTotalWidth(ANCHO_CUADROS);
        tabla.setLockedWidth(true);
        tabla.setWidths(new float[]{1f, 1f});
        tabla.setHorizontalAlignment(Element.ALIGN_CENTER);
        return tabla;
    }

    private PdfPCell espacioRecoleccion(String etiqueta, boolean cargado,
                                        String responsable) {
        StringBuilder texto = new StringBuilder(etiqueta)
                .append("\nEstado: ").append(estado(cargado));
        if (responsable != null && !responsable.isBlank()) {
            texto.append("\n").append(responsable);
        }
        PdfPCell celda = new PdfPCell(new Phrase(texto.toString(), CABECERA));
        celda.setBorderColor(new Color(150, 150, 150));
        celda.setPadding(10);
        celda.setFixedHeight(LADO_CUADRO);
        celda.setVerticalAlignment(Element.ALIGN_TOP);
        return celda;
    }

    private PdfPCell cuadroVacio() {
        PdfPCell celda = new PdfPCell();
        celda.setFixedHeight(LADO_CUADRO);
        celda.setBorder(Rectangle.NO_BORDER);
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

    private PdfPCell dato(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, TEXTO));
        celda.setBorderColor(new Color(175, 175, 175));
        celda.setPadding(6);
        return celda;
    }

    private String estado(boolean cargado) {
        return cargado ? "CARGADO" : "PENDIENTE";
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }

    private static class NumeracionPaginas extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle pagina = document.getPageSize();
            ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase("Página " + writer.getPageNumber(), SUBTITULO),
                    (pagina.getLeft() + pagina.getRight()) / 2,
                    document.bottom() - 18, 0);
        }
    }
}
