package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.dto.ProductorRequest;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.EstadoRevisionSieProductor;
import com.federa.backend.repository.ImagenCargoRepository;
import com.federa.backend.repository.ImagenProductorRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.TenenciaLoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RevisionSieEdicionManualTest {

    private ProductorService servicio;
    private Productor productor;

    @BeforeEach
    void preparar() {
        Federacion federacion = Federacion.builder().numero("2").build();
        Central central = Central.builder().id(3L).nombre("13 DE JUNIO")
                .abreviatura("13J").federacion(federacion).build();
        Sindicato sindicato = Sindicato.builder().id(7L).nombre("1RO DE MAYO")
                .central(central).build();
        productor = Productor.builder().id(8L).nombres("MARIA").apellidos("PEREZ")
                .ci("1234567").correlativo(10).sindicato(sindicato)
                .revisionSieEstado(EstadoRevisionSieProductor.DIFERENCIA_PENDIENTE)
                .revisionSieMensaje("Hay diferencias")
                .sieNombresSugeridos("MARÍA").sieApellidosSugeridos("PÉREZ").build();
        productor.setEstado(true);
        ProductorRepository repositorio = mock(ProductorRepository.class);
        when(repositorio.findById(8L)).thenReturn(Optional.of(productor));
        SindicatoService sindicatos = mock(SindicatoService.class);
        when(sindicatos.buscar(7L)).thenReturn(sindicato);
        servicio = new ProductorService(repositorio,
                mock(TenenciaLoteRepository.class), mock(ImagenProductorRepository.class),
                mock(ImagenCargoRepository.class), sindicatos, mock(NumeradorPadron.class),
                mock(AlmacenObjetos.class));
    }

    @Test
    void cambiarLaIdentidadResuelveLaRevisionComoCorreccionManual() {
        var respuesta = servicio.actualizar(8L,
                new ProductorRequest("MARÍA", "PÉREZ", "1234567",
                        null, null, null, false, 7L));

        assertThat(respuesta.revisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.CORREGIDO_MANUAL);
        assertThat(respuesta.revisionSieBloqueaImpresion()).isFalse();
        assertThat(productor.getSieNombresSugeridos()).isNull();
        assertThat(respuesta.revisionSieMensaje())
                .contains("MARIA PEREZ", "MARÍA PÉREZ", "CI 1234567");
    }

    @Test
    void editarOtroDatoNoDescartaLaSugerenciaDeSie() {
        var respuesta = servicio.actualizar(8L,
                new ProductorRequest("MARIA", "PEREZ", "1234567",
                        null, null, "FOTO NUEVA", false, 7L));

        assertThat(respuesta.revisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.DIFERENCIA_PENDIENTE);
        assertThat(respuesta.revisionSieBloqueaImpresion()).isTrue();
        assertThat(productor.getSieNombresSugeridos()).isEqualTo("MARÍA");
    }
}
