package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.ImagenCargoRepository;
import com.federa.backend.repository.ImagenProductorRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.TenenciaLoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservacionProductorServiceTest {

    private ProductorRepository repositorio;
    private ProductorService servicio;
    private Productor productor;

    @BeforeEach
    void preparar() {
        repositorio = mock(ProductorRepository.class);
        servicio = new ProductorService(repositorio,
                mock(TenenciaLoteRepository.class),
                mock(ImagenProductorRepository.class),
                mock(ImagenCargoRepository.class),
                mock(SindicatoService.class), mock(NumeradorPadron.class),
                mock(AlmacenObjetos.class));
        Federacion federacion = Federacion.builder().numero("2").build();
        Central central = Central.builder().id(3L).nombre("13 DE JUNIO")
                .abreviatura("13J").federacion(federacion).build();
        Sindicato sindicato = Sindicato.builder().id(7L).nombre("1RO DE MAYO")
                .central(central).build();
        productor = Productor.builder().id(8L).nombres("MARÍA").apellidos("PÉREZ")
                .ci("1234567").correlativo(10).sindicato(sindicato).build();
        productor.setEstado(true);
        when(repositorio.findById(8L)).thenReturn(Optional.of(productor));
    }

    @Test
    void guardaElTextoYLoPuedeQuitar() {
        var observado = servicio.observar(8L, "  Revisar   fotografía  ");

        assertThat(observado.observado()).isTrue();
        assertThat(observado.observacion()).isEqualTo("Revisar fotografía");
        assertThat(productor.getObservacionManual()).isEqualTo("Revisar fotografía");

        var habilitado = servicio.quitarObservacion(8L);

        assertThat(habilitado.observado()).isFalse();
        assertThat(habilitado.observacion()).isNull();
        verify(repositorio, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void noPermiteObservarSinExplicarElMotivo() {
        assertThatThrownBy(() -> servicio.observar(8L, "   "))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("motivo");
        assertThat(productor.isObservado()).isFalse();
    }
}
