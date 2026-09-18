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
    void revisionPadronSoloMuestraObservacionesSolicitadasYAumentaVeintePorCiento()
            throws Exception {
        InformePreImpresionCentral informe = new InformePreImpresionCentral(
                13L, "13 DE JUNIO", "FEDERACIÓN CARRASCO TROPICAL", 2,
                List.of(new InformePreImpresionCentral.SeccionSindicato(
                        21L, "1RO DE MAYO", List.of(
                        new InformePreImpresionCentral.Fila(
                                1L, "MARÍA", "PÉREZ", "123456", "22 A",
                                true, List.of("Fotografía", "Cédula")),
                        new InformePreImpresionCentral.Fila(
                                2L, "JUAN", "MAMANI", "654321", "",
                                false, List.of("Fotografía", "Número de lote"))))));

        byte[] pdf = new InformeRevisionPadronCentralPdf().generar(informe);
        String texto = texto(pdf);

        assertThat(texto)
                .contains("INFORME DE REVISIÓN DEL PADRÓN DE PRODUCTORES")
                .contains("Espacios para nuevos registros: 1")
                .contains("NOMBRE OBSERVADO")
                .contains("SIN NÚMERO DE LOTE")
                .doesNotContain("Fotografía")
                .doesNotContain("Cédula");
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
                                        List.of("Observado", "Cédula", "Número de lote")))),
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
                .contains("(OBSERVADO)")
                .contains("CONSTANCIA DE ENTREGA DE INFORME")
                .doesNotContain("CÓDIGO")
                .doesNotContain("CLASIFICACIÓN")
                .doesNotContain("COMPLETO")
                .doesNotContain("CARNET IMPRESO");
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
    void informeDeFaseDistingueImpresosPendientesYReimpresiones() throws Exception {
        InformeFaseImpresion informe = new InformeFaseImpresion(
                13L, "13 DE JUNIO", "FEDERACIÓN CARRASCO TROPICAL",
                7L, 2, LocalDateTime.of(2026, 9, 11, 8, 0), null, 2, 2,
                List.of(
                        new InformeFaseImpresion.SeccionSindicato(
                                21L, "1RO DE MAYO",
                                List.of(new InformeFaseImpresion.Fila(
                                        1L, "MARÍA", "PÉREZ", "123456", "22 A",
                                        List.of(), true)),
                                List.of(new InformeFaseImpresion.Fila(
                                        2L, "JUAN", "MAMANI", "", "",
                                        List.of("OBSERVADO: REVISAR CÉDULA"),
                                        false))),
                        new InformeFaseImpresion.SeccionSindicato(
                                22L, "NUEVA ESPERANZA",
                                List.of(new InformeFaseImpresion.Fila(
                                        3L, "ANA", "QUISPE", "987654", "23",
                                        List.of(), false)),
                                List.of(new InformeFaseImpresion.Fila(
                                        4L, "LUIS", "ROJAS", "456789", "",
                                        List.of("NÚMERO DE LOTE"), false)))));

        byte[] pdf = new InformeFaseImpresionPdf().generar(informe);
        String texto = texto(pdf);

        assertThat(texto)
                .contains("FASE 2 DE IMPRESIÓN")
                .contains("PENDIENTES POR DATOS U OBSERVACIONES")
                .contains("CARNET REIMPRESO")
                .contains("OBSERVADO: REVISAR CÉDULA")
                .doesNotContain("CLASIFICACIÓN")
                .doesNotContain("CARNETS IMPRESOS EN LA FASE");
        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages()).isEqualTo(2);
            String primera = textoPagina(lector, 1);
            String segunda = textoPagina(lector, 2);
            assertThat(primera).contains("SINDICATO: 1RO DE MAYO", "Página 1 de 1")
                    .doesNotContain("NUEVA ESPERANZA");
            assertThat(primera).containsOnlyOnce("CARNET REIMPRESO");
            assertThat(segunda).contains("SINDICATO: NUEVA ESPERANZA", "Página 1 de 1")
                    .doesNotContain("1RO DE MAYO")
                    .doesNotContain("CARNET REIMPRESO");
        } finally {
            lector.close();
        }
        guardarMuestra("informe-fase-impresion-muestra.pdf", pdf);
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
