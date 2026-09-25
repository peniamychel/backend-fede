package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.AsistenciaRepository;
import com.federa.backend.repository.DetalleGrupoImpresionCredencialRepository;
import com.federa.backend.repository.ImagenCargoRepository;
import com.federa.backend.repository.ImagenProductorRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.TenenciaLoteRepository;
import com.federa.backend.repository.VetoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PapeleraProductorServiceTest {
    private final ProductorRepository productores = mock(ProductorRepository.class);
    private final TenenciaLoteRepository tenencias = mock(TenenciaLoteRepository.class);
    private final VetoRepository vetos = mock(VetoRepository.class);
    private final CargoRepository cargos = mock(CargoRepository.class);
    private final AsistenciaRepository asistencias = mock(AsistenciaRepository.class);
    private final DetalleGrupoImpresionCredencialRepository detalles = mock(DetalleGrupoImpresionCredencialRepository.class);
    private final ImagenProductorRepository imagenes = mock(ImagenProductorRepository.class);
    private final AlmacenObjetos almacen = mock(AlmacenObjetos.class);
    private final ProductorService servicio = new ProductorService(productores, tenencias,
            imagenes, mock(ImagenCargoRepository.class),
            mock(SindicatoService.class), mock(NumeradorPadron.class),
            almacen, vetos, cargos, asistencias, detalles);

    private Productor productor() {
        var federacion = Federacion.builder().numero("2").build();
        var central = Central.builder().id(3L).nombre("13 DE JUNIO")
                .abreviatura("13J").federacion(federacion).build();
        var sindicato = Sindicato.builder().id(7L).nombre("1RO DE MAYO")
                .central(central).build();
        return Productor.builder().id(8L).nombres("MARIA").apellidos("PEREZ")
                .ci("123456").sindicato(sindicato).correlativo(10).build();
    }

    @Test
    void eliminarMueveAPapeleraSinBorrarLaFicha() {
        var productor = productor();
        when(productores.findById(8L)).thenReturn(Optional.of(productor));
        when(vetos.findByProductorIdAndVigenteIsTrue(8L)).thenReturn(Optional.empty());
        when(cargos.findByProductorIdAndVigenteIsTrue(8L)).thenReturn(Optional.empty());

        servicio.eliminar(8L);

        assertThat(productor.getEliminadoEn()).isNotNull();
        verify(productores).flush();
        verify(productores, never()).delete(any(Productor.class));
    }

    @Test
    void noRestauraSiCedulaYaSeRegistroDeNuevo() {
        var productor = productor();
        productor.setEliminadoEn(LocalDateTime.now());
        when(productores.findEnPapeleraPorId(8L)).thenReturn(Optional.of(productor));
        when(productores.existeOtraCedulaActiva("123456", 8L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.restaurar(8L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("cédula ya fue registrada");
        assertThat(productor.getEliminadoEn()).isNotNull();
        verify(productores, never()).flush();
    }

    @Test
    void restauraSiCedulaLibreConservandoLaFicha() {
        var productor = productor();
        productor.setEliminadoEn(LocalDateTime.now());
        when(productores.findEnPapeleraPorId(8L)).thenReturn(Optional.of(productor));

        var respuesta = servicio.restaurar(8L);

        assertThat(productor.getEliminadoEn()).isNull();
        assertThat(respuesta.id()).isEqualTo(8L);
        verify(productores).flush();
    }

    @Test
    void eliminaDefinitivamenteSoloDesdePapelera() {
        var productor = productor();
        productor.setEliminadoEn(LocalDateTime.now());
        when(productores.findEnPapeleraPorId(8L)).thenReturn(Optional.of(productor));
        when(imagenes.findClavesPorProductor(8L)).thenReturn(java.util.List.of("foto-8"));

        servicio.eliminarDefinitivamente(8L);

        var orden = inOrder(asistencias, detalles, productores, almacen);
        orden.verify(asistencias).eliminarPorProductor(8L);
        orden.verify(detalles).eliminarPorProductor(8L);
        orden.verify(productores).delete(productor);
        orden.verify(productores).flush();
        orden.verify(almacen).borrar("foto-8");
    }

    @Test
    void noBorraDefinitivamenteUnProductorActivo() {
        assertThatThrownBy(() -> servicio.eliminarDefinitivamente(8L))
                .isInstanceOf(com.federa.backend.exception.RecursoNoEncontradoException.class);
        verify(productores, never()).delete(any(Productor.class));
    }
}
