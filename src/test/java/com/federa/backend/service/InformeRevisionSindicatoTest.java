package com.federa.backend.service;

import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.seguridad.AlcanceCentral;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class InformeRevisionSindicatoTest {

    private final SindicatoRepository sindicatos = mock(SindicatoRepository.class);
    private final CredencialService credenciales = mock(CredencialService.class);
    private final InformePreImpresionCentralService servicio = new InformePreImpresionCentralService(
            mock(CentralRepository.class), sindicatos, credenciales,
            mock(InformePreImpresionCentralPdf.class), new InformeRevisionPadronCentralPdf());

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void descargaSoloElSindicatoSolicitadoConElFormatoHabitual() throws Exception {
        Sindicato sindicato = sindicato();
        when(sindicatos.findById(7L)).thenReturn(Optional.of(sindicato));
        when(credenciales.estadoRevisionDatosSindicato(7L)).thenReturn(
                new CredencialService.EstadoRevisionDatosSindicato(7L, "1RO DE MAYO", List.of(
                        new CredencialService.FilaRevisionDatos(12L, "MARÍA", "PÉREZ", "123456",
                                "", null, true, "Revisar", List.of("Número de lote")))));

        var descarga = servicio.descargarRevisionPadronSindicatoPdf(7L);

        assertThat(descarga.nombreArchivo()).endsWith(".pdf");
        verify(sindicatos, never()).findByCentralIdOrderByNombreAsc(anyLong());
        PdfReader lector = new PdfReader(descarga.contenido());
        try {
            assertThat(lector.getNumberOfPages()).isEqualTo(1);
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertThat(texto).contains("SINDICATO: 1RO DE MAYO", "MARÍA", "PÉREZ",
                    "* Obs = Productor observado revisión general", "N° lote, Obs");
        } finally {
            lector.close();
        }
        Path muestras = Path.of("target", "pdf-qa");
        Files.createDirectories(muestras);
        Files.write(muestras.resolve("revision-padron-sindicato-muestra.pdf"),
                descarga.contenido());
    }

    @Test
    void rechazaSindicatoFueraDelAlcanceDeLaCuenta() {
        when(sindicatos.findById(7L)).thenReturn(Optional.of(sindicato()));
        var autenticacion = new UsernamePasswordAuthenticationToken("operador", null, List.of());
        autenticacion.setDetails(new AlcanceCentral.Datos(3L, 1L, false, Set.of(8L)));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);

        assertThatThrownBy(() -> servicio.descargarRevisionPadronSindicatoPdf(7L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(credenciales);
    }

    @Test
    void preImpresionDescargaSoloElSindicatoSolicitado() throws Exception {
        Sindicato sindicato = sindicato();
        when(sindicatos.findById(7L)).thenReturn(Optional.of(sindicato));
        when(credenciales.estadoRevisionDatosSindicato(7L)).thenReturn(
                new CredencialService.EstadoRevisionDatosSindicato(7L, "1RO DE MAYO", List.of(
                        new CredencialService.FilaRevisionDatos(12L, "MARÍA", "PÉREZ", "123456",
                                "", null, false, "", List.of("Número de lote")))));
        var generador = new InformePreImpresionCentralService(
                mock(CentralRepository.class), sindicatos, credenciales,
                new InformePreImpresionCentralPdf(), mock(InformeRevisionPadronCentralPdf.class));

        var descarga = generador.descargarPreImpresionSindicatoPdf(7L);

        assertThat(descarga.nombreArchivo()).contains("pre-impresion").endsWith(".pdf");
        verify(sindicatos, never()).findByCentralIdOrderByNombreAsc(anyLong());
        PdfReader lector = new PdfReader(descarga.contenido());
        try {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertThat(texto).contains("SINDICATO: 1RO DE MAYO", "MARÍA", "N° lote",
                    "CONSTANCIA DE ENTREGA DE INFORME");
        } finally { lector.close(); }
        Path muestras = Path.of("target", "pdf-qa");
        Files.createDirectories(muestras);
        Files.write(muestras.resolve("pre-impresion-sindicato-muestra.pdf"),
                descarga.contenido());
    }

    private Sindicato sindicato() {
        Federacion federacion = Federacion.builder().id(1L).nombre("CARRASCO TROPICAL").build();
        Central central = Central.builder().id(3L).nombre("13 DE JUNIO")
                .federacion(federacion).build();
        return Sindicato.builder().id(7L).nombre("1RO DE MAYO").central(central).build();
    }
}
