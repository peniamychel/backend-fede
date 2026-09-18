package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.model.ImagenProductor;
import com.federa.backend.model.Productor;
import com.federa.backend.model.enums.TipoImagen;
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
    void descargaLaFotografiaGrandeComoPngConNombreReconocible() {
        ImagenProductorRepository imagenes = mock(ImagenProductorRepository.class);
        AlmacenObjetos almacen = mock(AlmacenObjetos.class);
        ImagenProductorService servicio = new ImagenProductorService(
                imagenes, mock(ProductorService.class),
                mock(ProcesadorImagenes.class), almacen);
        Productor productor = new Productor();
        productor.setId(18L);
        productor.setNombres("MARÍA");
        productor.setApellidos("PÉREZ");
        ImagenProductor original = ImagenProductor.builder()
                .productor(productor)
                .tipo(TipoImagen.ORIGINAL)
                .tipoMime("image/png")
                .clave("originales/foto.png")
                .build();
        when(imagenes.findByProductorIdAndTipo(18L, TipoImagen.ORIGINAL))
                .thenReturn(Optional.of(original));
        when(almacen.leer("originales/foto.png")).thenReturn(new byte[]{1, 2, 3});

        ImagenProductorService.ArchivoDescarga descarga =
                servicio.descargarOriginal(18L);

        assertThat(descarga.contenido()).containsExactly(1, 2, 3);
        assertThat(descarga.tipoMime()).isEqualTo("image/png");
        assertThat(descarga.nombreArchivo())
                .startsWith("fotografia-").endsWith(".png");
    }

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
