package com.federa.backend.service;

import com.federa.backend.dto.ConsultaPersonaResponse;
import com.federa.backend.dto.ConfirmacionSieRequest;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.dto.RevisionSieProductorResponse;
import com.federa.backend.model.Productor;
import com.federa.backend.model.enums.EstadoRevisionSieProductor;
import com.federa.backend.repository.ProductorRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RevisionSieProductorServiceTest {

    private final ProductorRepository productores = mock(ProductorRepository.class);
    private final ConsultaPersonaService consulta = mock(ConsultaPersonaService.class);
    private final RevisionSieProductorService servicio =
            new RevisionSieProductorService(productores, consulta);

    @Test
    void soloCorrigeDespuesDeAceptarLaPropuesta() {
        Productor productor = pendiente("JULIO", "PENA", "123");
        when(productores.findByIdParaRevisionSie(8L)).thenReturn(Optional.of(productor));
        when(consulta.consultar("123")).thenReturn(new ConsultaPersonaResponse(
                ConsultaPersonaResponse.Estado.ENCONTRADA,
                "JÚLIO", "PEÑA MUÑOZ", "Encontrada"));

        RevisionSieProductorResponse primera = servicio.revisar(8L);
        assertThat(primera.estado()).isEqualTo(
                RevisionSieProductorResponse.Estado.REQUIERE_CONFIRMACION);
        assertThat(primera.datosModificados()).isFalse();
        assertThat(primera.completada()).isFalse();
        assertThat(productor.getNombres()).isEqualTo("JULIO");
        assertThat(productor.getApellidos()).isEqualTo("PENA");
        assertThat(productor.isRevisionSiePendiente()).isFalse();
        assertThat(productor.getRevisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.DIFERENCIA_PENDIENTE);
        assertThat(productor.getSieNombresSugeridos()).isEqualTo("JÚLIO");
        var confirmada = servicio.confirmar(8L,
                new ConfirmacionSieRequest(true, primera.actuales(), primera.propuestos()));
        RevisionSieProductorResponse segunda = servicio.revisar(8L);

        assertThat(confirmada.estado()).isEqualTo(RevisionSieProductorResponse.Estado.CORREGIDA);
        assertThat(confirmada.datosModificados()).isTrue();
        assertThat(productor.getNombres()).isEqualTo("JÚLIO");
        assertThat(productor.getApellidos()).isEqualTo("PEÑA MUÑOZ");
        assertThat(productor.isRevisionSiePendiente()).isFalse();
        assertThat(productor.getRevisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.CORREGIDO_SIE);
        assertThat(productor.getSieNombresSugeridos()).isNull();
        assertThat(confirmada.mensaje()).contains("JULIO PENA", "JÚLIO PEÑA MUÑOZ");
        assertThat(segunda.estado()).isEqualTo(
                RevisionSieProductorResponse.Estado.CORREGIDA);
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
        assertThat(productor.getRevisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.NO_ENCONTRADO);
        assertThat(productor.isRevisionSieBloqueaImpresion()).isTrue();
    }

    @Test
    void permiteAprobarManualmenteCuandoSieNoEncuentraSinBorrarOtraObservacion() {
        Productor productor = pendiente("ANA", "ROJAS", "456");
        productor.setRevisionSiePendiente(false);
        productor.setRevisionSieEstado(EstadoRevisionSieProductor.NO_ENCONTRADO);
        productor.setRevisionSieMensaje("La cédula no fue encontrada en SIE.");
        productor.setObservacionManual("Revisar fotografía");
        when(productores.findByIdParaRevisionSie(9L)).thenReturn(Optional.of(productor));

        RevisionSieProductorResponse resultado = servicio.aprobarDatosActuales(9L);

        assertThat(resultado.estado())
                .isEqualTo(RevisionSieProductorResponse.Estado.APROBADA_MANUAL);
        assertThat(resultado.completada()).isTrue();
        assertThat(resultado.datosModificados()).isFalse();
        assertThat(productor.getRevisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.APROBADO_MANUAL);
        assertThat(productor.isRevisionSieBloqueaImpresion()).isFalse();
        assertThat(productor.isRevisionSiePendiente()).isFalse();
        assertThat(productor.getObservacionManual()).isEqualTo("Revisar fotografía");
        assertThat(productor.getRevisionSieMensaje()).contains("aprobados manualmente");
        verifyNoInteractions(consulta);
    }

    @Test
    void noApruebaManualmenteUnaCorreccionSiePendiente() {
        Productor productor = pendiente("ANA", "ROJAS", "456");
        productor.setRevisionSiePendiente(false);
        productor.setRevisionSieEstado(EstadoRevisionSieProductor.DIFERENCIA_PENDIENTE);
        when(productores.findByIdParaRevisionSie(9L)).thenReturn(Optional.of(productor));

        assertThatThrownBy(() -> servicio.aprobarDatosActuales(9L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("SIE no encontró");
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

        assertThat(resultado.estado()).isEqualTo(RevisionSieProductorResponse.Estado.REQUIERE_CONFIRMACION);
        assertThat(resultado.datosModificados()).isFalse();
        assertThat(productor.getNombres()).isEqualTo("MARIA");
        assertThat(productor.getApellidos()).isEqualTo("NUNEZ");
        verify(consulta).consultar("321");
    }

    @Test
    void rechazarConservaLaSugerenciaPendienteSinVolverAConsultarSie() {
        Productor productor = conPropuesta();
        productor.setNombresCorregidos("ANA MARIA");
        var propuesta = servicio.revisar(8L);
        assertThat(propuesta.actuales().nombres()).isEqualTo("ANA MARIA");
        var resultado = servicio.confirmar(8L,
                new ConfirmacionSieRequest(false, propuesta.actuales(), propuesta.propuestos()));
        assertThat(resultado.estado()).isEqualTo(RevisionSieProductorResponse.Estado.CONSERVADA);
        assertThat(productor.getNombres()).isEqualTo("ANA");
        assertThat(productor.getNombresCorregidos()).isEqualTo("ANA MARIA");
        assertThat(productor.isRevisionSiePendiente()).isFalse();
        assertThat(productor.getSieNombresSugeridos()).isEqualTo("ANA MARÍA");
        assertThat(productor.isRevisionSieBloqueaImpresion()).isTrue();
        assertThat(servicio.revisar(8L).estado()).isEqualTo(
                RevisionSieProductorResponse.Estado.REQUIERE_CONFIRMACION);
        assertThat(servicio.verificarManualmente(8L).propuestos().nombres())
                .isEqualTo("ANA MARÍA");
        verify(consulta, times(1)).consultar("123");
    }

    @Test
    void impideConfirmarSiLaCedulaCambioMientrasEstabaAbiertoElDialogo() {
        Productor productor = conPropuesta();
        var propuesta = servicio.revisar(8L);
        productor.setCi("OTRA");
        assertThatThrownBy(() -> servicio.confirmar(8L,
                new ConfirmacionSieRequest(true, propuesta.actuales(), propuesta.propuestos())))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(productor.getNombres()).isEqualTo("ANA");
        assertThat(productor.isRevisionSiePendiente()).isFalse();
        assertThat(productor.getRevisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.DIFERENCIA_PENDIENTE);
    }

    @Test
    void aceptarUsaLaSugerenciaGuardadaAunqueSieLuegoCambie() {
        Productor productor = conPropuesta();
        var propuesta = servicio.revisar(8L);
        when(consulta.consultar("123")).thenReturn(new ConsultaPersonaResponse(
                ConsultaPersonaResponse.Estado.ENCONTRADA, "OTRA", "PERSONA", "Encontrada"));
        var resultado = servicio.confirmar(8L,
                new ConfirmacionSieRequest(true, propuesta.actuales(), propuesta.propuestos()));
        assertThat(resultado.estado()).isEqualTo(RevisionSieProductorResponse.Estado.CORREGIDA);
        assertThat(productor.getNombres()).isEqualTo("ANA MARÍA");
        verify(consulta, times(1)).consultar("123");
    }

    @Test
    void aceptarLaSugerenciaGuardadaNoNecesitaConexionConSie() {
        Productor productor = conPropuesta();
        var propuesta = servicio.revisar(8L);
        when(consulta.consultar("123")).thenReturn(new ConsultaPersonaResponse(
                ConsultaPersonaResponse.Estado.NO_DISPONIBLE, null, null, "Sin conexión"));
        var resultado = servicio.confirmar(8L,
                new ConfirmacionSieRequest(true, propuesta.actuales(), propuesta.propuestos()));
        assertThat(resultado.completada()).isTrue();
        assertThat(productor.getNombres()).isEqualTo("ANA MARÍA");
        assertThat(productor.getRevisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.CORREGIDO_SIE);
        verify(consulta, times(1)).consultar("123");
    }

    @Test
    void siLosDatosCoincidenCompletaSinPedirConfirmacion() {
        Productor productor = conPropuesta();
        productor.setNombres("ANA MARÍA");
        assertThat(servicio.revisar(8L).estado()).isEqualTo(
                RevisionSieProductorResponse.Estado.VERIFICADA);
        assertThat(productor.isRevisionSiePendiente()).isFalse();
        assertThat(productor.getRevisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.VERIFICADO);
    }

    private Productor conPropuesta() {
        Productor productor = pendiente("ANA", "ROJAS", "123");
        when(productores.findByIdParaRevisionSie(8L)).thenReturn(Optional.of(productor));
        when(consulta.consultar("123")).thenReturn(new ConsultaPersonaResponse(
                ConsultaPersonaResponse.Estado.ENCONTRADA, "ANA MARÍA", "ROJAS", "Encontrada"));
        return productor;
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
