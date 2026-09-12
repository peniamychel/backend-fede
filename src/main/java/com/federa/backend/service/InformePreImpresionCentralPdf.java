package com.federa.backend.service;

import com.federa.backend.dto.InformePreImpresionCentral;
import com.lowagie.text.Chunk;
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

/** PDF de revisión nominal separado y numerado por sindicato. */
@Component
public class InformePreImpresionCentralPdf {

    private static final Color VERDE = new Color(28, 104, 73);
    private static final Color VERDE_CLARO = new Color(222, 239, 231);
    private static final Color GRIS = new Color(115, 115, 115);
    private static final Color ROJO = new Color(185, 28, 28);
    private static final Font TITULO = fuente(12, Font.BOLD, VERDE);
    private static final Font SUBTITULO = fuente(8.5f, Font.NORMAL, GRIS);
    private static final Font SECCION = fuente(9, Font.BOLD, Color.BLACK);
    private static final Font CABECERA = fuente(6.8f, Font.BOLD, Color.BLACK);
    private static final Font CELDA = fuente(6.8f, Font.NORMAL, Color.BLACK);
    private static final Font OBSERVADO = fuente(6.8f, Font.BOLD, ROJO);
    private static final Font PIE = fuente(8, Font.NORMAL, Color.BLACK);

    public byte[] generar(InformePreImpresionCentral informe) {
        try {
            if (informe.sindicatos().isEmpty()) {
                return numerar(generarSinSindicatos(informe));
            }
            List<byte[]> documentos = new ArrayList<>();
            for (InformePreImpresionCentral.SeccionSindicato seccion : informe.sindicatos()) {
                // Se genera un documento por sindicato para reiniciar su paginación.
                documentos.add(numerar(generarSindicato(informe, seccion)));
            }
            return combinar(documentos);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException(
                    "No se pudo generar el informe pre-impresión de " + informe.central(), e);
        }
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
        documento.add(constancia(seccion));
        documento.close();
        return salida.toByteArray();
    }

    private Document nuevoDocumento() {
        return new Document(PageSize.LETTER, 30, 30, 28, 40);
    }

    private void encabezado(
            Document documento, InformePreImpresionCentral informe,
            InformePreImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        Paragraph titulo = new Paragraph(
                "INFORME DE REVISIÓN DE DATOS DE IMPRESIÓN DE CARNET DE PRODUCTORES",
                TITULO);
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
        Paragraph resumen = new Paragraph(
                "Productores revisados: "
                        + (seccion == null ? informe.total() : seccion.productores().size())
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
                "DATOS FALTANTES"};
        PdfPTable tabla = new PdfPTable(titulos.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{.4f, 1.35f, 1.65f, .9f, .85f, 2.8f});
        tabla.setSpacingAfter(12);

        PdfPCell sindicato = new PdfPCell(new Phrase(
                "SINDICATO: " + seccion.sindicato(), SECCION));
        sindicato.setColspan(titulos.length);
        sindicato.setBorderColor(VERDE);
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
            tabla.addCell(datosFaltantes(fila));
        }
        if (seccion.productores().isEmpty()) {
            PdfPCell vacia = dato("El sindicato no tiene productores.", Element.ALIGN_CENTER);
            vacia.setColspan(titulos.length);
            vacia.setPadding(8);
            tabla.addCell(vacia);
        }
        return tabla;
    }

    private PdfPCell datosFaltantes(InformePreImpresionCentral.Fila fila) {
        Phrase frase = new Phrase();
        if (fila.observado()) {
            frase.add(new Chunk("(OBSERVADO)", OBSERVADO));
            if (!fila.datosFaltantes().isEmpty()) frase.add(new Chunk(" ", CELDA));
        }
        List<String> visibles = fila.datosFaltantes().stream()
                .filter(dato -> !"Observado".equalsIgnoreCase(dato))
                .toList();
        frase.add(new Chunk(String.join(", ", visibles), CELDA));
        PdfPCell celda = new PdfPCell(frase);
        configurarDato(celda, Element.ALIGN_LEFT);
        return celda;
    }

    private PdfPCell cabecera(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, CABECERA));
        celda.setBackgroundColor(VERDE_CLARO);
        celda.setBorderColor(VERDE);
        celda.setPadding(4);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        return celda;
    }

    private PdfPCell dato(Object valor, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(
                valor == null ? "" : String.valueOf(valor), CELDA));
        configurarDato(celda, alineacion);
        return celda;
    }

    private void configurarDato(PdfPCell celda, int alineacion) {
        celda.setBorderColor(new Color(170, 170, 170));
        celda.setBorderWidth(.4f);
        celda.setPadding(3);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
    }

    private PdfPTable constancia(InformePreImpresionCentral.SeccionSindicato seccion)
            throws DocumentException {
        PdfPTable contenido = new PdfPTable(2);
        contenido.setWidths(new float[]{1.4f, 1f});
        PdfPCell titulo = sinBorde("CONSTANCIA DE ENTREGA DE INFORME", PIE);
        titulo.setColspan(2);
        contenido.addCell(titulo);
        PdfPCell detalle = sinBorde(
                "\nRecibí conforme el informe de revisión de "
                        + seccion.productores().size() + " productor(es) del sindicato "
                        + seccion.sindicato() + ".\n\n", PIE);
        detalle.setColspan(2);
        contenido.addCell(detalle);
        contenido.addCell(sinBorde(
                "Nombre de quien recibe: ______________________________\n\n"
                        + "C.I.: ____________________\n\n"
                        + "Firma: _________________________________    "
                        + "Fecha: ____ / ____ / ______", PIE));
        PdfPCell entrega = sinBorde(
                "\n\nEntregado por: ______________________________", PIE);
        entrega.setHorizontalAlignment(Element.ALIGN_RIGHT);
        entrega.setVerticalAlignment(Element.ALIGN_BOTTOM);
        contenido.addCell(entrega);

        PdfPTable bloque = new PdfPTable(1);
        bloque.setWidthPercentage(100);
        bloque.setKeepTogether(true);
        bloque.setSpacingBefore(5);
        PdfPCell marco = new PdfPCell(contenido);
        marco.setBorder(Rectangle.BOX);
        marco.setBorderColor(new Color(150, 150, 150));
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

    /** Une los informes conservando la numeración independiente de cada sindicato. */
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
