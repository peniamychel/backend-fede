package com.federa.backend.service;

import com.federa.backend.model.Central;
import com.federa.backend.model.FaseImpresionCarnet;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Productor;
import com.federa.backend.model.ProductorFaseImpresion;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.FaseImpresionCarnetRepository;
import com.federa.backend.repository.ProductorFaseImpresionRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.seguridad.AlcanceCentral;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InformeFaseImpresionServiceTest {

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void incluyeTodosLosProductoresYMarcaSoloFasesConImpresionRegistrada() {
        var fases = mock(FaseImpresionCarnetRepository.class);
        var participantes = mock(ProductorFaseImpresionRepository.class);
        var sindicatos = mock(SindicatoRepository.class);
        var credenciales = mock(CredencialService.class);
        var pdf = mock(InformeFaseImpresionPdf.class);
        var servicio = new InformeFaseImpresionService(
                fases, participantes, sindicatos, credenciales, pdf);

        Central central = mock(Central.class);
        Federacion federacion = mock(Federacion.class);
        when(central.getId()).thenReturn(13L);
        when(central.getNombre()).thenReturn("13 DE JUNIO");
        when(central.getFederacion()).thenReturn(federacion);
        when(federacion.getNombre()).thenReturn("CARRASCO TROPICAL");
        FaseImpresionCarnet fase1 = fase(10L, 1, central);
        FaseImpresionCarnet fase2 = fase(11L, 2, central);
        when(fases.findById(11L)).thenReturn(Optional.of(fase2));

        Sindicato primero = sindicato(21L, "1RO DE MAYO");
        Sindicato segundo = sindicato(22L, "NUEVA ESPERANZA");
        when(sindicatos.findByCentralIdOrderByNombreAsc(13L))
                .thenReturn(List.of(primero, segundo));
        when(credenciales.estadoRevisionDatosSindicato(21L)).thenReturn(
                new CredencialService.EstadoRevisionDatosSindicato(21L, "1RO DE MAYO", List.of(
                        dato(1L, "MARÍA"), dato(2L, "JUAN"), dato(3L, "ANA"))));
        when(credenciales.estadoRevisionDatosSindicato(22L)).thenReturn(
                new CredencialService.EstadoRevisionDatosSindicato(22L, "NUEVA ESPERANZA",
                        List.of(dato(4L, "LUIS"))));
        var historial = List.of(
                participacion(1L, fase1, 1, false),
                participacion(2L, fase1, 1, false),
                participacion(1L, fase2, 1, true),
                participacion(3L, fase2, 0, false));
        when(participantes.findImpresionesHastaFase(13L, 2)).thenReturn(historial);

        var informe = servicio.obtener(13L, 11L);

        assertThat(informe.sindicatos()).hasSize(2);
        var primerSindicato = informe.sindicatos().get(0);
        assertThat(primerSindicato.productores()).hasSize(3);
        assertThat(primerSindicato.productores().get(0).fasesImpresas()).containsExactly(1, 2);
        assertThat(primerSindicato.productores().get(0).reimpreso()).isTrue();
        assertThat(primerSindicato.productores().get(1).fasesImpresas()).containsExactly(1);
        assertThat(primerSindicato.productores().get(2).fasesImpresas()).isEmpty();
        assertThat(primerSindicato.impresosEnFase()).isEqualTo(1);
        assertThat(primerSindicato.impresosAcumulados()).isEqualTo(2);
        assertThat(informe.sindicatos().get(1).productores()).hasSize(1);
        assertThat(informe.totalImpresos()).isEqualTo(1);
        assertThat(informe.totalPendientes()).isEqualTo(2);
        verify(participantes).findImpresionesHastaFase(13L, 2);
    }

    @Test
    void descargaLaFaseSoloParaElSindicatoSolicitado() throws Exception {
        var fases = mock(FaseImpresionCarnetRepository.class);
        var participantes = mock(ProductorFaseImpresionRepository.class);
        var sindicatos = mock(SindicatoRepository.class);
        var credenciales = mock(CredencialService.class);
        var servicio = new InformeFaseImpresionService(
                fases, participantes, sindicatos, credenciales,
                new InformeFaseImpresionPdf());
        Central central = mock(Central.class);
        Federacion federacion = mock(Federacion.class);
        when(central.getId()).thenReturn(13L);
        when(central.getNombre()).thenReturn("13 DE JUNIO");
        when(central.getFederacion()).thenReturn(federacion);
        when(federacion.getNombre()).thenReturn("CARRASCO TROPICAL");
        Sindicato sindicato = sindicato(21L, "1RO DE MAYO");
        when(sindicato.getCentral()).thenReturn(central);
        when(sindicatos.findById(21L)).thenReturn(Optional.of(sindicato));
        FaseImpresionCarnet fase = fase(11L, 2, central);
        when(fases.findById(11L)).thenReturn(Optional.of(fase));
        when(fases.findByCentralIdOrderByNumeroDesc(13L)).thenReturn(List.of(fase));
        when(participantes.findImpresionesHastaFaseSindicato(13L, 2, 21L))
                .thenReturn(List.of());
        when(credenciales.estadoRevisionDatosSindicato(21L)).thenReturn(
                new CredencialService.EstadoRevisionDatosSindicato(
                        21L, "1RO DE MAYO", List.of(dato(1L, "MARÍA"))));

        assertThat(servicio.fasesDisponiblesSindicato(21L))
                .extracting(InformeFaseImpresionService.FaseDisponible::numero)
                .containsExactly(2);
        var descarga = servicio.descargarSindicatoPdf(21L, 11L);

        assertThat(descarga.nombreArchivo()).contains("fase-2").endsWith(".pdf");
        PdfReader lector = new PdfReader(descarga.contenido());
        try {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertThat(texto).contains("FASE 2", "SINDICATO: 1RO DE MAYO", "MARÍA",
                    "CONSTANCIA DE RECEPCIÓN").doesNotContain("NUEVA ESPERANZA");
        } finally { lector.close(); }
        Path muestras = Path.of("target", "pdf-qa");
        Files.createDirectories(muestras);
        Files.write(muestras.resolve("informe-fase-sindicato-muestra.pdf"),
                descarga.contenido());
        verify(sindicatos, never()).findByCentralIdOrderByNombreAsc(13L);
        verify(participantes).findImpresionesHastaFaseSindicato(13L, 2, 21L);
        verify(participantes, never()).findImpresionesHastaFase(13L, 2);
    }

    @Test
    void noPermiteConsultarFasesDeOtroSindicato() {
        var fases = mock(FaseImpresionCarnetRepository.class);
        var participantes = mock(ProductorFaseImpresionRepository.class);
        var sindicatos = mock(SindicatoRepository.class);
        var credenciales = mock(CredencialService.class);
        var servicio = new InformeFaseImpresionService(
                fases, participantes, sindicatos, credenciales,
                mock(InformeFaseImpresionPdf.class));
        Central central = mock(Central.class);
        when(central.getId()).thenReturn(13L);
        Sindicato sindicato = sindicato(21L, "1RO DE MAYO");
        when(sindicato.getCentral()).thenReturn(central);
        when(sindicatos.findById(21L)).thenReturn(Optional.of(sindicato));
        var autenticacion = new UsernamePasswordAuthenticationToken(
                "operador", null, List.of());
        autenticacion.setDetails(new AlcanceCentral.Datos(13L, 1L, false, Set.of(22L)));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);

        assertThatThrownBy(() -> servicio.fasesDisponiblesSindicato(21L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.descargarSindicatoPdf(21L, 11L))
                .isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(fases, credenciales);
    }

    private FaseImpresionCarnet fase(Long id, int numero, Central central) {
        FaseImpresionCarnet fase = mock(FaseImpresionCarnet.class);
        when(fase.getId()).thenReturn(id);
        when(fase.getNumero()).thenReturn(numero);
        when(fase.getCentral()).thenReturn(central);
        when(fase.getAbiertaEn()).thenReturn(LocalDateTime.of(2026, 9, 11, 8, 0));
        return fase;
    }

    private Sindicato sindicato(Long id, String nombre) {
        Sindicato sindicato = mock(Sindicato.class);
        when(sindicato.getId()).thenReturn(id);
        when(sindicato.getNombre()).thenReturn(nombre);
        return sindicato;
    }

    private CredencialService.FilaRevisionDatos dato(Long id, String nombre) {
        return new CredencialService.FilaRevisionDatos(id, nombre, "PÉREZ", "123456", "22",
                "Sistema", false, "", List.of());
    }

    private ProductorFaseImpresion participacion(Long productorId, FaseImpresionCarnet fase,
                                                  int impresiones, boolean reimpresion) {
        Productor productor = mock(Productor.class);
        when(productor.getId()).thenReturn(productorId);
        ProductorFaseImpresion participante = mock(ProductorFaseImpresion.class);
        when(participante.getProductor()).thenReturn(productor);
        when(participante.getFase()).thenReturn(fase);
        when(participante.getImpresionesEnFase()).thenReturn(impresiones);
        when(participante.isReimpresion()).thenReturn(reimpresion);
        return participante;
    }
}
