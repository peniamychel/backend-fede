package com.federa.backend.service;

import com.federa.backend.dto.InformeFaseImpresion;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** PDF nominal de los resultados y pendientes de una fase. */
@Component
public class InformeFaseImpresionPdf {

    private static final Color VERDE = new Color(28, 104, 73);
    private static final Color VERDE_CLARO = new Color(222, 239, 231);
    private static final Color GRIS = new Color(100, 100, 100);
    private static final Color ROJO = new Color(180, 35, 35);
    private static final Font TITULO = fuente(13, Font.BOLD, VERDE);
    private static final Font SUBTITULO = fuente(8.5f, Font.NORMAL, GRIS);
    private static final Font SECCION = fuente(9, Font.BOLD, Color.BLACK);
    private static final Font CABECERA = fuente(6.8f, Font.BOLD, Color.BLACK);
    private static final Font CELDA = fuente(6.8f, Font.NORMAL, Color.BLACK);
    private static final Font ALERTA = fuente(6.8f, Font.BOLD, ROJO);
    private static final Font FIRMA = fuente(8, Font.NORMAL, Color.BLACK);

    public byte[] generar(InformeFaseImpresion informe) {
        try {
            if (informe.sindicatos().isEmpty()) {
                return numerar(generarSinSindicatos(informe));
            }
            List<byte[]> documentos = new ArrayList<>();
            for (InformeFaseImpresion.SeccionSindicato seccion : informe.sindicatos()) {
                documentos.add(numerar(generarSindicato(informe, seccion)));
            }
            return combinar(documentos);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException(
                    "No se pudo generar el informe de la fase " + informe.numeroFase(), e);
        }
    }

