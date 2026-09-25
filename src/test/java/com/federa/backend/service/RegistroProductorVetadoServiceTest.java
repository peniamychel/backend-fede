package com.federa.backend.service;

import com.federa.backend.dto.ProductorRequest;
import com.federa.backend.dto.ProductorResponse;
import com.federa.backend.dto.RegistrarProductorVetadoRequest;
import com.federa.backend.dto.VetoRequest;
import com.federa.backend.dto.VetoResponse;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Central;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.ProductorRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistroProductorVetadoServiceTest {
    private final ProductorService productores = mock(ProductorService.class);
    private final ProductorRepository repositorio = mock(ProductorRepository.class);
    private final VetoService vetos = mock(VetoService.class);
    private final SindicatoService sindicatos = mock(SindicatoService.class);
    private final RegistroProductorVetadoService servicio =
            new RegistroProductorVetadoService(productores, repositorio, vetos, sindicatos);

    private void sindicatoExistente() {
        when(sindicatos.buscar(7L)).thenReturn(Sindicato.builder()
                .id(7L).central(Central.builder().id(3L).build()).build());
    }

    @Test
    void registraProductorPendienteDeSieYLoVeta() {
        sindicatoExistente();
        var peticion = new RegistrarProductorVetadoRequest(
                7L, " 123456 ", "MARIA", "PEREZ", "Decisión del sindicato");
        var creado = mock(ProductorResponse.class);
        when(creado.id()).thenReturn(8L);
        when(productores.crear(any(ProductorRequest.class))).thenReturn(creado);
        var productor = new Productor();
        when(repositorio.findById(8L)).thenReturn(Optional.of(productor));
        var veto = mock(VetoResponse.class);
        when(vetos.vetar(any(VetoRequest.class))).thenReturn(veto);

        assertThat(servicio.registrar(peticion)).isSameAs(veto);
        assertThat(productor.isRevisionSiePendiente()).isTrue();
        verify(repositorio).existeCedulaNormalizada("123456");
        verify(productores).crear(argThat(r -> r.sindicatoId().equals(7L)
                && r.ci().equals("123456") && r.nombres().equals("MARIA")));
        verify(vetos).vetar(argThat(r -> r.productorId().equals(8L)
                && r.motivo().equals("Decisión del sindicato")));
    }

    @Test
    void rechazaCedulaExistenteSinRegistrarNiVetar() {
        sindicatoExistente();
        when(repositorio.existeCedulaNormalizada("123456")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar(new RegistrarProductorVetadoRequest(
                7L, "123456", "MARIA", "PEREZ", "Motivo")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya pertenece a un productor");
        verifyNoInteractions(productores, vetos);
    }
}
