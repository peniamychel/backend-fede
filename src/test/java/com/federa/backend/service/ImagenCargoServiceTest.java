package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.dto.CargoResponse;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.Central;
import com.federa.backend.model.Productor;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.ImagenCargoRepository;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImagenCargoServiceTest {

    private final ImagenCargoRepository imagenes = mock(ImagenCargoRepository.class);
    private final CargoRepository cargos = mock(CargoRepository.class);
    private final ProcesadorImagenes procesador = mock(ProcesadorImagenes.class);
    private final AlmacenObjetos almacen = mock(AlmacenObjetos.class);
    private final ReglasDirectorio reglas = new ReglasDirectorio(false, false);
    private final ImagenCargoService servicio = new ImagenCargoService(
            imagenes, cargos, procesador, almacen, reglas);

    @Test
    void centralPuedeCargarPieDeFirmaComoPng() {
        Cargo cargo = cargoCentral();
        byte[] subido = {1, 2};
        byte[] png = {3, 4, 5};
        BufferedImage origen = new BufferedImage(80, 30, BufferedImage.TYPE_INT_ARGB);
        when(cargos.findById(7L)).thenReturn(Optional.of(cargo));
        when(procesador.leer(subido)).thenReturn(origen);
        when(procesador.generarPng(eq(origen), anyInt(), anyInt()))
                .thenReturn(new ProcesadorImagenes.Variante(png, 80, 30, "image/png"));

        CargoResponse respuesta = servicio.guardar(
                7L, TipoImagenCargo.PIE_FIRMA, subido, "pie.jpg");

        assertThat(respuesta.pieFirmaUrl()).startsWith("/api/v1/archivos/pies-firma/");
        assertThat(respuesta.firmaUrl()).isNull();
        verify(almacen).guardar(cargo.getImagenes().get(0).getClave(), png);
    }

    @Test
    void sindicatoRechazaPieDeFirmaMientrasLaOpcionEstaDeshabilitada() {
        Cargo cargo = mock(Cargo.class);
        when(cargo.getAmbito()).thenReturn(com.federa.backend.model.enums.Ambito.SINDICATO);
        when(cargo.getCargo()).thenReturn(TipoCargo.SECRETARIO_GENERAL);
        when(cargos.findById(8L)).thenReturn(Optional.of(cargo));

        assertThatThrownBy(() -> servicio.guardar(
                8L, TipoImagenCargo.PIE_FIRMA, new byte[]{1}, "pie.png"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("deshabilitado para sindicatos");
    }

    private Cargo cargoCentral() {
        Central central = new Central();
        central.setId(3L);
        central.setNombre("IVIRGARZAMA");
        Productor productor = new Productor();
        productor.setNombres("ANA");
        productor.setApellidos("QUISPE");
        Cargo cargo = new Cargo();
        cargo.setId(7L);
        cargo.setCargo(TipoCargo.SECRETARIO_GENERAL);
        cargo.setProductor(productor);
        cargo.colgarDe(null, central, null);
        cargo.iniciar(LocalDate.of(2026, 8, 18));
        return cargo;
    }
}
