package com.federa.backend.service;

import com.federa.backend.dto.InformeFaseImpresion;
import com.federa.backend.dto.InformePreImpresionCentral;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InformesFasesImpresionPdfTest {

    @Test
    void revisionPadronReservaFirmasManualesSinImagenes() throws Exception {
        var filas = java.util.stream.IntStream.rangeClosed(1, 60).mapToObj(i ->
                new InformePreImpresionCentral.Fila((long) i, "MARÍA " + i, "PÉREZ MAMANI",
                        "123456" + i, String.valueOf(i), false, List.<String>of())).toList();
        var informe = new InformePreImpresionCentral(13L, "13 DE JUNIO", "CARRASCO TROPICAL", 60,
                List.of(new InformePreImpresionCentral.SeccionSindicato(21L, "VILLAZÓN", filas)));
        byte[] pdf = new InformeRevisionPadronCentralPdf().generar(informe);
        var tabla = new InformeRevisionPadronCentralPdf().tabla(informe.sindicatos().get(0));
        assertThat(tabla.getRows()).hasSize(2 + 60 + 12);
        for (int fila = 62; fila < 74; fila++) {
            var celdas = tabla.getRow(fila).getCells();
            assertThat(celdas[0].getColspan()).isEqualTo(6);
            for (int c = 1; c < 6; c++) assertThat(celdas[c]).isNull();
        }
        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages()).isGreaterThan(1);
            var extractor = new PdfTextExtractor(lector);
            assertThat(extractor.getTextFromPage(1)).contains("* Obs = Productor observado revisión general");
            String ultima = extractor.getTextFromPage(lector.getNumberOfPages());
            assertThat(ultima).contains("Sello, firma y pie de firma", "SECRETARIO GENERAL", "SINDICATO VILLAZÓN", "CENTRAL 13 DE JUNIO");
            for (int p = 1; p < lector.getNumberOfPages(); p++) {
                assertThat(extractor.getTextFromPage(p)).doesNotContain("Sello, firma y pie de firma");
            }
            for (int p = 2; p <= lector.getNumberOfPages(); p++) {
                assertThat(extractor.getTextFromPage(p)).doesNotContain("* N° lote =", "* Sie =", "* Obs =", "* Ci =");
            }
            var recursos = lector.getPageN(lector.getNumberOfPages())
                    .getAsDict(com.lowagie.text.pdf.PdfName.RESOURCES)
                    .getAsDict(com.lowagie.text.pdf.PdfName.XOBJECT);
            assertThat(recursos == null || recursos.size() == 0).isTrue();
        } finally { lector.close(); }
        guardarMuestra("revision-padron-firmas-60.pdf", pdf);
    }

    @Test
    void revisionPadronSoloMuestraObservacionesSolicitadasYAumentaVeintePorCiento()
            throws Exception {
        InformePreImpresionCentral informe = new InformePreImpresionCentral(
                13L, "13 DE JUNIO", "FEDERACIÓN CARRASCO TROPICAL", 5,
                List.of(new InformePreImpresionCentral.SeccionSindicato(
                        21L, "1RO DE MAYO", List.of(
                        new InformePreImpresionCentral.Fila(
                                1L, "MARÍA", "PÉREZ", "", "",
                                true, List.of("Fotografía", "Cédula", "Revisión SIE pendiente")),
                        new InformePreImpresionCentral.Fila(
                                2L, "JUAN", "MAMANI", "654321", "",
                                false, List.of("Fotografía", "Número de lote")),
                        new InformePreImpresionCentral.Fila(
                                3L, "ANA", "ROJAS", "", "23",
                                false, List.of("Cédula")),
                        new InformePreImpresionCentral.Fila(
                                4L, "LUIS", "QUISPE", "789012", "24",
                                false, List.of("Revisión SIE pendiente")),
                        new InformePreImpresionCentral.Fila(
                                5L, "ROSA", "LÓPEZ", "345678", "25",
                                false, List.of("Fotografía"))))));

        byte[] pdf = new InformeRevisionPadronCentralPdf().generar(informe);
        String texto = texto(pdf);

        assertThat(texto)
                .contains("INFORME DE REVISIÓN DEL PADRÓN DE PRODUCTORES")
                .doesNotContain("Espacios para nuevos registros")
                .contains("SECRETARIO GENERAL")
                .contains("* N° lote = Falta el número de lote")
                .contains("* Sie = Revisar nombre y apellidos frente a la cédula de identidad")
                .contains("* Obs = Productor observado revisión general")
                .contains("* Ci = Falta la cédula de identidad")
                .contains("N° lote, Sie, Obs, Ci")
                .doesNotContain("NOMBRE OBSERVADO")
                .doesNotContain("SIN NÚMERO DE LOTE")
                .doesNotContain("Revisión SIE pendiente")
                .doesNotContain("Fotografía");
        assertThat(InformeRevisionPadronCentralPdf.cantidadFilasEnBlanco(100))
                .isEqualTo(20);
        assertThat(InformeRevisionPadronCentralPdf.cantidadFilasEnBlanco(6))
                .isEqualTo(2);
        assertThat(InformeRevisionPadronCentralPdf.cantidadFilasEnBlanco(0))
                .isZero();
        guardarMuestra("informe-revision-padron-muestra.pdf", pdf);
    }

    @Test
    void preImpresionEsUnSoloListadoSinEstadoDeCarnet() throws Exception {
        InformePreImpresionCentral informe = new InformePreImpresionCentral(
                13L, "13 DE JUNIO", "FEDERACIÓN CARRASCO TROPICAL", 3,
                List.of(
                        new InformePreImpresionCentral.SeccionSindicato(
                                21L, "1RO DE MAYO", List.of(
                                new InformePreImpresionCentral.Fila(
                                        1L, "MARÍA", "PÉREZ", "123456", "22 A",
                                        false, List.of()),
                                new InformePreImpresionCentral.Fila(
                                        2L, "JUAN", "MAMANI", "", "",
                                        true,
                                        List.of("Observado", "Cédula", "Número de lote",
                                                "Revisión SIE pendiente", "Fotografía")))),
                        new InformePreImpresionCentral.SeccionSindicato(
                                22L, "NUEVA ESPERANZA", List.of(
                                new InformePreImpresionCentral.Fila(
                                        3L, "ANA", "QUISPE", "987654", "23",
                                        false, List.of())))));

        byte[] pdf = new InformePreImpresionCentralPdf().generar(informe);
        String texto = texto(pdf);

        assertThat(texto)
                .contains("INFORME DE REVISIÓN DE DATOS DE IMPRESIÓN DE CARNET DE PRODUCTORES")
                .contains("DATOS FALTANTES")
                .contains("* N° lote = Falta el número de lote")
                .contains("* Sie = Revisar nombre y apellidos frente a la cédula de identidad")
                .contains("* Obs = Productor observado revisión general")
                .contains("* Ci = Falta la cédula de identidad")
                .contains("* Foto = Falta fotografía del productor")
                .contains("N° lote, Sie, Obs, Ci, Foto")
                .contains("CONSTANCIA DE ENTREGA DE INFORME")
                .doesNotContain("(OBSERVADO)")
                .doesNotContain("Revisión SIE pendiente")
                .doesNotContain("CÓDIGO")
                .doesNotContain("CLASIFICACIÓN")
                .doesNotContain("COMPLETO")
                .doesNotContain("CARNET IMPRESO");
        var generador = new InformePreImpresionCentralPdf();
        var tablaPrimera = generador.tabla(informe.sindicatos().get(0));
        assertThat(tablaPrimera.getRows()).hasSize(2 + 2 + 2);
        for (int fila = 4; fila < 6; fila++) {
            var celdas = tablaPrimera.getRow(fila).getCells();
            assertThat(celdas[0].getColspan()).isEqualTo(6);
            for (int c = 1; c < 6; c++) assertThat(celdas[c]).isNull();
        }
        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages()).isEqualTo(2);
            String primera = textoPagina(lector, 1);
            String segunda = textoPagina(lector, 2);
            assertThat(primera).contains("SINDICATO: 1RO DE MAYO", "Página 1 de 1")
                    .doesNotContain("NUEVA ESPERANZA");
            assertThat(segunda).contains("SINDICATO: NUEVA ESPERANZA", "Página 1 de 1")
                    .doesNotContain("1RO DE MAYO");
        } finally {
            lector.close();
        }
        guardarMuestra("informe-pre-impresion-muestra.pdf", pdf);
    }

    @Test
    void preImpresionEscalaFilasEnBlancoYLeyendaSoloEnPrimeraPagina() throws Exception {
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(0)).isEqualTo(2);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(9)).isEqualTo(2);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(10)).isEqualTo(3);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(29)).isEqualTo(3);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(30)).isEqualTo(4);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(49)).isEqualTo(4);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(50)).isEqualTo(5);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(69)).isEqualTo(5);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(70)).isEqualTo(6);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(99)).isEqualTo(6);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(100)).isEqualTo(7);
        assertThat(InformePreImpresionCentralPdf.cantidadFilasEnBlanco(200)).isEqualTo(7);

        var filas = java.util.stream.IntStream.rangeClosed(1, 120).mapToObj(i ->
                new InformePreImpresionCentral.Fila((long) i, "MARÍA " + i,
                        "PÉREZ MAMANI", "123456" + i, String.valueOf(i), false,
                        List.<String>of())).toList();
        var informe = new InformePreImpresionCentral(13L, "13 DE JUNIO", "CARRASCO TROPICAL",
                120, List.of(new InformePreImpresionCentral.SeccionSindicato(
                21L, "VILLAZÓN", filas)));
        byte[] pdf = new InformePreImpresionCentralPdf().generar(informe);
        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages()).isGreaterThan(1);
            assertThat(textoPagina(lector, 1)).contains("* Foto = Falta fotografía del productor");
            for (int p = 2; p <= lector.getNumberOfPages(); p++) {
                assertThat(textoPagina(lector, p)).doesNotContain("* Foto =", "* Sie =");
            }
        } finally { lector.close(); }
        guardarMuestra("informe-pre-impresion-120.pdf", pdf);
    }

    @Test
    void informeDeFaseListaATodosConMarcasDeImpresionYResumen() throws Exception {
        InformeFaseImpresion informe = new InformeFaseImpresion(
                13L, "13 DE JUNIO", "FEDERACIÓN CARRASCO TROPICAL",
                7L, 2, LocalDateTime.of(2026, 9, 11, 8, 0), null, 2, 2,
                List.of(
                        new InformeFaseImpresion.SeccionSindicato(
                                21L, "1RO DE MAYO",
                                List.of(new InformeFaseImpresion.Fila(
                                        1L, "MARÍA", "PÉREZ", "123456", "22 A",
                                        false, List.of(), List.of(1, 2), true),
                                new InformeFaseImpresion.Fila(
                                        2L, "JUAN", "MAMANI", "", "",
                                        true, List.of("Cédula", "Número de lote",
                                                "Revisión SIE pendiente", "Fotografía"),
                                        List.of(), false)), 1, 1),
                        new InformeFaseImpresion.SeccionSindicato(
                                22L, "NUEVA ESPERANZA",
                                List.of(new InformeFaseImpresion.Fila(
                                        3L, "ANA", "QUISPE", "987654", "23",
                                        false, List.of(), List.of(1), false),
                                new InformeFaseImpresion.Fila(
                                        4L, "LUIS", "ROJAS", "456789", "",
                                        false, List.of("Número de lote"), List.of(), false)),
                                0, 1)));

        byte[] pdf = new InformeFaseImpresionPdf().generar(informe);
        String texto = texto(pdf);

        assertThat(texto)
                .contains("FASE 2 DE IMPRESIÓN")
                .contains("* Foto = Falta fotografía del productor")
                .contains("N° lote, Sie, Obs, Ci, Foto")
                .contains("CARNET REIMPRESO")
                .contains("IMPRESOS F2", "IMPRESOS ACUMULADOS", "PENDIENTES", "AVANCE")
                .contains("f1", "f2")
                .doesNotContain("PENDIENTES POR DATOS U OBSERVACIONES")
                .doesNotContain("OBSERVADO: REVISAR CÉDULA")
                .doesNotContain("CLASIFICACIÓN")
                .doesNotContain("CARNETS IMPRESOS EN LA FASE");
        assertThat(new InformeFaseImpresionPdf().tabla(informe.sindicatos().get(0), 2)
                .getRows()).hasSize(4);
        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages()).isEqualTo(2);
            String primera = textoPagina(lector, 1);
            String segunda = textoPagina(lector, 2);
            assertThat(primera).contains("SINDICATO: 1RO DE MAYO", "Página 1 de 1")
                    .doesNotContain("NUEVA ESPERANZA");
            assertThat(primera).containsOnlyOnce("CARNET REIMPRESO");
            assertThat(segunda).contains("SINDICATO: NUEVA ESPERANZA", "Página 1 de 1")
                    .contains("CONSTANCIA DE RECEPCIÓN")
                    .contains("No se entregaron carnets del sindicato NUEVA ESPERANZA en esta fase")
                    .doesNotContain("1RO DE MAYO")
                    .doesNotContain("CARNET REIMPRESO");
        } finally {
            lector.close();
        }
        guardarMuestra("informe-fase-impresion-muestra.pdf", pdf);
    }

    @Test
    void distintivosDeFaseNoInvadenLasFilasVecinas() throws Exception {
        var filas = java.util.stream.IntStream.rangeClosed(1, 75).mapToObj(i ->
                new InformeFaseImpresion.Fila((long) i, "NOMBRE " + i, "APELLIDO",
                        String.valueOf(100000 + i), "", false,
                        List.of("Número de lote"), i % 3 == 0 ? List.of() : List.of(1),
                        false)).toList();
        var seccion = new InformeFaseImpresion.SeccionSindicato(
                21L, "VILLAZÓN", filas, 50, 50);
        var generador = new InformeFaseImpresionPdf();
        var tabla = generador.tabla(seccion, 1);
        tabla.setTotalWidth(552);
        tabla.calculateHeights(true);
        float altoFilaSinCirculo = tabla.getRow(4).getMaxHeights();
        for (int i = 0; i < filas.size(); i++) {
            if (!filas.get(i).fasesImpresas().isEmpty()) {
                assertThat(tabla.getRow(i + 2).getMaxHeights())
                        .isCloseTo(altoFilaSinCirculo, org.assertj.core.data.Offset.offset(.1f));
            }
        }
        var informe = new InformeFaseImpresion(13L, "13 DE JUNIO", "CARRASCO TROPICAL",
                7L, 1, LocalDateTime.of(2026, 9, 11, 8, 0), null, 50, 25,
                List.of(seccion));
        byte[] pdf = generador.generar(informe);
        PdfReader lector = new PdfReader(pdf);
        try {
            String ultimaPagina = textoPagina(lector, lector.getNumberOfPages());
            assertThat(ultimaPagina).contains("NOMBRE 75", "CONSTANCIA DE RECEPCIÓN");
        } finally { lector.close(); }
        guardarMuestra("informe-fase-distintivos-75.pdf", pdf);
    }

    private String texto(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        try {
            StringBuilder resultado = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                resultado.append(extractor.getTextFromPage(pagina)).append(' ');
            }
            return resultado.toString().replaceAll("\\s+", " ");
        } finally {
            lector.close();
        }
    }

    private String textoPagina(PdfReader lector, int pagina) throws Exception {
        return new PdfTextExtractor(lector).getTextFromPage(pagina)
                .replaceAll("\\s+", " ");
    }

    private void guardarMuestra(String nombre, byte[] pdf) throws Exception {
        Path directorio = Path.of("target", "pdf-qa");
        Files.createDirectories(directorio);
        Files.write(directorio.resolve(nombre), pdf);
    }
}
