package com.federa.backend.service;

import com.federa.backend.dto.InformePreImpresionCentral;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
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

/** Lista por sindicato para revisar el padrón y anotar productores faltantes. */
@Component
public class InformeRevisionPadronCentralPdf {

    private static final Color NEGRO = Color.BLACK;
    private static final Color GRIS = new Color(115, 115, 115);
    private static final Color FONDO_GRIS = new Color(235, 235, 235);
    private static final Font TITULO = fuente(14, Font.BOLD, NEGRO);
    private static final Font SECCION = fuente(9, Font.BOLD, NEGRO);
    private static final Font SUBTITULO = fuente(9, Font.NORMAL, GRIS);
    private static final Font CABECERA = fuente(9, Font.BOLD, NEGRO);
    private static final Font CELDA = fuente(9, Font.NORMAL, NEGRO);

    public byte[] generar(InformePreImpresionCentral informe) {
        try {
            if (informe.sindicatos().isEmpty()) {
                return numerar(generarSinSindicatos(informe));
            }
            List<byte[]> documentos = new ArrayList<>();
            for (InformePreImpresionCentral.SeccionSindicato seccion : informe.sindicatos()) {
                documentos.add(numerar(generarSindicato(informe, seccion)));
            }
            return combinar(documentos);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException(
                    "No se pudo generar el informe de revisión del padrón de "
                            + informe.central(), e);
        }
    }

    static int cantidadFilasEnBlanco(int productores) {
        if (productores <= 0) return 0;
        return (int) Math.ceil(productores * 0.20d);
    }

    private byte[] generarSinSindicatos(InformePreImpresionCentral informe)
            throws DocumentException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = nuevoDocumento();
        PdfWriter.getInstance(documento, salida);
        documento.open();
        encabezado(documento, informe, null);
        documento.add(new Paragraph("Esta central no tiene sindicatos.", CELDA));
        documento.close();
        return salida.toByteArray();
    }

    private byte[] generarSindicato(
            InformePreImpresionCentral informe,
            InformePreImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = nuevoDocumento();
        PdfWriter.getInstance(documento, salida);
        documento.open();
        encabezado(documento, informe, seccion);
        documento.add(tabla(seccion));
        documento.close();
        return salida.toByteArray();
    }

    private Document nuevoDocumento() {
        return new Document(PageSize.LETTER, 30, 30, 28, 40);
    }

    private void encabezado(
            Document documento,
            InformePreImpresionCentral informe,
            InformePreImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        Paragraph titulo = new Paragraph(
                "INFORME DE REVISIÓN DEL PADRÓN DE PRODUCTORES", TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);

        Paragraph organizacion = new Paragraph(
                informe.federacion() + " · CENTRAL " + informe.central(), SECCION);
        organizacion.setAlignment(Element.ALIGN_CENTER);
        documento.add(organizacion);
        if (seccion != null) {
            Paragraph sindicato = new Paragraph(
                    "SINDICATO: " + seccion.sindicato(), SECCION);
            sindicato.setAlignment(Element.ALIGN_CENTER);
            documento.add(sindicato);
        }

        int total = seccion == null ? informe.total() : seccion.productores().size();
        int adicionales = cantidadFilasEnBlanco(total);
        Paragraph resumen = new Paragraph(
                "Productores registrados: " + total
                        + "   ·   Espacios para nuevos registros: " + adicionales
                        + "   ·   Generado: "
                        + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), SUBTITULO);
        resumen.setAlignment(Element.ALIGN_CENTER);
        resumen.setSpacingAfter(12);
        documento.add(resumen);
    }

    private PdfPTable tabla(InformePreImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        String[] titulos = {"N°", "NOMBRES", "APELLIDOS", "C.I.", "N° LOTE",
                "OBSERVACIONES"};
        PdfPTable tabla = new PdfPTable(titulos.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{.4f, 1.45f, 1.7f, .9f, .8f, 2.25f});

        PdfPCell sindicato = new PdfPCell(new Phrase(
                "SINDICATO: " + seccion.sindicato(), SECCION));
        sindicato.setColspan(titulos.length);
        sindicato.setBorderColor(NEGRO);
        sindicato.setPadding(5);
        tabla.addCell(sindicato);
        for (String titulo : titulos) tabla.addCell(cabecera(titulo));
        tabla.setHeaderRows(2);

        int numero = 1;
        for (InformePreImpresionCentral.Fila fila : seccion.productores()) {
            tabla.addCell(dato(numero++, Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.nombres(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.apellidos(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.ci(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.lotes(), Element.ALIGN_CENTER));
            tabla.addCell(dato(observaciones(fila), Element.ALIGN_LEFT));
        }

        int adicionales = cantidadFilasEnBlanco(seccion.productores().size());
        for (int i = 0; i < adicionales; i++) {
            tabla.addCell(dato(numero++, Element.ALIGN_RIGHT));
            for (int columna = 1; columna < titulos.length; columna++) {
                PdfPCell vacia = dato("", Element.ALIGN_LEFT);
                vacia.setMinimumHeight(19);
                tabla.addCell(vacia);
            }
        }

        if (seccion.productores().isEmpty()) {
            PdfPCell vacia = dato("El sindicato no tiene productores.", Element.ALIGN_CENTER);
            vacia.setColspan(titulos.length);
            vacia.setPadding(8);
            tabla.addCell(vacia);
        }
        return tabla;
    }

    private String observaciones(InformePreImpresionCentral.Fila fila) {
        List<String> observaciones = new ArrayList<>(2);
        if (fila.observado()) observaciones.add("NOMBRE OBSERVADO");
        if (fila.lotes() == null || fila.lotes().isBlank()) {
            observaciones.add("SIN NÚMERO DE LOTE");
        }
        return String.join(" · ", observaciones);
    }

    private PdfPCell cabecera(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, CABECERA));
        celda.setBackgroundColor(FONDO_GRIS);
        celda.setBorderColor(NEGRO);
        celda.setPadding(4);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        return celda;
    }

    private PdfPCell dato(Object valor, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(
                valor == null ? "" : String.valueOf(valor), CELDA));
        celda.setBorderColor(new Color(170, 170, 170));
        celda.setBorderWidth(.4f);
        celda.setPadding(3);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    private byte[] numerar(byte[] original) throws IOException, DocumentException {
        PdfReader lector = new PdfReader(original);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfStamper sello = new PdfStamper(lector, salida);
        BaseFont fuente = FuentesInforme.regular();
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

    private byte[] combinar(List<byte[]> documentos)
            throws DocumentException, IOException {
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
        return FuentesInforme.fuente(tamano, estilo, color);
    }
}
