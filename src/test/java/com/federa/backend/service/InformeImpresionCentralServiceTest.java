package com.federa.backend.service;

import com.federa.backend.dto.InformeImpresionCentral;
import com.federa.backend.dto.InformeNominalImpresionCentral;
import com.federa.backend.model.Central;
import com.federa.backend.model.FaseImpresionCarnet;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Productor;
import com.federa.backend.model.ProductorFaseImpresion;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.TenenciaLote;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.FaseImpresionCarnetRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.ProductorFaseImpresionRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.repository.TenenciaLoteRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

class InformeImpresionCentralServiceTest {

    @Test
    void consolidaLosMismosEstadosDeCadaSindicato() {
        CentralRepository centrales = mock(CentralRepository.class);
        SindicatoRepository sindicatos = mock(SindicatoRepository.class);
        ProductorRepository productores = mock(ProductorRepository.class);
        TenenciaLoteRepository tenencias = mock(TenenciaLoteRepository.class);
        FaseImpresionCarnetRepository fases = mock(FaseImpresionCarnetRepository.class);
        ProductorFaseImpresionRepository participantes =
                mock(ProductorFaseImpresionRepository.class);
        CredencialService credenciales = mock(CredencialService.class);
        Federacion federacion = Federacion.builder().id(1L).nombre("FEDERA").build();
        Central central = Central.builder().id(13L).nombre("13 DE JUNIO")
                .federacion(federacion).build();
        Sindicato primero = Sindicato.builder().id(1L).nombre("1RO DE MAYO")
                .selloClave("sellos/1ro-de-mayo.png").build();
        Sindicato segundo = Sindicato.builder().id(2L).nombre("NUEVA ESPERANZA").build();
        when(centrales.findById(13L)).thenReturn(Optional.of(central));
        when(sindicatos.findByCentralIdOrderByNombreAsc(13L))
                .thenReturn(List.of(primero, segundo));
        List<Productor> padron = productoresParaInforme(primero, segundo);
        when(productores.findBySindicatoCentralIdOrderByApellidosAscNombresAsc(13L))
                .thenReturn(padron);
        TenenciaLote sistemaPorParcela = new TenenciaLote();
        sistemaPorParcela.setProductor(padron.get(0));
        sistemaPorParcela.setLote(Lote.builder().estadoLote(EstadoLote.CON_SISTEMA).build());
        sistemaPorParcela.iniciar(LocalDate.of(2026, 1, 1));
        when(tenencias.findVigentesDeProductores(anyList()))
                .thenReturn(List.of(sistemaPorParcela));
        FaseImpresionCarnet fase1 = fase(101L, central, 1);
        FaseImpresionCarnet fase2 = fase(102L, central, 2);
        when(fases.findByCentralIdOrderByNumeroAsc(13L))
                .thenReturn(List.of(fase1, fase2));
        when(participantes.findTodosDeFase(101L)).thenReturn(List.of(
                participante(fase1, padron.get(0), false),
                participante(fase1, padron.get(10), false)));
        when(participantes.findTodosDeFase(102L)).thenReturn(List.of(
                participante(fase2, padron.get(0), true),
                participante(fase2, padron.get(1), false)));
        when(credenciales.panelImpresionSindicato(1L)).thenReturn(
                panel(1L, primero.getNombre(), 10, 4, 5, 1, 3));
        when(credenciales.panelImpresionSindicato(2L)).thenReturn(
                panel(2L, segundo.getNombre(), 6, 3, 1, 2, 1));

        InformeImpresionCentralService servicio = new InformeImpresionCentralService(
                centrales, sindicatos, productores, tenencias, fases, participantes, credenciales,
                mock(InformeImpresionCentralPdf.class));
        InformeImpresionCentral informe = servicio.obtener(13L);

        assertThat(informe.total()).isEqualTo(16);
        assertThat(informe.impresos()).isEqualTo(7);
        assertThat(informe.pendientes()).isEqualTo(9);
        assertThat(informe.pendientesConFoto()).isEqualTo(6);
        assertThat(informe.sinFoto()).isEqualTo(3);
        assertThat(informe.listosParaImprimir()).isEqualTo(4);
        assertThat(informe.observados()).isEqualTo(3);
        assertThat(informe.sistema()).isEqualTo(8);
        assertThat(informe.sinSistema()).isEqualTo(8);
        assertThat(informe.sindicatosSinSello()).isEqualTo(1);
        assertThat(informe.detalle()).extracting(
                InformeImpresionCentral.FilaSindicato::selloCargado)
                .containsExactly(true, false);
        assertThat(informe.porcentajeAvance()).isEqualTo(43.8);
        assertThat(informe.detalle()).extracting(InformeImpresionCentral.FilaSindicato::sindicato)
                .containsExactly("1RO DE MAYO", "NUEVA ESPERANZA");
        assertThat(informe.detalle().get(0).porcentajeAvance()).isEqualTo(40.0);
        assertThat(informe.detalle().get(0).observados()).isEqualTo(1);
        assertThat(informe.detalle().get(0).sistema()).isEqualTo(6);
        assertThat(informe.detalle().get(0).sinSistema()).isEqualTo(4);
        assertThat(informe.avancesFase()).extracting(
                InformeImpresionCentral.AvanceFase::numeroFase)
                .containsExactly(1, 2);
        assertThat(informe.avancesFase()).extracting(
                InformeImpresionCentral.AvanceFase::porcentajeAvance)
                .containsExactly(12.5, 43.8);
        assertThat(informe.detalle().get(0).avancesFase()).extracting(
                InformeImpresionCentral.AvanceFase::porcentajeAvance)
                .containsExactly(10.0, 40.0);
        assertThat(informe.detalle().get(1).avancesFase()).extracting(
                InformeImpresionCentral.AvanceFase::porcentajeAvance)
                .containsExactly(16.7, 50.0);
    }

