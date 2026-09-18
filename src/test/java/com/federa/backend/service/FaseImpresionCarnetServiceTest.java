package com.federa.backend.service;

import com.federa.backend.dto.EstadoFasesImpresionCentral;
import com.federa.backend.model.Central;
import com.federa.backend.model.FaseImpresionCarnet;
import com.federa.backend.model.Productor;
import com.federa.backend.model.ProductorFaseImpresion;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.EstadoFaseImpresionCarnet;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.FaseImpresionCarnetRepository;
import com.federa.backend.repository.ProductorFaseImpresionRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.SindicatoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaseImpresionCarnetServiceTest {

    @Mock CentralRepository centralRepository;
    @Mock SindicatoRepository sindicatoRepository;
    @Mock ProductorRepository productorRepository;
    @Mock FaseImpresionCarnetRepository faseRepository;
    @Mock ProductorFaseImpresionRepository participanteRepository;

    private FaseImpresionCarnetService servicio;

    @BeforeEach
    void preparar() {
        servicio = new FaseImpresionCarnetService(
                centralRepository, sindicatoRepository, productorRepository,
                faseRepository, participanteRepository);
    }

    @Test
    void reservaReimpresionSinFaseActivaSinModificarHistorial() {
        Central central = Central.builder().id(5L).build();
        Sindicato sindicato = Sindicato.builder().id(8L).central(central).build();
        Productor productor = Productor.builder().id(1L).sindicato(sindicato)
                .credencialImpresiones(2).build();
        when(productorRepository.findById(1L)).thenReturn(Optional.of(productor));
        when(faseRepository.findFirstByCentralIdAndEstadoOrderByNumeroDesc(
                5L, EstadoFaseImpresionCarnet.ABIERTA)).thenReturn(Optional.empty());

        servicio.agregarParaReimpresion(1L);

        assertThat(productor.isFaseImpresionPendiente()).isTrue();
        assertThat(productor.isReimpresionFasePendiente()).isTrue();
        assertThat(productor.getCredencialImpresiones()).isEqualTo(2);
        org.mockito.Mockito.verifyNoInteractions(participanteRepository);
    }

    @Test
    void cancelaReservaSinFaseYConservaConteo() {
        Central central = Central.builder().id(5L).build();
        Productor productor = Productor.builder().id(1L)
                .sindicato(Sindicato.builder().central(central).build())
                .credencialImpresiones(2).faseImpresionPendiente(true)
                .reimpresionFasePendiente(true).build();
        when(productorRepository.findById(1L)).thenReturn(Optional.of(productor));
        servicio.cancelarReimpresion(1L);
        assertThat(productor.isFaseImpresionPendiente()).isFalse();
        assertThat(productor.isReimpresionFasePendiente()).isFalse();
        assertThat(productor.getCredencialImpresiones()).isEqualTo(2);
        org.mockito.Mockito.verifyNoInteractions(participanteRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 2})
    void cancelaEnFaseAbiertaConservandoImpresionesReales(int impresiones) {
        Central central = Central.builder().id(5L).build();
        Productor productor = Productor.builder().id(1L)
                .sindicato(Sindicato.builder().central(central).build())
                .credencialImpresiones(3).faseImpresionPendiente(true)
                .reimpresionFasePendiente(true).build();
        FaseImpresionCarnet fase = new FaseImpresionCarnet();
        fase.setId(11L);
        ProductorFaseImpresion participante = new ProductorFaseImpresion(
                fase, productor, java.time.LocalDateTime.now(), true, true, impresiones);
        when(productorRepository.findById(1L)).thenReturn(Optional.of(productor));
        when(faseRepository.findFirstByCentralIdAndEstadoOrderByNumeroDesc(
                5L, EstadoFaseImpresionCarnet.ABIERTA)).thenReturn(Optional.of(fase));
        when(participanteRepository.findByFaseIdAndProductorId(11L, 1L))
                .thenReturn(Optional.of(participante));
        servicio.cancelarReimpresion(1L);
        if (impresiones == 0) {
            org.mockito.Mockito.verify(participanteRepository).delete(participante);
        } else {
            assertThat(participante.isPendiente()).isFalse();
            assertThat(participante.getImpresionesEnFase()).isEqualTo(impresiones);
            org.mockito.Mockito.verify(participanteRepository, org.mockito.Mockito.never())
                    .delete(any());
        }
        assertThat(productor.getCredencialImpresiones()).isEqualTo(3);
        assertThat(productor.isReimpresionFasePendiente()).isFalse();
    }

    @Test
    void primeraFaseIncorporaTodoElPadronYConservaElAvanceExistente() {
        Central central = Central.builder().id(5L).nombre("IVIRGARZAMA").build();
        Sindicato sindicato = Sindicato.builder().id(8L).nombre("LIBERTAD")
                .central(central).build();
        Productor impreso = Productor.builder().id(1L).nombres("MARÍA")
                .sindicato(sindicato).credencialImpresiones(2)
                .impresionesManualesSinFase(1).reimpresionManualSinFase(true).build();
        Productor pendiente = Productor.builder().id(2L).nombres("JUAN")
                .sindicato(sindicato).credencialImpresiones(0).build();
        List<ProductorFaseImpresion> guardados = new ArrayList<>();
        FaseImpresionCarnet[] faseCreada = new FaseImpresionCarnet[1];

        when(centralRepository.findByIdParaNumerar(5L)).thenReturn(Optional.of(central));
        when(faseRepository.findFirstByCentralIdAndEstadoOrderByNumeroDesc(
                5L, EstadoFaseImpresionCarnet.ABIERTA)).thenReturn(Optional.empty());
        when(faseRepository.findFirstByCentralIdOrderByNumeroDesc(5L))
                .thenReturn(Optional.empty());
        when(faseRepository.saveAndFlush(any())).thenAnswer(invocacion -> {
            FaseImpresionCarnet fase = invocacion.getArgument(0);
            fase.setId(11L);
            faseCreada[0] = fase;
            return fase;
        });
        when(productorRepository.findBySindicatoCentralIdOrderByApellidosAscNombresAsc(5L))
                .thenReturn(List.of(impreso, pendiente));
        when(participanteRepository.saveAll(anyList())).thenAnswer(invocacion -> {
            guardados.addAll(invocacion.getArgument(0));
            return invocacion.getArgument(0);
        });
        when(centralRepository.findById(5L)).thenReturn(Optional.of(central));
        when(faseRepository.findByCentralIdOrderByNumeroDesc(5L))
                .thenAnswer(invocacion -> List.of(faseCreada[0]));
        when(participanteRepository.findTodosDeFase(11L))
                .thenAnswer(invocacion -> guardados);

        EstadoFasesImpresionCentral estado = servicio.abrir(5L);

        assertThat(guardados.get(0).isReimpresion()).isTrue();
        assertThat(guardados.get(0).getImpresionesEnFase()).isEqualTo(1);
        assertThat(impreso.getImpresionesManualesSinFase()).isZero();
        assertThat(impreso.getCredencialImpresiones()).isEqualTo(2);

        assertThat(estado.faseActiva()).isNotNull();
        assertThat(estado.faseActiva().numero()).isEqualTo(1);
        assertThat(estado.faseActiva().total()).isEqualTo(2);
        assertThat(estado.faseActiva().impresos()).isEqualTo(1);
        assertThat(estado.faseActiva().pendientes()).isEqualTo(1);
        assertThat(central.getFaseImpresionActivaNumero()).isEqualTo(1);
        assertThat(pendiente.isFaseImpresionPendiente()).isTrue();
        assertThat(impreso.isFaseImpresionPendiente()).isFalse();
    }

    @Test
    void productorYaImpresoPuedeEntrarEnLaFaseActivaComoReimpresion() {
        Central central = Central.builder().id(5L).nombre("IVIRGARZAMA").build();
        Sindicato sindicato = Sindicato.builder().id(8L).nombre("LIBERTAD")
                .central(central).build();
        Productor productor = Productor.builder().id(1L).nombres("MARÍA")
                .sindicato(sindicato).credencialImpresiones(2).build();
        FaseImpresionCarnet fase = new FaseImpresionCarnet();
        fase.setId(11L);
        fase.setCentral(central);
        fase.setNumero(2);
        fase.setEstado(EstadoFaseImpresionCarnet.ABIERTA);

        when(productorRepository.findById(1L)).thenReturn(Optional.of(productor));
        when(faseRepository.findFirstByCentralIdAndEstadoOrderByNumeroDesc(
                5L, EstadoFaseImpresionCarnet.ABIERTA)).thenReturn(Optional.of(fase));
        when(participanteRepository.findByFaseIdAndProductorId(11L, 1L))
                .thenReturn(Optional.empty());
        when(participanteRepository.save(any())).thenAnswer(invocacion ->
                invocacion.getArgument(0));

        ProductorFaseImpresion participante = servicio.agregarParaReimpresion(1L);

        assertThat(participante.isPendiente()).isTrue();
        assertThat(participante.isReimpresion()).isTrue();
        assertThat(productor.isFaseImpresionPendiente()).isTrue();
        assertThat(productor.isReimpresionFasePendiente()).isTrue();
    }
}
