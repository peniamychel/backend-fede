package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.dto.DirectorioResponse;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.FederacionRepository;
import com.federa.backend.repository.SindicatoRepository;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelloDirectorioServiceTest {

    private final SindicatoService sindicatos = mock(SindicatoService.class);
    private final CentralService centrales = mock(CentralService.class);
    private final FederacionService federaciones = mock(FederacionService.class);
    private final SindicatoRepository sindicatoRepository = mock(SindicatoRepository.class);
    private final CentralRepository centralRepository = mock(CentralRepository.class);
    private final FederacionRepository federacionRepository = mock(FederacionRepository.class);
    private final ProcesadorImagenes procesador = mock(ProcesadorImagenes.class);
    private final AlmacenObjetos almacen = mock(AlmacenObjetos.class);
    private final DirectorioService directorio = mock(DirectorioService.class);

    private final SelloDirectorioService servicio = new SelloDirectorioService(
            sindicatos, centrales, federaciones,
            sindicatoRepository, centralRepository, federacionRepository,
            procesador, almacen, directorio);

    @Test
    void guardaElSelloEnElNivelYDevuelveElDirectorioActualizado() {
        Sindicato sindicato = new Sindicato();
        sindicato.setId(4L);
        sindicato.setNombre("LIBERTAD");
        byte[] subido = {1, 2, 3};
        byte[] reducido = {4, 5};
        BufferedImage imagen = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        DirectorioResponse esperado = new DirectorioResponse(
                Ambito.SINDICATO, 4L, "LIBERTAD", "/api/v1/archivos/sellos/sello.png",
                false, false, true, List.of());

        when(sindicatos.buscar(4L)).thenReturn(sindicato);
        when(procesador.leer(subido)).thenReturn(imagen);
        when(procesador.generarPng(eq(imagen), anyInt(), anyInt()))
                .thenReturn(new ProcesadorImagenes.Variante(
                        reducido, 10, 10, "image/png"));
        when(directorio.obtener(Ambito.SINDICATO, 4L)).thenReturn(esperado);

        DirectorioResponse respuesta = servicio.guardar(Ambito.SINDICATO, 4L, subido);

        assertThat(respuesta).isSameAs(esperado);
        assertThat(sindicato.getSelloClave())
                .startsWith("sellos/sindicato-4-")
                .endsWith("-libertad.png");
        verify(almacen).guardar(sindicato.getSelloClave(), reducido);
        verify(sindicatoRepository).flush();
    }
}