    @Test
    void elPdfIncluyeTotalesYDetalle() throws IOException {
        InformeImpresionCentral informe = new InformeImpresionCentral(
                13L, "13 DE JUNIO", "FEDERA", 1, 1, 10, 4, 6, 5, 1, 3,
                2, 6, 4, 40,
                List.of(
                        new InformeImpresionCentral.AvanceFase(1, 30),
                        new InformeImpresionCentral.AvanceFase(2, 40)),
                List.of(new InformeImpresionCentral.FilaSindicato(
                        1L, "1RO DE MAYO", false, 10, 4, 6, 5, 1, 3,
                        2, 6, 4, 40,
                        List.of(
                                new InformeImpresionCentral.AvanceFase(1, 30),
                                new InformeImpresionCentral.AvanceFase(2, 40)))));

        byte[] pdf = new InformeImpresionCentralPdf().generar(informe);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "avance-impresion-muestra.pdf"), pdf);
        PdfReader lector = new PdfReader(pdf);
        try {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1)
                    .replaceAll("\\s+", " ");
            assertThat(texto).contains(
                    "AVANCE DE IMPRESIÓN DE CARNETS DE PRODUCTOR", "13 DE JUNIO",
                    "1RO DE MAYO", "AVANCE FASE 1", "AVANCE FASE 2",
                    "30.0%", "40.0%", "SIND. SIN SELLO", "FALTA");
            assertThat(texto.split("AVANCE FASE 2", -1).length - 1).isEqualTo(2);
            String textoCompacto = texto.replaceAll("\\s+", "");
            assertThat(textoCompacto).contains(
                    "PENDIENTES", "OBSERVADOS", "SISTEMA", "SINSISTEMA");
            assertThat(textoCompacto).doesNotContain("CONFOTO", "LISTOS");
        } finally {
            lector.close();
        }
    }

    @Test
    void elInformeNominalRespetaLaSeleccionDeSindicatos() {
        CentralRepository centrales = mock(CentralRepository.class);
        SindicatoRepository sindicatos = mock(SindicatoRepository.class);
        CredencialService credenciales = mock(CredencialService.class);
        Central central = Central.builder().id(13L).nombre("13 DE JUNIO")
                .federacion(Federacion.builder().id(1L).nombre("FEDERA").build()).build();
        Sindicato primero = Sindicato.builder().id(1L).nombre("1RO DE MAYO").build();
        Sindicato segundo = Sindicato.builder().id(2L).nombre("NUEVA ESPERANZA").build();
        when(centrales.findById(13L)).thenReturn(Optional.of(central));
        when(sindicatos.findByCentralIdOrderByNombreAsc(13L))
                .thenReturn(List.of(primero, segundo));
        when(credenciales.estadoNominalSindicato(2L)).thenReturn(
                new CredencialService.EstadoNominalSindicato(
                        2L, segundo.getNombre(),
                        List.of(filaNominal(8L, "MARÍA", "PÉREZ", List.of(), 2)),
                        List.of(
                                filaNominal(9L, "JUAN", "MAMANI",
                                        List.of("Fotografía", "Sello del sindicato",
                                                "Sello de la central",
                                                "Secretario General de la central",
                                                "Firma del secretario general de la central",
                                                "Cédula", "Número de lote", "Observado"), 0),
                                filaNominal(10L, "ANA", "QUISPE",
                                        List.of("Sello del sindicato",
                                                "Sello de la central"), 0))));

        InformeNominalImpresionCentralService servicio =
                new InformeNominalImpresionCentralService(
                        centrales, sindicatos, credenciales,
                        mock(InformeNominalImpresionCentralPdf.class));
        InformeNominalImpresionCentral informe = servicio.obtener(13L, List.of(2L));

        assertThat(informe.sindicatos()).extracting(
                InformeNominalImpresionCentral.SeccionSindicato::sindicato)
                .containsExactly("NUEVA ESPERANZA");
        assertThat(informe.totalImpresos()).isEqualTo(1);
        assertThat(informe.totalFaltantesDatos()).isEqualTo(1);
        assertThat(informe.sindicatos().get(0).faltantesDatos())
                .extracting(InformeNominalImpresionCentral.Fila::productorId)
                .containsExactly(9L);
        assertThat(informe.sindicatos().get(0).faltantesDatos().get(0).datosFaltantes())
                .containsExactly("Fotografía", "Cédula", "Número de lote", "Observado");
    }

    @Test
    void elPdfNominalIncluyeConstanciaDeRecepcionYFaltantes() throws IOException {
        InformeNominalImpresionCentral informe = new InformeNominalImpresionCentral(
                13L, "13 DE JUNIO", "FEDERA", 1, 1,
                List.of(new InformeNominalImpresionCentral.SeccionSindicato(
                        2L, "NUEVA ESPERANZA",
                        List.of(new InformeNominalImpresionCentral.Fila(
                                8L, "MARÍA", "PÉREZ", "123", "22 A", "213J8", 2,
                                LocalDateTime.of(2026, 8, 28, 10, 30), List.of())),
                        List.of(new InformeNominalImpresionCentral.Fila(
                                9L, "JUAN", "MAMANI", "", "23", "213J9", 0,
                                null, List.of("Fotografía", "Cédula", "Número de lote",
                                        "Observado"))))));

        byte[] pdf = new InformeNominalImpresionCentralPdf().generar(informe);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "informe-nominal-muestra.pdf"), pdf);
        PdfReader lector = new PdfReader(pdf);
        try {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1)
                    .replaceAll("\\s+", " ");
            assertThat(texto).contains(
                    "INFORME NOMINAL DE IMPRESIÓN DE CARNET DE PRODUCTOR",
                    "MARÍA", "JUAN",
                    "Fotografía, Cédula, Número de lote, Observado",
                    "CONSTANCIA DE RECEPCIÓN",
                    "Nombre de quien recibe", "Firma", "Entregado por", "Página 1 de 1");
            assertThat(texto).doesNotContain("IMPRESIONES", "ÚLTIMA IMPRESIÓN");
            assertThat(lector.getPageSize(1).getHeight())
                    .isGreaterThan(lector.getPageSize(1).getWidth());
        } finally {
            lector.close();
        }
    }

    @Test
    void elPdfNominalRepiteSindicatoYNumeraLasPaginasPorSindicato()
            throws IOException {
        List<InformeNominalImpresionCentral.Fila> muchasFilas = IntStream.rangeClosed(1, 95)
                .mapToObj(numero -> new InformeNominalImpresionCentral.Fila(
                        (long) numero, "NOMBRE " + numero, "APELLIDO " + numero,
                        String.valueOf(1000000 + numero), "22 A", "213J" + numero,
                        1, null, List.of()))
                .toList();
        InformeNominalImpresionCentral.SeccionSindicato primera =
                new InformeNominalImpresionCentral.SeccionSindicato(
                        1L, "1RO DE MAYO", muchasFilas, List.of());
        InformeNominalImpresionCentral.SeccionSindicato segunda =
                new InformeNominalImpresionCentral.SeccionSindicato(
                        2L, "NUEVA ESPERANZA", muchasFilas.subList(0, 8), List.of());
        InformeNominalImpresionCentral informe = new InformeNominalImpresionCentral(
                13L, "13 DE JUNIO", "FEDERA", 103, 0, List.of(primera, segunda));

        byte[] pdf = new InformeNominalImpresionCentralPdf().generar(informe);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "informe-nominal-largo-muestra.pdf"), pdf);
        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages()).isGreaterThan(2);
            int iniciosDeSindicato = 0;
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                String texto = new PdfTextExtractor(lector).getTextFromPage(pagina)
                        .replaceAll("\\s+", " ");
                assertThat(texto).contains("SINDICATO:");
                if (texto.contains("Página 1 de")) iniciosDeSindicato++;
            }
            assertThat(iniciosDeSindicato).isEqualTo(2);
        } finally {
            lector.close();
        }
    }

    private static CredencialService.FilaInformeImpresion filaNominal(
            long id, String nombres, String apellidos, List<String> faltantes,
            int impresiones) {
        return new CredencialService.FilaInformeImpresion(
                id, nombres, apellidos, "123", "22", "213J" + id,
                impresiones, null, faltantes);
    }

    private static CredencialService.PanelImpresionSindicato panel(
            long id, String nombre, int total, int impresos,
            int conFoto, int sinFoto, int listos) {
        return new CredencialService.PanelImpresionSindicato(
                id, nombre, total, impresos, conFoto, sinFoto, listos,
                List.of(), List.of());
    }

    private static List<Productor> productoresParaInforme(
            Sindicato primero, Sindicato segundo) {
        List<Productor> resultado = new ArrayList<>();
        for (int indice = 0; indice < 16; indice++) {
            boolean delPrimero = indice < 10;
            boolean conSistema = indice < 6 || (indice >= 10 && indice < 12);
            boolean observado = indice == 0 || indice == 10 || indice == 11;
            resultado.add(Productor.builder()
                    .id((long) indice + 1)
                    .nombres("PRODUCTOR " + indice)
                    .sindicato(delPrimero ? primero : segundo)
                    .clasificacionPendiente(conSistema
                            ? EstadoLote.CON_SISTEMA : EstadoLote.BLANCO)
                    .observacionManual(observado ? "Revisar documentación" : null)
                    .build());
        }
        // El primer productor demuestra que una parcela vigente prevalece
        // sobre la clasificación pendiente.
        resultado.get(0).setClasificacionPendiente(EstadoLote.BLANCO);
        return resultado;
    }

    private static FaseImpresionCarnet fase(Long id, Central central, int numero) {
        FaseImpresionCarnet fase = new FaseImpresionCarnet(
                central, numero, LocalDateTime.of(2026, 9, 11, 8, 0));
        fase.setId(id);
        return fase;
    }

    private static ProductorFaseImpresion participante(
            FaseImpresionCarnet fase, Productor productor, boolean reimpresion) {
        return new ProductorFaseImpresion(
                fase, productor, LocalDateTime.of(2026, 9, 11, 8, 0),
                false, reimpresion, 1);
    }
}