    private byte[] generarSinSindicatos(InformeFaseImpresion informe)
            throws DocumentException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = nuevoDocumento();
        PdfWriter.getInstance(documento, salida);
        documento.open();
        encabezado(documento, informe, null);
        documento.add(new Paragraph("La fase no tiene productores.", CELDA));
        documento.close();
        return salida.toByteArray();
    }

    private byte[] generarSindicato(
            InformeFaseImpresion informe,
            InformeFaseImpresion.SeccionSindicato seccion)
            throws DocumentException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = nuevoDocumento();
        PdfWriter.getInstance(documento, salida);
        documento.open();
        encabezado(documento, informe, seccion);
        sindicato(documento, seccion);
        documento.close();
        return salida.toByteArray();
    }

    private Document nuevoDocumento() {
        return new Document(PageSize.LETTER, 30, 30, 28, 40);
    }

    private void encabezado(
            Document documento, InformeFaseImpresion informe,
            InformeFaseImpresion.SeccionSindicato seccion)
            throws DocumentException {
        Paragraph titulo = new Paragraph(
                "INFORME NOMINAL DE LA FASE " + informe.numeroFase()
                        + " DE IMPRESIÓN DE CARNETS DE PRODUCTOR", TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);
        Paragraph organizacion = new Paragraph(
                informe.federacion() + " · CENTRAL " + informe.central(), SECCION);
        organizacion.setAlignment(Element.ALIGN_CENTER);
        documento.add(organizacion);
        if (seccion != null) {
            Paragraph sindicato = new Paragraph("SINDICATO: " + seccion.sindicato(), SECCION);
            sindicato.setAlignment(Element.ALIGN_CENTER);
            documento.add(sindicato);
        }
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String periodo = "Habilitada: " + informe.abiertaEn().format(formato)
                + (informe.cerradaEn() == null ? " · FASE ACTIVA"
                : " · Cerrada: " + informe.cerradaEn().format(formato));
        Paragraph resumen = new Paragraph(
                "Impresos: "
                        + (seccion == null ? informe.totalImpresos() : seccion.impresos().size())
                        + " · Pendientes: "
                        + (seccion == null
                        ? informe.totalPendientes() : seccion.pendientes().size())
                        + " · " + periodo, SUBTITULO);
        resumen.setAlignment(Element.ALIGN_CENTER);
        resumen.setSpacingAfter(12);
        documento.add(resumen);
    }

    private void sindicato(Document documento, InformeFaseImpresion.SeccionSindicato seccion)
            throws DocumentException {
        Paragraph impresos = new Paragraph(
                "CARNETS IMPRESOS EN LA FASE (" + seccion.impresos().size() + ")", SECCION);
        impresos.setSpacingAfter(4);
        documento.add(impresos);
        documento.add(tabla(seccion.sindicato(), seccion.impresos(), false));
        if (!seccion.impresos().isEmpty()) documento.add(constancia(seccion));

        Paragraph pendientes = new Paragraph(
                "PENDIENTES POR DATOS U OBSERVACIONES ("
                        + seccion.pendientes().size() + ")", SECCION);
        pendientes.setSpacingBefore(14);
        pendientes.setSpacingAfter(4);
        documento.add(pendientes);
        documento.add(tabla(seccion.sindicato(), seccion.pendientes(), true));
    }

    private PdfPTable tabla(String sindicato, List<InformeFaseImpresion.Fila> filas,
                            boolean alertas) throws DocumentException {
        String[] titulos = {"N°", "NOMBRES", "APELLIDOS", "C.I.", "N° LOTE",
                "OBSERVACIONES"};
        PdfPTable tabla = new PdfPTable(titulos.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{.4f, 1.3f, 1.6f, .85f, .8f, 2.8f});
        PdfPCell nombre = new PdfPCell(new Phrase("SINDICATO: " + sindicato, SECCION));
        nombre.setColspan(titulos.length);
        nombre.setBorderColor(VERDE);
        nombre.setPadding(4);
        tabla.addCell(nombre);
        for (String titulo : titulos) tabla.addCell(cabecera(titulo));
        tabla.setHeaderRows(2);
        int numero = 1;
        for (InformeFaseImpresion.Fila fila : filas) {
            tabla.addCell(dato(numero++, Element.ALIGN_RIGHT, false));
            tabla.addCell(dato(fila.nombres(), Element.ALIGN_LEFT, false));
            tabla.addCell(dato(fila.apellidos(), Element.ALIGN_LEFT, false));
            tabla.addCell(dato(fila.ci(), Element.ALIGN_LEFT, false));
            tabla.addCell(dato(fila.lotes(), Element.ALIGN_CENTER, false));
            String observacion = fila.reimpreso()
                    ? unir("CARNET REIMPRESO", fila.observaciones())
                    : String.join(", ", fila.observaciones());
            tabla.addCell(dato(observacion, Element.ALIGN_LEFT,
                    alertas || fila.reimpreso()));
        }
        if (filas.isEmpty()) {
            PdfPCell vacia = dato("Sin registros en este grupo.", Element.ALIGN_CENTER, false);
            vacia.setColspan(titulos.length);
            vacia.setPadding(8);
            tabla.addCell(vacia);
        }
        return tabla;
    }

    private String unir(String primero, List<String> otros) {
        return otros.isEmpty() ? primero : primero + ", " + String.join(", ", otros);
    }

    private PdfPCell cabecera(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, CABECERA));
        celda.setBackgroundColor(VERDE_CLARO);
        celda.setBorderColor(VERDE);
        celda.setPadding(4);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        return celda;
    }

    private PdfPCell dato(Object valor, int alineacion, boolean alerta) {
        PdfPCell celda = new PdfPCell(new Phrase(
                valor == null ? "" : String.valueOf(valor), alerta ? ALERTA : CELDA));
        celda.setBorderColor(new Color(170, 170, 170));
        celda.setBorderWidth(.4f);
        celda.setPadding(3);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    private PdfPTable constancia(InformeFaseImpresion.SeccionSindicato seccion)
            throws DocumentException {
        PdfPTable bloque = new PdfPTable(1);
        bloque.setWidthPercentage(100);
        bloque.setKeepTogether(true);
        bloque.setSpacingBefore(10);
        PdfPCell marco = new PdfPCell(new Phrase(
                "CONSTANCIA DE RECEPCIÓN\n\nRecibí conforme " + seccion.impresos().size()
                        + " carnet(s) del sindicato " + seccion.sindicato() + ".\n\n"
                        + "Nombre de quien recibe: ______________________________    "
                        + "C.I.: ____________________\n\nFirma: __________________________    "
                        + "Fecha: ____ / ____ / ______    Entregado por: ____________________",
                FIRMA));
        marco.setBorder(Rectangle.BOX);
        marco.setBorderColor(new Color(150, 150, 150));
        marco.setPadding(9);
        bloque.addCell(marco);
        return bloque;
    }

    private byte[] numerar(byte[] original) throws IOException, DocumentException {
        PdfReader lector = new PdfReader(original);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfStamper sello = new PdfStamper(lector, salida);
        BaseFont fuente = BaseFont.createFont(
                BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        int total = lector.getNumberOfPages();
        for (int pagina = 1; pagina <= total; pagina++) {
            Rectangle hoja = lector.getPageSizeWithRotation(pagina);
            String texto = "Página " + pagina + " de " + total;
            float ancho = fuente.getWidthPoint(texto, 8);
            PdfContentByte lienzo = sello.getOverContent(pagina);
            lienzo.beginText();
            lienzo.setColorFill(GRIS);
            lienzo.setFontAndSize(fuente, 8);
            lienzo.setTextMatrix((hoja.getWidth() - ancho) / 2, 18);
            lienzo.showText(texto);
            lienzo.endText();
        }
        sello.close();
        lector.close();
        return salida.toByteArray();
    }

    /** Une las secciones sin alterar la numeración propia de cada sindicato. */
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
