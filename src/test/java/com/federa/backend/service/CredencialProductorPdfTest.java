package com.federa.backend.service;

import com.federa.backend.dto.CredencialProductor;
import com.federa.backend.dto.DisenoCredencial;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la credencial.
 * <p>
 * Como el informe, se alimenta el generador con datos armados a mano y se lee
 * de vuelta el PDF. Lo que no se puede comprobar leyendo texto —que la tarjeta
 * mida lo que debe— sí se comprueba: el tamaño de página está en el PDF y se
 * verifica al punto.
 */
class CredencialProductorPdfTest {

    private final CredencialProductorPdf generador = new CredencialProductorPdf();

    // ------------------------------------------------------------- utilería

    private CredencialProductor credencial(byte[] foto,
                                           CredencialProductor.Firmante presidente,
                                           CredencialProductor.Firmante secretario) {
        return new CredencialProductor(
                "FEDERACIÓN CARRASCO", "1RO MAYO", "ALTO SAN SALVADOR",
                "CANDIDO", "COLQUECHAMBI MAMANI", "3692655", "12-A",
                foto, null, null, null, presidente, secretario, null,
                "09/08/2026", "AB12CD34EF",
                "2-1MO-7", qr());
    }

    private String texto(byte[] pdf, int pagina) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(lector).getTextFromPage(pagina).replaceAll("\\s+", " ");
        } finally {
            lector.close();
        }
    }

    /** Retrato de prueba, con la proporción de una foto carnet. */
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

    /** Retrato con fondo transparente: la Wiphala debe seguir viéndose debajo. */
    private byte[] retratoSinFondo() throws IOException {
        BufferedImage lienzo = new BufferedImage(240, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = lienzo.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(120, 140, 165));
        g.fillOval(80, 55, 80, 80);
        g.fillOval(45, 150, 150, 190);
        g.dispose();
        return aPng(lienzo);
    }

    private byte[] firmaDe(String trazo) throws IOException {
        BufferedImage lienzo = new BufferedImage(600, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 240);
        g.setColor(new Color(20, 20, 70));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int previoX = 40;
        int previoY = 150;
        double fase = trazo.hashCode() % 10;
        for (int x = 50; x < 560; x += 8) {
            int y = (int) (150 + Math.sin(x * 0.035 + fase) * 55 - x * 0.06);
            g.drawLine(previoX, previoY, x, y);
            previoX = x;
            previoY = y;
        }
        g.dispose();
        return aPng(lienzo);
    }

    private byte[] selloDe(String nombre, String cargo) throws IOException {
        BufferedImage lienzo = new BufferedImage(700, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 700, 200);
        g.setColor(new Color(25, 25, 25));
        // Centrado dentro de la propia imagen: la credencial centra el sello
        // como recuadro, así que un sello con el texto pegado al borde se ve
        // corrido aunque el recuadro esté bien puesto.
        centrar(g, nombre, new Font("Arial", Font.BOLD, 42), 700, 80);
        centrar(g, cargo, new Font("Arial", Font.PLAIN, 34), 700, 140);
        g.dispose();
        return aPng(lienzo);
    }

    private void centrar(Graphics2D g, String texto, Font fuente, int ancho, int y) {
        g.setFont(fuente);
        int medida = g.getFontMetrics().stringWidth(texto);
        g.drawString(texto, (ancho - medida) / 2, y);
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
    @DisplayName("mide lo que una cédula, y es apaisada")
    void tamanoDeCedula() throws IOException {
        byte[] pdf = generador.generar(credencial(null, null, null));

        PdfReader lector = new PdfReader(pdf);
        try {
            com.lowagie.text.Rectangle hoja = lector.getPageSize(1);
            // CR80: 85,6 × 54 mm. Es la medida de una cédula o una tarjeta de
            // banco, así que entra en cualquier portacredencial.
            assertThat(hoja.getWidth()).isCloseTo(242.65f, org.assertj.core.data.Offset.offset(0.5f));
            assertThat(hoja.getHeight()).isCloseTo(153.01f, org.assertj.core.data.Offset.offset(0.5f));
            assertThat(hoja.getWidth()).isGreaterThan(hoja.getHeight());
            // Anverso y reverso, y las dos del mismo tamaño.
            assertThat(lector.getNumberOfPages()).isEqualTo(2);
            assertThat(lector.getPageSize(2).getWidth()).isEqualTo(hoja.getWidth());
        } finally {
            lector.close();
        }
    }

    @Test
    @DisplayName("el anverso lleva los datos del productor")
    void anversoConDatos() throws IOException {
        String anverso = texto(generador.generar(credencial(null, null, null)), 1);

        assertThat(anverso)
                .contains("CANDIDO COLQUECHAMBI MAMANI")
                .contains("12-A")
                .contains("ALTO SAN SALVADOR")
                .contains("1RO MAYO")
                .contains("CARRASCO")
                .contains("2-1MO-7");
        // La plantilla no tiene campo de cédula; el N° superior es el código
        // institucional del padrón.
        assertThat(anverso).doesNotContain("3692655");
    }

    @Test
    @DisplayName("el número de productor ya no va en la credencial")
    void sinNumeroDeProductor() throws IOException {
        // Lo reemplazó el código del padrón, que además dice dónde está la
        // persona. El carné sigue existiendo en la ficha y en la búsqueda; lo
        // que se quitó es imprimirlo acá, y con él el campo del record que lo
        // traía hasta el dibujo.
        String anverso = texto(generador.generar(credencial(null, null, null)), 1);

        assertThat(anverso).doesNotContain("N° PRODUCTOR");
        // El rótulo está en la imagen de fondo; el valor variable sigue estando.
        assertThat(anverso).contains("12-A");
    }

    @Test
    @DisplayName("el reverso reserva los tres niveles, no los datos del productor")
    void reversoConDirectorio() throws IOException {
        String reverso = texto(generador.generar(credencial(null, null, null)), 2);

        // Los nombres de nivel y el texto legal están rasterizados en la
        // plantilla. Lo variable son los tres bloques de firma.
        assertThat(reverso).contains("SIN FIRMANTE");
        // La cédula va solo en el anverso: repetirla atrás gastaría el espacio
        // que necesitan las firmas.
        assertThat(reverso).doesNotContain("3692655");
    }

    @Test
    @DisplayName("sin foto queda el recuadro con el aviso, no un hueco mudo")
    void sinFoto() throws IOException {
        String anverso = texto(generador.generar(credencial(null, null, null)), 1);

        assertThat(anverso).contains("SIN FOTO");
    }

    @Test
    @DisplayName("con foto no aparece el aviso")
    void conFoto() throws IOException {
        byte[] pdf = generador.generar(credencial(retrato(), null, null));

        assertThat(texto(pdf, 1)).doesNotContain("SIN FOTO");
    }

    @Test
    @DisplayName("el pie automático siempre imprime nombre, cargo y organización")
    void firmantesConSello() throws IOException {
        CredencialProductor.Firmante presidente = new CredencialProductor.Firmante(
                "ALBERTO CHOQUE", firmaDe("a"),
                selloDe("ALBERTO CHOQUE", "SECRETARIO GENERAL"));

        String reverso = texto(generador.generar(
                credencial(null, presidente, null)), 2);

        assertThat(reverso).contains("SECRETARIO GENERAL");
        assertThat(reverso).contains("ALBERTO CHOQUE");
    }

    @Test
    @DisplayName("sin sello se imprime el nombre bajo la línea")
    void firmanteSinSello() throws IOException {
        CredencialProductor.Firmante secretario =
                new CredencialProductor.Firmante("BEATRIZ LIMACHI", firmaDe("b"), null);

        String reverso = texto(generador.generar(
                credencial(null, null, secretario)), 2);

        assertThat(reverso).contains("BEATRIZ LIMACHI");
    }

    @Test
    @DisplayName("sin directorio se señalan los tres firmantes faltantes")
    void sinDirectorio() throws IOException {
        String reverso = texto(generador.generar(credencial(retrato(), null, null)), 2);

        assertThat(reverso).contains("SIN FIRMANTE");
    }

    @Test
    @DisplayName("una foto corrupta no impide emitir la credencial")
    void fotoCorrupta() throws IOException {
        byte[] pdf = generador.generar(credencial(new byte[]{9, 9, 9}, null, null));

        assertThat(texto(pdf, 1)).contains("COLQUECHAMBI MAMANI").contains("SIN FOTO");
    }

    @Test
    @DisplayName("un apellido largo se achica en vez de cortarse")
    void apellidoLargo() throws IOException {
        // En un documento de identidad el nombre no se puede truncar.
        CredencialProductor largo = new CredencialProductor(
                "FEDERACIÓN CARRASCO", "1RO MAYO", "ALTO SAN SALVADOR",
                "MARÍA DE LOS ÁNGELES", "COLQUECHAMBI DE VILLARROEL SAAVEDRA",
                "8005906-1V", "50-51, 57-A", null, null, null, "09/08/2026",
                "AB12CD34EF", "2-1MO-7", qr());

        String anverso = texto(generador.generar(largo), 1);

        assertThat(anverso).contains("COLQUECHAMBI DE VILLARROEL SAAVEDRA");
        assertThat(anverso).contains("MARÍA DE LOS ÁNGELES");
        assertThat(anverso).doesNotContain("8005906-1V");
    }

    @Test
    @DisplayName("el código del padrón va impreso en el anverso")
    void codigoDelPadron() throws IOException {
        String anverso = texto(generador.generar(credencial(null, null, null)), 1);

        assertThat(anverso).contains("2-1MO-7");
    }

    @Test
    @DisplayName("un campo agregado en el editor también se imprime en el PDF")
    void campoAgregadoPorEditor() throws IOException {
        DisenoCredencial base = DisenoCredencial.porDefecto();
        List<DisenoCredencial.Elemento> elementos = new ArrayList<>(base.elementos());
        elementos.add(new DisenoCredencial.Elemento(
                "ci-agregada", DisenoCredencial.Cara.CARA,
                DisenoCredencial.Tipo.TEXTO, "CI", "Cédula de identidad",
                12f, 12f, 70f, 8f, 8f, true,
                DisenoCredencial.Alineacion.IZQUIERDA, "#000000", ""));

        DisenoCredencial editado = new DisenoCredencial(base.ancho(), base.alto(), elementos);
        String anverso = texto(generador.generar(
                credencial(null, null, null), editado), 1);

        assertThat(anverso).contains("3692655");
    }

    @Test
    @DisplayName("sin código del padrón no se imprime nada en su lugar")
    void sinCodigoDelPadron() throws IOException {
        // Es lo que pasa mientras la federación no tenga número o la central no
        // tenga sigla. Antes que un "--1" impreso en una credencial que después
        // circula en papel, no va nada.
        CredencialProductor sinCodigo = new CredencialProductor(
                "FEDERACIÓN CARRASCO", "1RO MAYO", "ALTO SAN SALVADOR",
                "CANDIDO", "COLQUECHAMBI MAMANI", "3692655", "12-A",
                null, null, null, "09/08/2026", "AB12CD34EF", null, qr());

        String anverso = texto(generador.generar(sinCodigo), 1);

        assertThat(anverso).doesNotContain("CÓDIGO");
        // Y el resto de la credencial sale igual.
        assertThat(anverso).contains("COLQUECHAMBI MAMANI");
    }

    @Test
    @DisplayName("la rejilla del pliego entra en la hoja carta")
    void laRejillaEntra() {
        // Esta es la prueba de un error real: con cinco filas el bloque medía
        // 805 puntos contra los 792 de una carta, y la fila de arriba salía
        // cortada. No se veía en el texto extraído, solo mirando la hoja.
        float alto = CredencialProductorPdf.FILAS * CredencialProductorPdf.ALTO
                + (CredencialProductorPdf.FILAS - 1) * CredencialProductorPdf.AIRE;
        float ancho = CredencialProductorPdf.COLUMNAS * CredencialProductorPdf.ANCHO
                + (CredencialProductorPdf.COLUMNAS - 1) * CredencialProductorPdf.AIRE;

        // Y con holgura: 18 puntos por lado es lo que se come cualquier
        // impresora hogareña antes de empezar a imprimir.
        assertThat(alto).isLessThan(com.lowagie.text.PageSize.LETTER.getHeight() - 36f);
        assertThat(ancho).isLessThan(com.lowagie.text.PageSize.LETTER.getWidth() - 36f);
    }

    @Test
    @DisplayName("el pliego pone ocho por hoja, y una hoja de reversos por cada una")
    void pliego() throws IOException {
        List<CredencialProductor> tanda = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            tanda.add(new CredencialProductor(
                    "FEDERACIÓN CARRASCO", "1RO MAYO", "ALTO SAN SALVADOR",
                    "PRODUCTOR " + i, "APELLIDO " + i, "800000" + i, String.valueOf(i),
                    null, null, null, "09/08/2026", "CP" + i + "QR",
                    "2-1MO-" + i, qr()));
        }

        byte[] pdf = generador.generarPliego(tanda);

        PdfReader lector = new PdfReader(pdf);
        try {
            // 12 credenciales: una hoja de 8 y otra de 4, cada una con su
            // hoja de reversos.
            assertThat(lector.getNumberOfPages()).isEqualTo(4);
            assertThat(lector.getPageSize(1).getWidth()).isCloseTo(612f,
                    org.assertj.core.data.Offset.offset(1f));
        } finally {
            lector.close();
        }
        assertThat(texto(pdf, 1)).contains("PRODUCTOR 1").contains("PRODUCTOR 8");
        assertThat(texto(pdf, 3)).contains("PRODUCTOR 9").contains("PRODUCTOR 12");
        // La novena no puede haber quedado en la primera hoja.
        assertThat(texto(pdf, 1)).doesNotContain("PRODUCTOR 9");
    }

    @Test
    @DisplayName("el reverso de cada tarjeta cae detrás de su anverso al voltear la hoja")
    void reversosEspejados() {
        float hoja = com.lowagie.text.PageSize.LETTER.getWidth();

        float[] anversoIzquierda = CredencialProductorPdf.posicionEnHoja(0, false);
        float[] reversoIzquierda = CredencialProductorPdf.posicionEnHoja(0, true);
        float[] anversoDerecha = CredencialProductorPdf.posicionEnHoja(1, false);
        float[] reversoDerecha = CredencialProductorPdf.posicionEnHoja(1, true);

        // La primera tarjeta se imprime a la izquierda por delante y a la
        // derecha por detrás. Si las dos cayeran en el mismo lado, al voltear
        // el papel cada reverso terminaría sobre la tarjeta del vecino.
        assertThat(anversoIzquierda[0]).isLessThan(hoja / 2f);
        assertThat(reversoIzquierda[0]).isGreaterThan(hoja / 2f);
        assertThat(anversoDerecha[0]).isGreaterThan(hoja / 2f);
        assertThat(reversoDerecha[0]).isLessThan(hoja / 2f);

        // Y son espejo exacto respecto del centro: el borde izquierdo del
        // anverso tiene que caer donde cae el derecho del reverso.
        assertThat(hoja - (reversoIzquierda[0] + CredencialProductorPdf.ANCHO))
                .isCloseTo(anversoIzquierda[0], org.assertj.core.data.Offset.offset(0.01f));

        // La altura no se toca: voltear por el lado largo no mueve las filas.
        assertThat(reversoIzquierda[1]).isEqualTo(anversoIzquierda[1]);
    }

    @Test
    @DisplayName("las filas siempre arrancan a la misma altura, aunque sobre lugar")
    void filasEnPosicionFija() {
        // Una hoja a medio llenar se recorta igual que una llena: la tarjeta 0
        // de la segunda hoja va donde iba la 0 de la primera.
        assertThat(CredencialProductorPdf.posicionEnHoja(CredencialProductorPdf.POR_HOJA, false))
                .isEqualTo(CredencialProductorPdf.posicionEnHoja(0, false));

        // Y las filas bajan, no suben.
        assertThat(CredencialProductorPdf.posicionEnHoja(2, false)[1])
                .isLessThan(CredencialProductorPdf.posicionEnHoja(0, false)[1]);
    }

    @Test
    @DisplayName("deja muestras en target/ para revisarlas a ojo")
    void muestrasParaRevisar() throws IOException {
        CredencialProductor.Firmante ejecutivo = new CredencialProductor.Firmante(
                "ALBERTO CHOQUE MAMANI", "EJECUTIVO", "FEDERACIÓN CARRASCO",
                firmaDe("ejecutivo"));
        CredencialProductor.Firmante secretarioCentral = new CredencialProductor.Firmante(
                "BEATRIZ LIMACHI QUISPE", "SECRETARIO GENERAL", "CENTRAL 1RO MAYO",
                firmaDe("central"));
        CredencialProductor.Firmante secretarioSindicato = new CredencialProductor.Firmante(
                "CARLOS MAMANI COLQUE", "SECRETARIO GENERAL", "ALTO SAN SALVADOR",
                firmaDe("sindicato"));

        CredencialProductor muestra = new CredencialProductor(
                "FEDERACIÓN CARRASCO", "1RO MAYO", "ALTO SAN SALVADOR",
                "CANDIDO", "COLQUECHAMBI MAMANI", "3692655", "12-A", retratoSinFondo(),
                selloDe("FEDERACIÓN", "CARRASCO"), selloDe("CENTRAL", "1RO MAYO"),
                selloDe("SINDICATO", "ALTO SAN SALVADOR"), ejecutivo,
                secretarioCentral, secretarioSindicato, "09/08/2026", "AB12CD34EF",
                "2-1MO-7", qr());

        Files.write(Path.of("target", "credencial-de-muestra.pdf"),
                generador.generar(muestra));

        List<CredencialProductor> tanda = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            tanda.add(new CredencialProductor(
                    "FEDERACIÓN CARRASCO", "1RO MAYO", "ALTO SAN SALVADOR",
                    "PRODUCTOR " + i, "APELLIDO LARGO NUMERO " + i, "800000" + i,
                    String.valueOf(i), retratoSinFondo(), muestra.selloFederacion(),
                    muestra.selloCentral(), muestra.selloSindicato(), ejecutivo,
                    secretarioCentral, secretarioSindicato, "09/08/2026", "MUESTRA" + i,
                    "2-1MO-" + i, qr()));
        }
        Files.write(Path.of("target", "credenciales-pliego.pdf"),
                generador.generarPliego(tanda));

        assertThat(Path.of("target", "credencial-de-muestra.pdf")).exists();
    }
}
