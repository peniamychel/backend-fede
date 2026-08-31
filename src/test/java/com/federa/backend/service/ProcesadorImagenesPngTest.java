package com.federa.backend.service;

import com.federa.backend.model.enums.TipoImagen;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ProcesadorImagenesPngTest {

    private final ProcesadorImagenes procesador = new ProcesadorImagenes();

    @Test
    void conservaTransparenciaYRespetaLimitesDeLasFotosDeCredencial() throws Exception {
        BufferedImage origen = new BufferedImage(900, 900, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = origen.createGraphics();
        try {
            // Simula cabeza y hombros sobre un lienzo al que se le quitó el fondo.
            g.setColor(new Color(112, 76, 54, 255));
            g.fillOval(285, 120, 330, 390);
            g.setColor(new Color(44, 82, 130, 255));
            g.fillRoundRect(165, 480, 570, 370, 210, 210);
        } finally {
            g.dispose();
        }

        ProcesadorImagenes.Variante foto = procesador.generarPng(origen, TipoImagen.ORIGINAL);
        ProcesadorImagenes.Variante miniatura = procesador.generarPng(origen, TipoImagen.MINIATURA);

        assertThat(foto.tipoMime()).isEqualTo("image/png");
        assertThat(foto.contenido()).hasSizeLessThanOrEqualTo(300 * 1024);
        assertThat(foto.ancho()).isLessThanOrEqualTo(600);
        assertThat(foto.alto()).isLessThanOrEqualTo(600);

        assertThat(miniatura.tipoMime()).isEqualTo("image/png");
        assertThat(miniatura.contenido()).hasSizeLessThanOrEqualTo(30 * 1024);
        assertThat(miniatura.ancho()).isLessThanOrEqualTo(128);
        assertThat(miniatura.alto()).isLessThanOrEqualTo(128);

        BufferedImage resultado = ImageIO.read(new ByteArrayInputStream(foto.contenido()));
        assertThat(resultado.getColorModel().hasAlpha()).isTrue();
        assertThat((resultado.getRGB(0, 0) >>> 24) & 0xff).isZero();
        assertThat((resultado.getRGB(resultado.getWidth() / 2, resultado.getHeight() / 2) >>> 24) & 0xff)
                .isGreaterThan(0);
    }

    @Test
    void conservaTransparenciaYProporcionEnFirmasYSellos() throws Exception {
        BufferedImage origen = new BufferedImage(800, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = origen.createGraphics();
        try {
            g.setColor(new Color(12, 45, 120, 255));
            g.drawLine(80, 120, 720, 80);
        } finally {
            g.dispose();
        }

        ProcesadorImagenes.Variante firma = procesador.generarPng(
                origen, 200, 200 * 1024);

        assertThat(firma.tipoMime()).isEqualTo("image/png");
        assertThat(firma.ancho()).isEqualTo(200);
        assertThat(firma.alto()).isEqualTo(60);
        assertThat(firma.contenido()).hasSizeLessThanOrEqualTo(200 * 1024);

        BufferedImage resultado = ImageIO.read(new ByteArrayInputStream(firma.contenido()));
        assertThat(resultado.getColorModel().hasAlpha()).isTrue();
        assertThat((resultado.getRGB(0, 0) >>> 24) & 0xff).isZero();
    }

    @Test
    void prepararPlantillaPngConservaElHuecoTransparente() throws Exception {
        BufferedImage origen = new BufferedImage(856, 540, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = origen.createGraphics();
        try {
            g.setColor(new Color(20, 90, 60, 255));
            g.fillRect(0, 0, origen.getWidth(), origen.getHeight());
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(580, 80, 200, 280);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream entrada = new ByteArrayOutputStream();
        ImageIO.write(origen, "png", entrada);

        ProcesadorImagenes.Variante plantilla = procesador.prepararPng(
                entrada.toByteArray(), 1800, 1024 * 1024);
        BufferedImage resultado = ImageIO.read(
                new ByteArrayInputStream(plantilla.contenido()));

        assertThat(plantilla.tipoMime()).isEqualTo("image/png");
        assertThat(resultado.getColorModel().hasAlpha()).isTrue();
        assertThat((resultado.getRGB(650, 150) >>> 24) & 0xff).isZero();
    }

    @Test
    void noRechazaUnaMiniaturaPngConMuchoDetalleAunqueSupereElPesoObjetivo() {
        BufferedImage origen = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
        Random aleatorio = new Random(42);
        for (int y = 0; y < origen.getHeight(); y++) {
            for (int x = 0; x < origen.getWidth(); x++) {
                // Simula cabello, piel y texturas irregulares que un PNG no
                // puede reducir tanto como las figuras planas del otro test.
                int alfa = 120 + aleatorio.nextInt(136);
                int rojo = aleatorio.nextInt(256);
                int verde = aleatorio.nextInt(256);
                int azul = aleatorio.nextInt(256);
                origen.setRGB(x, y,
                        (alfa << 24) | (rojo << 16) | (verde << 8) | azul);
            }
        }

        ProcesadorImagenes.Variante miniatura = procesador.generarPng(
                origen, TipoImagen.MINIATURA);

        assertThat(miniatura.ancho()).isLessThanOrEqualTo(128);
        assertThat(miniatura.alto()).isLessThanOrEqualTo(128);
        assertThat(miniatura.contenido()).isNotEmpty();
        // Documenta el caso que antes lanzaba ArchivoInvalidoException.
        assertThat(miniatura.contenido().length)
                .isGreaterThan(TipoImagen.MINIATURA.getPesoObjetivo());
    }

    @Test
    void elOriginalEditableNuncaSuperaTrescientosKilobytes() {
        BufferedImage origen = new BufferedImage(3000, 2200, BufferedImage.TYPE_INT_RGB);
        Random aleatorio = new Random(84);
        for (int y = 0; y < origen.getHeight(); y++) {
            for (int x = 0; x < origen.getWidth(); x++) {
                origen.setRGB(x, y, aleatorio.nextInt(0x1000000));
            }
        }

        ProcesadorImagenes.Variante editable = procesador.generarOriginalEditable(origen);

        assertThat(editable.tipoMime()).isEqualTo("image/jpeg");
        assertThat(editable.contenido())
                .hasSizeLessThanOrEqualTo(ProcesadorImagenes.PESO_ORIGINAL_EDITABLE);
        assertThat(Math.max(editable.ancho(), editable.alto())).isLessThanOrEqualTo(1600);
    }

    @Test
    void elOriginalEditableAplicaLaOrientacionExifAntesDeGuardarse() throws Exception {
        BufferedImage horizontal = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(horizontal, "jpg", jpeg);

        byte[] originalGiradoPorExif = conOrientacionExif(jpeg.toByteArray(), 6);
        ProcesadorImagenes.Variante editable =
                procesador.prepararOriginalEditable(originalGiradoPorExif);

        assertThat(editable.ancho()).isEqualTo(20);
        assertThat(editable.alto()).isEqualTo(40);
    }

    @Test
    void unaFotoDeDocumentoGrandeQuedaDebajoDeTrescientosKilobytes() throws Exception {
        BufferedImage origen = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);
        Random aleatorio = new Random(126);
        for (int y = 0; y < origen.getHeight(); y++) {
            for (int x = 0; x < origen.getWidth(); x++) {
                // Ruido representa un caso más difícil de comprimir que una
                // hoja real con fondo claro y letras oscuras.
                origen.setRGB(x, y, aleatorio.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream entrada = new ByteArrayOutputStream();
        ImageIO.write(origen, "jpg", entrada);

        ProcesadorImagenes.Variante documento =
                procesador.prepararDocumento(entrada.toByteArray());

        assertThat(documento.tipoMime()).isEqualTo("image/jpeg");
        assertThat(documento.contenido())
                .hasSizeLessThanOrEqualTo(ProcesadorImagenes.PESO_DOCUMENTO);
        assertThat(Math.max(documento.ancho(), documento.alto()))
                .isLessThanOrEqualTo(2200);
    }

    /** Inserta un APP1 EXIF mínimo inmediatamente después del SOI del JPEG. */
    private byte[] conOrientacionExif(byte[] jpeg, int orientacion) {
        byte[] exif = {
                'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 42, 0, 8, 0, 0, 0,
                1, 0,
                0x12, 0x01, 3, 0, 1, 0, 0, 0,
                (byte) orientacion, 0, 0, 0,
                0, 0, 0, 0
        };
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(jpeg, 0, 2);
        salida.write(0xff);
        salida.write(0xe1);
        int largo = exif.length + 2;
        salida.write((largo >>> 8) & 0xff);
        salida.write(largo & 0xff);
        salida.write(exif, 0, exif.length);
        salida.write(jpeg, 2, jpeg.length - 2);
        return salida.toByteArray();
    }
}
