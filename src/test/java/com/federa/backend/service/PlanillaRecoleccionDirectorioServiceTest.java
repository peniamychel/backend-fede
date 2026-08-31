package com.federa.backend.service;

import com.federa.backend.dto.PlanillaRecoleccionDirectorio;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.ImagenCargo;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanillaRecoleccionDirectorioServiceTest {

    @Test
    void informaQueElementosYaEstanCargados() {
        CentralRepository centrales = mock(CentralRepository.class);
        SindicatoRepository sindicatos = mock(SindicatoRepository.class);
        CargoRepository cargos = mock(CargoRepository.class);
        Federacion federacion = Federacion.builder().id(1L).nombre("CARRASCO TROPICAL").build();
        Central central = Central.builder().id(13L).nombre("13 DE JUNIO")
                .selloClave("sellos/central.png").federacion(federacion).build();
        Sindicato primero = Sindicato.builder().id(1L).nombre("1RO DE MAYO")
                .selloClave("sellos/primero.png").build();
        Sindicato segundo = Sindicato.builder().id(2L).nombre("NUEVA ESPERANZA").build();
        Cargo secretario = Cargo.builder()
                .cargo(TipoCargo.SECRETARIO_GENERAL)
                .productor(Productor.builder().id(8L).nombres("MARÍA")
                        .apellidos("PÉREZ").build())
                .imagenes(List.of(ImagenCargo.builder().tipo(TipoImagenCargo.FIRMA).build()))
                .build();
        when(centrales.findById(13L)).thenReturn(Optional.of(central));
        when(sindicatos.findByCentralIdOrderByNombreAsc(13L))
                .thenReturn(List.of(primero, segundo));
        when(cargos.findByCentralIdAndCargoAndVigenteIsTrue(
                13L, TipoCargo.SECRETARIO_GENERAL)).thenReturn(Optional.of(secretario));

        PlanillaRecoleccionDirectorio informe = new PlanillaRecoleccionDirectorioService(
                centrales, sindicatos, cargos, mock(PlanillaRecoleccionDirectorioPdf.class))
                .obtener(13L);

        assertThat(informe.secretarioGeneral()).isEqualTo("MARÍA PÉREZ");
        assertThat(informe.selloCentralCargado()).isTrue();
        assertThat(informe.firmaCentralCargada()).isTrue();
        assertThat(informe.pieFirmaCentralCargado()).isFalse();
        assertThat(informe.sindicatos())
                .extracting(PlanillaRecoleccionDirectorio.FilaSindicato::selloCargado)
                .containsExactly(true, false);
    }

    @Test
    void generaUnPdfConEspaciosParaLaCentralYLosSindicatos() throws IOException {
        PlanillaRecoleccionDirectorio informe = new PlanillaRecoleccionDirectorio(
                13L, "CARRASCO TROPICAL", "13 DE JUNIO", "MARÍA PÉREZ",
                true, false, false,
                List.of(
                        new PlanillaRecoleccionDirectorio.FilaSindicato(
                                1L, "1RO DE MAYO", true),
                        new PlanillaRecoleccionDirectorio.FilaSindicato(
                                2L, "NUEVA ESPERANZA", false)));

        byte[] pdf = new PlanillaRecoleccionDirectorioPdf().generar(informe);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "planilla-recoleccion-directorio-muestra.pdf"), pdf);
        PdfReader lector = new PdfReader(pdf);
        try {
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                texto.append(new PdfTextExtractor(lector).getTextFromPage(pagina));
            }
            String contenido = texto.toString().replaceAll("\\s+", " ");
            assertThat(contenido).contains(
                    "PLANILLA DE RECOLECCIÓN DE SELLOS, FIRMAS Y PIES DE FIRMA",
                    "CENTRAL 13 DE JUNIO", "SELLO DE LA CENTRAL",
                    "FIRMA DEL SECRETARIO GENERAL", "PIE DE FIRMA",
                    "MARÍA PÉREZ", "1RO DE MAYO", "NUEVA ESPERANZA",
                    "CARGADO", "PENDIENTE", "SELLO DEL SINDICATO");
            assertThat(lector.getNumberOfPages()).isEqualTo(2);
        } finally {
            lector.close();
        }
    }
}
