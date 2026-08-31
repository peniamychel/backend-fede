package com.federa.backend.service;

import com.federa.backend.dto.ConsultaPersonaResponse;
import com.federa.backend.integracion.sie.PersonaSieClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultaPersonaServiceTest {

    private final PersonaSieClient sie = mock(PersonaSieClient.class);
    private final ConsultaPersonaService servicio = new ConsultaPersonaService(sie);

    @Test
    void separaComplementoYNormalizaLosDatosEncontrados() {
        PersonaSieClient.PersonaSie persona = new PersonaSieClient.PersonaSie(
                "8005906", "1V", "Yáuri", "  Pucho ", " Inocéntes ");
        when(sie.buscar("8005906", "1V")).thenReturn(new PersonaSieClient.Resultado(
                PersonaSieClient.Estado.ENCONTRADA, persona, null));

        ConsultaPersonaResponse respuesta = servicio.consultar("8005906-1V");

        assertThat(respuesta.estado()).isEqualTo(ConsultaPersonaResponse.Estado.ENCONTRADA);
        assertThat(respuesta.nombres()).isEqualTo("INOCÉNTES");
        assertThat(respuesta.apellidos()).isEqualTo("YÁURI PUCHO");
        verify(sie).buscar("8005906", "1V");
    }

    @Test
    void conservaLaDiferenciaEntreNoEncontradaYNoDisponible() {
        when(sie.buscar("111", "")).thenReturn(new PersonaSieClient.Resultado(
                PersonaSieClient.Estado.NO_ENCONTRADA, null, "No encontrada"));
        when(sie.buscar("222", "")).thenReturn(new PersonaSieClient.Resultado(
                PersonaSieClient.Estado.NO_DISPONIBLE, null, "Sin conexión"));

        assertThat(servicio.consultar("111").estado())
                .isEqualTo(ConsultaPersonaResponse.Estado.NO_ENCONTRADA);
        assertThat(servicio.consultar("222").estado())
                .isEqualTo(ConsultaPersonaResponse.Estado.NO_DISPONIBLE);
    }
}
