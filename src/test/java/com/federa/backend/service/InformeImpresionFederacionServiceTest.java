package com.federa.backend.service;

import com.federa.backend.dto.InformeImpresionCentral;
import com.federa.backend.dto.InformeImpresionFederacion;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.repository.CentralRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InformeImpresionFederacionServiceTest {

    @Test
    void consolidaLosTotalesDeTodasLasCentrales() {
        FederacionService federaciones = mock(FederacionService.class);
        CentralRepository centrales = mock(CentralRepository.class);
        InformeImpresionCentralService informes = mock(InformeImpresionCentralService.class);
        Federacion federacion = Federacion.builder().id(1L).nombre("CARRASCO TROPICAL").build();
        Central primera = Central.builder().id(10L).nombre("13 DE JUNIO")
                .federacion(federacion).build();
        Central segunda = Central.builder().id(11L).nombre("CHIMORÉ")
                .federacion(federacion).build();
        when(federaciones.buscar(1L)).thenReturn(federacion);
        when(centrales.findByFederacionIdOrderByNombreAsc(1L))
                .thenReturn(List.of(primera, segunda));
        when(informes.obtener(10L)).thenReturn(informe(primera, 2, 1, 12, 9, 2, 1, 2));
        when(informes.obtener(11L)).thenReturn(informe(segunda, 3, 2, 8, 3, 4, 1, 3));

        InformeImpresionFederacion resultado = new InformeImpresionFederacionService(
                federaciones, centrales, informes,
                mock(InformeImpresionFederacionPdf.class)).obtener(1L);

        assertThat(resultado.centrales()).isEqualTo(2);
        assertThat(resultado.sindicatos()).isEqualTo(5);
        assertThat(resultado.sindicatosSinSello()).isEqualTo(3);
        assertThat(resultado.total()).isEqualTo(20);
        assertThat(resultado.impresos()).isEqualTo(12);
        assertThat(resultado.pendientes()).isEqualTo(8);
        assertThat(resultado.pendientesConFoto()).isEqualTo(6);
        assertThat(resultado.sinFoto()).isEqualTo(2);
        assertThat(resultado.listosParaImprimir()).isEqualTo(5);
        assertThat(resultado.porcentajeAvance()).isEqualTo(60.0);
        assertThat(resultado.detalle()).extracting(InformeImpresionCentral::central)
                .containsExactly("13 DE JUNIO", "CHIMORÉ");
    }

    @Test
    void elPdfIncluyeResumenGlobalYCadaSindicato() throws IOException {
        InformeImpresionCentral central = new InformeImpresionCentral(
                10L, "13 DE JUNIO", "CARRASCO TROPICAL",
                1, 1, 12, 9, 3, 2, 1, 2, 75,
                List.of(new InformeImpresionCentral.FilaSindicato(
                        100L, "1RO DE MAYO", false,
                        12, 9, 3, 2, 1, 2, 75)));
        InformeImpresionFederacion informe = new InformeImpresionFederacion(
                1L, "CARRASCO TROPICAL", 1, 1, 1,
                12, 9, 3, 2, 1, 2, 75, List.of(central));

        byte[] pdf = new InformeImpresionFederacionPdf().generar(informe);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "avance-general-impresion-muestra.pdf"), pdf);
        PdfReader lector = new PdfReader(pdf);
        try {
            StringBuilder texto = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina)).append(' ');
            }
            String normalizado = texto.toString().replaceAll("\\s+", " ");
            assertThat(normalizado).contains(
                    "AVANCE GENERAL DE IMPRESIÓN DE CREDENCIALES",
                    "CARRASCO TROPICAL", "RESUMEN POR CENTRAL",
                    "13 DE JUNIO", "DETALLE POR CENTRAL Y SINDICATO",
                    "1RO DE MAYO", "SIND. SIN SELLO", "FALTA", "75.0%");
            assertThat(lector.getPageSizeWithRotation(1).getWidth())
                    .isGreaterThan(lector.getPageSizeWithRotation(1).getHeight());
        } finally {
            lector.close();
        }
    }

    private static InformeImpresionCentral informe(Central central, int sindicatos,
                                                     int sinSello, int total, int impresos,
                                                     int conFoto, int sinFoto, int listos) {
        return new InformeImpresionCentral(
                central.getId(), central.getNombre(), central.getFederacion().getNombre(),
                sindicatos, sinSello, total, impresos, total - impresos,
                conFoto, sinFoto, listos,
                total == 0 ? 0 : Math.round(impresos * 1000d / total) / 10d,
                List.of());
    }
}
