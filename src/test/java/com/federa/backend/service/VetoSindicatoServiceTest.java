package com.federa.backend.service;

import com.federa.backend.dto.VetoRequest;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.Veto;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.ReunionRepository;
import com.federa.backend.repository.VetoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VetoSindicatoServiceTest {

    @Test
    void permiteVetarDesdeElSindicatoSinReunion() {
        var productores = mock(ProductorRepository.class);
        var vetos = mock(VetoRepository.class);
        var cargos = mock(CargoRepository.class);
        var productor = productor();
        when(productores.findById(8L)).thenReturn(Optional.of(productor));
        when(vetos.findByProductorIdAndVigenteIsTrue(8L)).thenReturn(Optional.empty());
        when(vetos.saveAndFlush(any(Veto.class))).thenAnswer(invocacion -> {
            Veto veto = invocacion.getArgument(0);
            veto.setId(4L);
            return veto;
        });

        var servicio = new VetoService(vetos, productores,
                mock(ReunionRepository.class), cargos);
        var respuesta = servicio.vetar(new VetoRequest(
                8L, null, "Decisión del sindicato", null));

        assertThat(respuesta.vigente()).isTrue();
        assertThat(respuesta.reunion()).isNull();
        assertThat(respuesta.desde()).isEqualTo(LocalDate.now());
        verify(cargos).findByProductorIdAndVigenteIsTrue(8L);
    }

    private Productor productor() {
        var federacion = Federacion.builder().numero("2").build();
        var central = Central.builder().id(3L).nombre("13 DE JUNIO")
                .abreviatura("13J").federacion(federacion).build();
        var sindicato = Sindicato.builder().id(7L).nombre("1RO DE MAYO")
                .central(central).build();
        return Productor.builder().id(8L).nombres("MARIA").apellidos("PEREZ")
                .ci("1234567").correlativo(10).sindicato(sindicato).build();
    }
}
