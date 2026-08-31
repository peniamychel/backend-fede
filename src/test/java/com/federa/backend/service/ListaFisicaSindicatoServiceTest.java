package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.SindicatoRepository;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class ListaFisicaSindicatoServiceTest {

    private final ListaFisicaSindicatoService servicio =
            new ListaFisicaSindicatoService(
                    mock(SindicatoRepository.class), mock(AlmacenObjetos.class),
                    new ProcesadorImagenes());

    @Test
    @DisplayName("cada fotografía queda en una página y conserva su orientación")
    void paginasYOrientacion() throws IOException {
        byte[] pdf = servicio.generarPdf(List.of(
                imagen(600, 900, Color.WHITE),
                imagen(900, 600, Color.LIGHT_GRAY)));

        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages()).isEqualTo(2);
            assertThat(lector.getPageSize(1).getHeight())
                    .isGreaterThan(lector.getPageSize(1).getWidth());
            assertThat(lector.getPageSizeWithRotation(2).getWidth())
                    .isGreaterThan(lector.getPageSizeWithRotation(2).getHeight());
        } finally {
            lector.close();
        }
    }

    @Test
    @DisplayName("el PDF no vuelve a remuestrear la imagen ya procesada")
    void noRemuestreaAlCrearPdf() throws IOException {
        byte[] pdf = servicio.generarPdf(List.of(imagen(1200, 1800, Color.WHITE)));

        PdfReader lector = new PdfReader(pdf);
        try {
            PdfDictionary recursos = lector.getPageN(1).getAsDict(PdfName.RESOURCES);
            PdfDictionary objetos = recursos.getAsDict(PdfName.XOBJECT);
            PdfDictionary imagen = (PdfDictionary) PdfReader.getPdfObject(
                    objetos.get(objetos.getKeys().iterator().next()));
            assertThat(imagen.getAsNumber(PdfName.WIDTH).intValue()).isEqualTo(1200);
            assertThat(imagen.getAsNumber(PdfName.HEIGHT).intValue()).isEqualTo(1800);
            assertThat(imagen.get(PdfName.SUBTYPE)).isEqualTo(PdfName.IMAGE);
        } finally {
            lector.close();
        }
    }

    @Test
    @DisplayName("al agregar una foto grande guarda la página por debajo de 300 KB")
    void comprimeAntesDeGuardar() throws IOException {
        SindicatoRepository repositorio = mock(SindicatoRepository.class);
        AlmacenObjetos almacen = mock(AlmacenObjetos.class);
        Map<String, byte[]> objetos = new ConcurrentHashMap<>();
        doAnswer(invocacion -> {
            objetos.put(invocacion.getArgument(0), invocacion.getArgument(1));
            return null;
        }).when(almacen).guardar(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(byte[].class));
        when(almacen.leer(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocacion -> objetos.get(invocacion.getArgument(0)));
        when(almacen.urlPublica(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocacion -> "/api/v1/archivos/" + invocacion.getArgument(0));
        Sindicato sindicato = Sindicato.builder().id(13L).nombre("13 DE JUNIO").build();
        when(repositorio.findById(13L)).thenReturn(Optional.of(sindicato));
        ListaFisicaSindicatoService servicioReal = new ListaFisicaSindicatoService(
                repositorio, almacen, new ProcesadorImagenes());
        byte[] original = imagenConRuido(2600, 1900);
        assertThat(original.length).isGreaterThan(300 * 1024);

        ListaFisicaSindicatoService.ListaFisica resultado = servicioReal.agregar(
                13L, List.of(new ListaFisicaSindicatoService.ArchivoSubido(
                        original, "lista-grande.jpg", "image/jpeg")));

        assertThat(resultado.detallePaginas()).hasSize(1);
        assertThat(resultado.detallePaginas().get(0).tamanoBytes())
                .isLessThanOrEqualTo(ProcesadorImagenes.PESO_DOCUMENTO);
        assertThat(resultado.detallePaginas().get(0).tipoMime()).isEqualTo("image/jpeg");
    }

    private byte[] imagen(int ancho, int alto, Color color) throws IOException {
        BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = lienzo.createGraphics();
        grafico.setColor(color);
        grafico.fillRect(0, 0, ancho, alto);
        grafico.setColor(Color.BLACK);
        grafico.drawString(ancho + " × " + alto, 20, 30);
        grafico.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(lienzo, "jpg", salida);
        return salida.toByteArray();
    }

    private byte[] imagenConRuido(int ancho, int alto) throws IOException {
        BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Random aleatorio = new Random(52);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                lienzo.setRGB(x, y, aleatorio.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(lienzo, "jpg", salida);
        return salida.toByteArray();
    }
}
