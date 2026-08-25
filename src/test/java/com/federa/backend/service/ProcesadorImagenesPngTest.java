package com.federa.backend.service;

import com.federa.backend.model.enums.TipoImagen;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
}
