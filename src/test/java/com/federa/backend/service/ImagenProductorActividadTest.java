package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.model.ImagenProductor;
import com.federa.backend.model.Productor;
import com.federa.backend.repository.ImagenProductorRepository;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImagenProductorActividadTest {

    @Test
    void subirLaFotoMarcaAlProductorComoModificadoRecientemente() {
        ImagenProductorRepository imagenes = mock(ImagenProductorRepository.class);
        ProductorService productores = mock(ProductorService.class);
        ProcesadorImagenes procesador = mock(ProcesadorImagenes.class);
        AlmacenObjetos almacen = mock(AlmacenObjetos.class);
        ImagenProductorService servicio = new ImagenProductorService(
                imagenes, productores, procesador, almacen);

        Productor productor = new Productor();
        productor.setId(18L);
        productor.setNombres("MARÍA");
        BufferedImage origen = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ProcesadorImagenes.Variante variante = new ProcesadorImagenes.Variante(
                new byte[]{1, 2, 3}, 2, 2, "image/png");

        when(productores.buscar(18L)).thenReturn(productor);
        when(procesador.leer(any())).thenReturn(origen);
        when(procesador.generarPng(any(), any())).thenReturn(variante);
        when(imagenes.findByProductorIdAndTipo(any(), any())).thenReturn(Optional.empty());
        when(imagenes.save(any(ImagenProductor.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.guardar(18L, new byte[]{9}, "foto.png", null);

        assertThat(productor.getUpdatedAt()).isNotNull();
    }
}
