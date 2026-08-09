package com.federa.backend.service;

import com.federa.backend.dto.CredencialDirigente;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la credencial de dirigente.
 * <p>
 * Lo que distingue a esta tarjeta de la del productor es que va en vertical, y
 * eso sí se puede comprobar: el tamaño de página está dentro del PDF.
 */
class CredencialDirigentePdfTest {

    private final CredencialDirigentePdf generador = new CredencialDirigentePdf();

    // ------------------------------------------------------------- utilería

    private CredencialDirigente credencial(String cargo, String nivel, String central,
                                           byte[] foto, byte[] firma, byte[] sello) {
        return new CredencialDirigente(
                "FEDERACIÓN CARRASCO", cargo, nivel, "ALTO SAN SALVADOR", central,
                "CANDIDO", "COLQUECHAMBI MAMANI", "3692655", "desde el 01/03/2026",
                foto, firma, sello, "09/08/2026", "AB12CD34EF", qr());
    }

    private CredencialDirigente presidenteDeSindicato() {
        return credencial("Presidente", "Sindicato", "1RO MAYO", null, null, null);
    }

    private String texto(byte[] pdf, int pagina) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(lector).getTextFromPage(pagina).replaceAll("\\s+", " ");
        } finally {
            lector.close();
        }
    }

    private byte[] retrato() throws IOException {
        BufferedImage lienzo = new BufferedImage(240, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(205, 220, 235));
        g.fillRect(0, 0, 240, 320);
        g.setColor(new Color(120, 140, 165));
        g.fillOval(80, 55, 80, 80);
        g.fillOval(45, 150, 150, 190);
        g.dispose();
        return aPng(lienzo);
    }

    private byte[] firma() throws IOException {
        BufferedImage lienzo = new BufferedImage(600, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 240);
        g.setColor(new Color(20, 20, 70));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int previoX = 40;
        int previoY = 150;
        for (int x = 50; x < 560; x += 8) {
            int y = (int) (150 + Math.sin(x * 0.035) * 55 - x * 0.06);
            g.drawLine(previoX, previoY, x, y);
            previoX = x;
            previoY = y;
        }
        g.dispose();
        return aPng(lienzo);
    }

    private byte[] sello() throws IOException {
        BufferedImage lienzo = new BufferedImage(700, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 700, 200);
        g.setColor(new Color(25, 25, 25));
        centrar(g, "CANDIDO COLQUECHAMBI", new Font("Arial", Font.BOLD, 42), 80);
        centrar(g, "PRESIDENTE", new Font("Arial", Font.PLAIN, 34), 140);
        g.dispose();
        return aPng(lienzo);
    }

    private void centrar(Graphics2D g, String texto, Font fuente, int y) {
        g.setFont(fuente);
        g.drawString(texto, (700 - g.getFontMetrics().stringWidth(texto)) / 2, y);
    }

    /** QR de prueba, generado igual que el de verdad. */
    private byte[] qr() {
        return new GeneradorQr().generar("AB12CD34EF");
    }

    private byte[] aPng(BufferedImage imagen) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    // -------------------------------------------------------------- pruebas

    @Test
    @DisplayName("es vertical, y mide lo mismo que la del productor")
    void esVertical() throws IOException {
        byte[] pdf = generador.generar(presidenteDeSindicato());

        PdfReader lector = new PdfReader(pdf);
        try {
            com.lowagie.text.Rectangle hoja = lector.getPageSize(1);
            // Lo que la distingue de la del afiliado: el lado corto es el ancho.
            assertThat(hoja.getWidth()).isCloseTo(153.01f, Offset.offset(0.5f));
            assertThat(hoja.getHeight()).isCloseTo(242.65f, Offset.offset(0.5f));
            assertThat(hoja.getHeight()).isGreaterThan(hoja.getWidth());

            // Mismas medidas que la apaisada, solo que giradas: entra en el
            // mismo portacredencial y sale de la misma impresora.
            assertThat(hoja.getWidth()).isEqualTo(CredencialProductorPdf.ALTO);
            assertThat(hoja.getHeight()).isEqualTo(CredencialProductorPdf.ANCHO);

            assertThat(lector.getNumberOfPages()).isEqualTo(2);
            assertThat(lector.getPageSize(2).getWidth()).isEqualTo(hoja.getWidth());
        } finally {
            lector.close();
        }
    }

    @Test
    @DisplayName("ninguna línea del anverso cae debajo de la banda del pie")
    void elAnversoEntra() {
        // Esta es la prueba de un error real: el sindicato quedaba dibujado
        // detrás de la banda gris del pie, invisible. Ninguna prueba de texto
        // lo podía ver, porque el texto seguía estando en el PDF: solo estaba
        // tapado. Hay que mirar las alturas.
        CredencialDirigentePdf.Alturas alturas = CredencialDirigentePdf.Alturas.calcular();

        assertThat(alturas.holguraAbajo())
                .as("la última línea tiene que despejar la banda del pie")
                .isGreaterThan(4f);

        // Y de arriba hacia abajo, cada bloque debajo del anterior.
        assertThat(alturas.foto() + CredencialDirigentePdf.FOTO_ALTO)
                .isLessThanOrEqualTo(
                        CredencialDirigentePdf.ALTO - CredencialDirigentePdf.BANDA_TITULO);
        assertThat(alturas.franja()).isLessThan(alturas.foto());
        assertThat(alturas.apellidos()).isLessThan(alturas.franja());
        assertThat(alturas.nombres()).isLessThan(alturas.apellidos());
        assertThat(alturas.ciValor()).isLessThan(alturas.ciRotulo());
        assertThat(alturas.lugarRotulo()).isLessThan(alturas.ciValor());
        assertThat(alturas.lugarValor()).isLessThan(alturas.lugarRotulo());
    }

    @Test
    @DisplayName("el anverso destaca el cargo y dice de dónde es")
    void anverso() throws IOException {
        String anverso = texto(generador.generar(presidenteDeSindicato()), 1);

        assertThat(anverso)
                .contains("FEDERACIÓN CARRASCO")
                .contains("CREDENCIAL DE DIRIGENTE")
                .contains("PRESIDENTE")
                .contains("SINDICATO")
                .contains("ALTO SAN SALVADOR")
                .contains("COLQUECHAMBI MAMANI")
                .contains("CANDIDO")
                .contains("3692655")
                .contains("Central 1RO MAYO");
    }

    @Test
    @DisplayName("el reverso lleva el período y la advertencia")
    void reverso() throws IOException {
        String reverso = texto(generador.generar(presidenteDeSindicato()), 2);

        assertThat(reverso)
                .contains("ACREDITACIÓN")
                .contains("PRESIDENTE")
                .contains("EN FUNCIONES")
                .contains("desde el 01/03/2026")
                .contains("personal e intransferible")
                .contains("Emitida el 09/08/2026");
    }

    @Test
    @DisplayName("un período cerrado sale igual, con sus dos fechas")
    void periodoCerrado() throws IOException {
        // Sirve como constancia de que alguien ocupó el cargo.
        CredencialDirigente terminado = new CredencialDirigente(
                "FEDERACIÓN CARRASCO", "Secretario", "Sindicato", "ALTO SAN SALVADOR",
                "1RO MAYO", "MAYRA", "ANDALUZ SANABRIA", "10348064",
                "01/03/2025 — 28/02/2026", null, null, null, "09/08/2026", "AB12CD34EF", qr());

        assertThat(texto(generador.generar(terminado), 2))
                .contains("01/03/2025 — 28/02/2026");
    }

    @Test
    @DisplayName("sin foto queda el recuadro con el aviso")
    void sinFoto() throws IOException {
        assertThat(texto(generador.generar(presidenteDeSindicato()), 1))
                .contains("SIN FOTO");
    }

    @Test
    @DisplayName("con foto no aparece el aviso")
    void conFoto() throws IOException {
        byte[] pdf = generador.generar(
                credencial("Presidente", "Sindicato", "1RO MAYO", retrato(), null, null));

        assertThat(texto(pdf, 1)).doesNotContain("SIN FOTO");
    }

    @Test
    @DisplayName("sin sello se imprime el nombre bajo la línea")
    void sinSello() throws IOException {
        byte[] pdf = generador.generar(
                credencial("Presidente", "Sindicato", "1RO MAYO", null, firma(), null));

        assertThat(texto(pdf, 2)).contains("CANDIDO COLQUECHAMBI MAMANI");
    }

    @Test
    @DisplayName("con sello, el sello reemplaza al nombre escrito")
    void conSello() throws IOException {
        byte[] pdf = generador.generar(
                credencial("Presidente", "Sindicato", "1RO MAYO", null, firma(), sello()));

        assertThat(texto(pdf, 2)).doesNotContain("CANDIDO COLQUECHAMBI MAMANI");
    }

    @Test
    @DisplayName("un cargo de la federación no repite la central")
    void sinCentral() throws IOException {
        // Arriba de la federación no hay nada, y poner su nombre dos veces en
        // la misma tarjeta sería decir lo mismo dos veces.
        byte[] pdf = generador.generar(
                credencial("Vocal", "Federación", null, null, null, null));

        String anverso = texto(pdf, 1);
        assertThat(anverso).contains("VOCAL").contains("FEDERACIÓN");
        assertThat(anverso).doesNotContain("Central ");
        assertThat(texto(pdf, 2)).doesNotContain("CENTRAL");
    }

    @Test
    @DisplayName("una firma corrupta no impide emitir la credencial")
    void firmaCorrupta() throws IOException {
        byte[] pdf = generador.generar(credencial(
                "Presidente", "Sindicato", "1RO MAYO", null, new byte[]{9, 9, 9}, null));

        assertThat(texto(pdf, 1)).contains("COLQUECHAMBI MAMANI");
        assertThat(texto(pdf, 2)).contains("ACREDITACIÓN");
    }

    @Test
    @DisplayName("un apellido largo se achica en vez de cortarse")
    void apellidoLargo() throws IOException {
        CredencialDirigente largo = new CredencialDirigente(
                "FEDERACIÓN CARRASCO", "Secretario", "Sindicato",
                "GUALBERTO VILLARROEL", "PUERTO VILLARROEL",
                "MARÍA DE LOS ÁNGELES", "COLQUECHAMBI DE VILLARROEL SAAVEDRA",
                "8005906-1V", "desde el 01/03/2026", null, null, null, "09/08/2026",
                "AB12CD34EF", qr());

        assertThat(texto(generador.generar(largo), 1))
                .contains("COLQUECHAMBI DE VILLARROEL SAAVEDRA")
                .contains("MARÍA DE LOS ÁNGELES")
                .contains("GUALBERTO VILLARROEL");
    }

    @Test
    @DisplayName("deja una muestra en target/ para revisarla a ojo")
    void muestraParaRevisar() throws IOException {
        byte[] pdf = generador.generar(credencial(
                "Presidente", "Sindicato", "1RO MAYO", retrato(), firma(), sello()));

        Files.write(Path.of("target", "credencial-dirigente.pdf"), pdf);
        assertThat(Path.of("target", "credencial-dirigente.pdf")).exists();
    }
}
