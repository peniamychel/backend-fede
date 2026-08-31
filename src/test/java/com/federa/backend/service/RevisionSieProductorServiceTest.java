package com.federa.backend.service;

import com.federa.backend.dto.ConsultaPersonaResponse;
import com.federa.backend.dto.RevisionSieProductorResponse;
import com.federa.backend.model.Productor;
import com.federa.backend.repository.ProductorRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RevisionSieProductorServiceTest {

    private final ProductorRepository productores = mock(ProductorRepository.class);
    private final ConsultaPersonaService consulta = mock(ConsultaPersonaService.class);
    private final RevisionSieProductorService servicio =
            new RevisionSieProductorService(productores, consulta);

    @Test
    void corrigeUnaSolaVezCuandoSieEncuentraDiferencias() {
        Productor productor = pendiente("JULIO", "PENA", "123");
        when(productores.findByIdParaRevisionSie(8L)).thenReturn(Optional.of(productor));
        when(consulta.consultar("123")).thenReturn(new ConsultaPersonaResponse(
                ConsultaPersonaResponse.Estado.ENCONTRADA,
                "JÚLIO", "PEÑA MUÑOZ", "Encontrada"));

        RevisionSieProductorResponse primera = servicio.revisar(8L);
        RevisionSieProductorResponse segunda = servicio.revisar(8L);

        assertThat(primera.estado()).isEqualTo(RevisionSieProductorResponse.Estado.CORREGIDA);
        assertThat(primera.datosModificados()).isTrue();
        assertThat(productor.getNombres()).isEqualTo("JÚLIO");
        assertThat(productor.getApellidos()).isEqualTo("PEÑA MUÑOZ");
        assertThat(productor.isRevisionSiePendiente()).isFalse();
        assertThat(segunda.estado()).isEqualTo(
                RevisionSieProductorResponse.Estado.YA_REALIZADA);
        verify(consulta, times(1)).consultar("123");
    }

    @Test
    void aceptaLosDatosImportadosSiSieNoEncuentraLaCedula() {
        Productor productor = pendiente("ANA", "ROJAS", "456");
        when(productores.findByIdParaRevisionSie(9L)).thenReturn(Optional.of(productor));
        when(consulta.consultar("456")).thenReturn(new ConsultaPersonaResponse(
                ConsultaPersonaResponse.Estado.NO_ENCONTRADA, null, null, "No encontrada"));

        RevisionSieProductorResponse resultado = servicio.revisar(9L);

        assertThat(resultado.estado()).isEqualTo(
                RevisionSieProductorResponse.Estado.ACEPTADA_SIN_COINCIDENCIA);
        assertThat(resultado.completada()).isTrue();
        assertThat(productor.isRevisionSiePendiente()).isFalse();
    }

    @Test
    void dejaPendienteCuandoSieEstaTemporalmenteNoDisponible() {
        Productor productor = pendiente("ANA", "ROJAS", "789");
        when(productores.findByIdParaRevisionSie(10L)).thenReturn(Optional.of(productor));
        when(consulta.consultar("789")).thenReturn(new ConsultaPersonaResponse(
                ConsultaPersonaResponse.Estado.NO_DISPONIBLE, null, null, "Sin conexión"));

        RevisionSieProductorResponse resultado = servicio.revisar(10L);

        assertThat(resultado.estado()).isEqualTo(
                RevisionSieProductorResponse.Estado.NO_DISPONIBLE);
        assertThat(resultado.completada()).isFalse();
        assertThat(productor.isRevisionSiePendiente()).isTrue();
    }

    @Test
    void sinCedulaAceptaLaImportacionSinConsultarSie() {
        Productor productor = pendiente("ANA", "ROJAS", null);
        when(productores.findByIdParaRevisionSie(11L)).thenReturn(Optional.of(productor));

        RevisionSieProductorResponse resultado = servicio.revisar(11L);

        assertThat(resultado.estado()).isEqualTo(
                RevisionSieProductorResponse.Estado.ACEPTADA_SIN_CEDULA);
        assertThat(productor.isRevisionSiePendiente()).isFalse();
        verifyNoInteractions(consulta);
    }

    @Test
    void laVerificacionManualConsultaAunqueLaRevisionAutomaticaYaTermino() {
        Productor productor = pendiente("MARIA", "NUNEZ", "321");
        productor.setRevisionSiePendiente(false);
        when(productores.findByIdParaRevisionSie(12L)).thenReturn(Optional.of(productor));
        when(consulta.consultar("321")).thenReturn(new ConsultaPersonaResponse(
                ConsultaPersonaResponse.Estado.ENCONTRADA,
                "MARÍA", "NÚÑEZ", "Encontrada"));

        RevisionSieProductorResponse resultado = servicio.verificarManualmente(12L);

        assertThat(resultado.estado()).isEqualTo(RevisionSieProductorResponse.Estado.CORREGIDA);
        assertThat(resultado.datosModificados()).isTrue();
        assertThat(productor.getNombres()).isEqualTo("MARÍA");
        assertThat(productor.getApellidos()).isEqualTo("NÚÑEZ");
        verify(consulta).consultar("321");
    }

    private Productor pendiente(String nombres, String apellidos, String ci) {
        Productor productor = new Productor();
        productor.setNombres(nombres);
        productor.setApellidos(apellidos);
        productor.setCi(ci);
        productor.setRevisionSiePendiente(true);
        return productor;
    }
}
