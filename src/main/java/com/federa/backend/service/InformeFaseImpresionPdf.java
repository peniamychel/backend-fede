package com.federa.backend.service;

import com.federa.backend.dto.InformeFaseImpresion;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** PDF nominal de los resultados y pendientes de una fase. */
@Component
public class InformeFaseImpresionPdf {

    private static final Color NEGRO = Color.BLACK;
    private static final Color FONDO_GRIS = new Color(235, 235, 235);
    private static final Color GRIS = new Color(100, 100, 100);
    private static final Font TITULO = fuente(14, Font.BOLD, NEGRO);
    private static final Font SUBTITULO = fuente(9f, Font.NORMAL, GRIS);
    private static final Font SECCION = fuente(9, Font.BOLD, Color.BLACK);
    private static final Font CABECERA = fuente(9f, Font.BOLD, Color.BLACK);
    private static final Font CELDA = fuente(9f, Font.NORMAL, Color.BLACK);
    private static final Font FASE_DESTACADA = fuente(18f, Font.BOLD, Color.BLACK);
    private static final Font FIRMA = fuente(9, Font.NORMAL, Color.BLACK);

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
        PdfPTable bloqueConstancia = constancia(seccion);
        bloqueConstancia.setTotalWidth(PageSize.LETTER.getWidth() - 60);
        bloqueConstancia.calculateHeights(true);
        float margenInferior = 50 + bloqueConstancia.getTotalHeight() + 15;
        Document documento = new Document(PageSize.LETTER, 30, 30, 28, margenInferior);
        PdfWriter escritor = PdfWriter.getInstance(documento, salida);
        documento.open();
        encabezado(documento, informe, seccion);
        documento.add(leyendaObservaciones(informe.numeroFase()));
        sindicato(documento, escritor, informe, seccion, bloqueConstancia);
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
        Paragraph resumen = new Paragraph(periodo, SUBTITULO);
        resumen.setAlignment(Element.ALIGN_CENTER);
        resumen.setSpacingAfter(12);
        documento.add(resumen);
    }

    private PdfPTable leyendaObservaciones(int numeroFase) throws DocumentException {
        PdfPTable bloque = new PdfPTable(2);
        bloque.setWidthPercentage(100);
        bloque.setWidths(new float[]{3f, 1f});
        bloque.setSpacingAfter(8);
        PdfPCell leyenda = new PdfPCell(new Phrase(
                "* N° lote = Falta el número de lote.\n"
                        + "* Sie = Revisar nombre y apellidos frente a la cédula de identidad.\n"
                        + "* Obs = Productor observado revisión general.\n"
                        + "* Ci = Falta la cédula de identidad.\n"
                        + "* Foto = Falta fotografía del productor.\n"
                        + "* f1, f2... = Fase en que se imprimió el carnet; gris = fase anterior.",
                CELDA));
        leyenda.setBorder(Rectangle.NO_BORDER);
        leyenda.setPaddingLeft(5);
        leyenda.setPaddingRight(12);
        bloque.addCell(leyenda);
        PdfPCell fase = new PdfPCell(new Phrase("FASE " + numeroFase, FASE_DESTACADA));
        fase.setBorder(Rectangle.BOX);
        fase.setBorderColor(NEGRO);
        fase.setBorderWidth(1.2f);
        fase.setMinimumHeight(62);
        fase.setHorizontalAlignment(Element.ALIGN_CENTER);
        fase.setVerticalAlignment(Element.ALIGN_MIDDLE);
        bloque.addCell(fase);
        return bloque;
    }

    private void sindicato(Document documento, PdfWriter escritor,
                           InformeFaseImpresion informe,
                           InformeFaseImpresion.SeccionSindicato seccion,
                           PdfPTable bloqueConstancia)
            throws DocumentException {
        documento.add(tabla(seccion, informe.numeroFase()));
        documento.add(resumenSindicato(seccion, informe.numeroFase()));
        constanciaAlPie(documento, escritor, informe, seccion, bloqueConstancia);
    }

    private void constanciaAlPie(Document documento, PdfWriter escritor,
                                 InformeFaseImpresion informe,
                                 InformeFaseImpresion.SeccionSindicato seccion,
                                 PdfPTable bloque)
            throws DocumentException {
        float bordeInferior = 50;
        float techo = bordeInferior + bloque.getTotalHeight();
        if (escritor.getVerticalPosition(true) < techo + 12) {
            documento.newPage();
            encabezado(documento, informe, seccion);
        }
        bloque.writeSelectedRows(0, -1, documento.left(), techo,
                escritor.getDirectContent());
    }

    PdfPTable tabla(InformeFaseImpresion.SeccionSindicato seccion, int numeroFase)
            throws DocumentException {
        String[] titulos = {"N°", "NOMBRES", "APELLIDOS", "C.I.", "N° LOTE",
                "OBSERVACIONES"};
        PdfPTable tabla = new PdfPTable(titulos.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{.4f, 1.3f, 1.6f, .85f, .8f, 2.8f});
        PdfPCell nombre = new PdfPCell(new Phrase("SINDICATO: " + seccion.sindicato(), SECCION));
        nombre.setColspan(titulos.length);
        nombre.setBorderColor(NEGRO);
        nombre.setPadding(4);
        tabla.addCell(nombre);
        for (String titulo : titulos) tabla.addCell(cabecera(titulo));
        tabla.setHeaderRows(2);
        int numero = 1;
        for (InformeFaseImpresion.Fila fila : seccion.productores()) {
            tabla.addCell(dato(numero++, Element.ALIGN_RIGHT));
            tabla.addCell(dato(fila.nombres(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.apellidos(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.ci(), Element.ALIGN_LEFT));
            tabla.addCell(dato(fila.lotes(), Element.ALIGN_CENTER));
            PdfPCell observaciones = dato(observaciones(fila), Element.ALIGN_LEFT);
            List<Integer> marcas = fila.fasesImpresas();
            if (!marcas.isEmpty()) {
                observaciones.setPaddingRight(6 + marcas.size() * 17f);
                observaciones.setCellEvent((celda, posicion, lienzos) ->
                        dibujarFases(posicion, lienzos, marcas, numeroFase));
            }
            tabla.addCell(observaciones);
        }
        if (seccion.productores().isEmpty()) {
            PdfPCell vacia = dato("El sindicato no tiene productores.", Element.ALIGN_CENTER);
            vacia.setColspan(titulos.length);
            vacia.setPadding(8);
            tabla.addCell(vacia);
        }
        return tabla;
    }

    private String observaciones(InformeFaseImpresion.Fila fila) {
        List<String> notas = new ArrayList<>(6);
        if (fila.lotes() == null || fila.lotes().isBlank()) notas.add("N° lote");
        if (fila.datosFaltantes().stream().anyMatch("Revisión SIE pendiente"::equalsIgnoreCase)) {
            notas.add("Sie");
        }
        if (fila.observado()) notas.add("Obs");
        if (fila.ci() == null || fila.ci().isBlank()) notas.add("Ci");
        if (fila.datosFaltantes().stream().anyMatch("Fotografía"::equalsIgnoreCase)) {
            notas.add("Foto");
        }
        if (fila.reimpreso()) notas.add("CARNET REIMPRESO");
        return String.join(", ", notas);
    }

    private void dibujarFases(Rectangle posicion, PdfContentByte[] lienzos,
                              List<Integer> fases, int numeroFase) {
        PdfContentByte figuras = lienzos[PdfPTable.LINECANVAS];
        PdfContentByte textos = lienzos[PdfPTable.TEXTCANVAS];
        float centroY = (posicion.getTop() + posicion.getBottom()) / 2;
        for (int i = 0; i < fases.size(); i++) {
            int fase = fases.get(fases.size() - 1 - i);
            float centroX = posicion.getRight() - 9 - i * 17f;
            Color tinta = fase < numeroFase ? GRIS : NEGRO;
            figuras.saveState();
            figuras.setColorStroke(tinta);
            figuras.setLineWidth(.6f);
            figuras.circle(centroX, centroY, 5.5f);
            figuras.stroke();
            figuras.restoreState();
            textos.saveState();
            textos.beginText();
            textos.setColorFill(tinta);
            textos.setFontAndSize(CABECERA.getBaseFont(), fase < 10 ? 5.5f : 4.5f);
            textos.showTextAligned(Element.ALIGN_CENTER, "f" + fase,
                    centroX, centroY - 1.8f, 0);
            textos.endText();
            textos.restoreState();
        }
    }

    private PdfPTable resumenSindicato(InformeFaseImpresion.SeccionSindicato seccion,
                                       int numeroFase) throws DocumentException {
        PdfPTable tabla = new PdfPTable(5);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(8);
        tabla.setSpacingAfter(8);
        String[] titulos = {"TOTAL", "IMPRESOS F" + numeroFase, "IMPRESOS ACUMULADOS",
                "PENDIENTES", "AVANCE"};
        for (String titulo : titulos) tabla.addCell(cabecera(titulo));
        int total = seccion.productores().size();
        int pendientes = total - seccion.impresosAcumulados();
        String avance = total == 0 ? "0 %"
                : Math.round(seccion.impresosAcumulados() * 100f / total) + " %";
        for (Object valor : List.of(total, seccion.impresosEnFase(),
                seccion.impresosAcumulados(), pendientes, avance)) {
            tabla.addCell(dato(valor, Element.ALIGN_CENTER));
        }
        return tabla;
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

    private PdfPTable constancia(InformeFaseImpresion.SeccionSindicato seccion)
            throws DocumentException {
        PdfPTable bloque = new PdfPTable(1);
        bloque.setWidthPercentage(100);
        bloque.setKeepTogether(true);
        bloque.setSpacingBefore(10);
        String entrega = seccion.impresosEnFase() == 0
                ? "No se entregaron carnets del sindicato " + seccion.sindicato()
                        + " en esta fase.\n\n"
                : "Recibí conforme " + seccion.impresosEnFase()
                        + " carnet(s) del sindicato " + seccion.sindicato() + ".\n\n";
        PdfPCell marco = new PdfPCell(new Phrase(
                "CONSTANCIA DE RECEPCIÓN\n\n" + entrega
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
        return FuentesInforme.fuente(tamano, estilo, color);
    }
}
