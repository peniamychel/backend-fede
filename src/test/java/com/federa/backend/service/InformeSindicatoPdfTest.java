package com.federa.backend.service;

import com.federa.backend.dto.InformeSindicato;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del informe en PDF.
 * <p>
 * El generador no toca la base, así que acá se lo alimenta con datos armados a
 * mano y se lee de vuelta el texto del PDF resultante. Lo que se comprueba es
 * que la información llegue a la hoja y en la página correcta; el aspecto
 * visual hay que mirarlo, y para eso está {@code informe-de-muestra.pdf} que
 * deja la última prueba en target/.
 */
class InformeSindicatoPdfTest {

    private final InformeSindicatoPdf generador = new InformeSindicatoPdf();

    // ------------------------------------------------------------- utilería

    private InformeSindicato informe(int cantidadDeFilas,
                                     InformeSindicato.Dirigente dirigente) {
        List<InformeSindicato.Fila> filas = new ArrayList<>();
        for (int i = 1; i <= cantidadDeFilas; i++) {
            filas.add(new InformeSindicato.Fila(
                    i,
                    "PRODUCTOR " + i,
                    "APELLIDO " + i,
                    "800000" + i,
                    String.valueOf(i),
                    "CP" + i,
                    i % 3 == 0 ? "FALTA FOTO" : ""));
        }
        return new InformeSindicato("FEDERACIÓN CARRASCO", "1RO MAYO", "ALTO SAN SALVADOR",
                filas, dirigente, 2026);
    }

    /**
     * Texto de una página con los espacios normalizados.
     * <p>
     * El extractor corta renglones donde la hoja no tiene ningún corte: el
     * total de páginas va en un objeto aparte del PDF, y un párrafo largo se
     * reparte en varias líneas. Ninguna de las dos cosas cambia lo que se lee
     * impreso, así que se colapsa todo espacio en uno antes de comparar.
     */
    private String texto(byte[] pdf, int pagina) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        try {
            return normalizar(new PdfTextExtractor(lector).getTextFromPage(pagina));
        } finally {
            lector.close();
        }
    }

    private String textoCompleto(byte[] pdf) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder todo = new StringBuilder();
            for (int i = 1; i <= lector.getNumberOfPages(); i++) {
                todo.append(extractor.getTextFromPage(i)).append('\n');
            }
            return normalizar(todo.toString());
        } finally {
            lector.close();
        }
    }

    private String normalizar(String texto) {
        return texto.replaceAll("\\s+", " ").trim();
    }

    private int paginas(byte[] pdf) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        try {
            return lector.getNumberOfPages();
        } finally {
            lector.close();
        }
    }

    /** Un PNG cualquiera, para probar que las firmas se estampan. */
    private byte[] imagen(Color color) throws IOException {
        BufferedImage lienzo = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(lienzo, "png", salida);
        return salida.toByteArray();
    }

    // ------------------------------------------------------------- pruebas

    @Test
    @DisplayName("el encabezado se repite en todas las páginas")
    void encabezadoEnCadaPagina() throws IOException {
        // Suficientes filas para desbordar a una segunda hoja.
        byte[] pdf = generador.generar(informe(80, null));

        assertThat(paginas(pdf)).isGreaterThan(1);
        for (int pagina = 1; pagina <= paginas(pdf); pagina++) {
            assertThat(texto(pdf, pagina))
                    .as("página " + pagina)
                    .contains("FEDERACIÓN CARRASCO")
                    .contains("CENTRAL: 1RO MAYO")
                    .contains("SINDICATO: ALTO SAN SALVADOR")
                    // La fila de encabezados también se repite sola.
                    .contains("NOMBRE COMPLETO")
                    .contains("OBSERVACIONES")
                    .contains("Página " + pagina + " de " + paginas(pdf));
        }
    }

    @Test
    @DisplayName("los dos números que todavía se llenan a mano van como línea vacía")
    void camposParaLlenarAMano() throws IOException {
        String texto = texto(generador.generar(informe(3, null)), 1);

        assertThat(texto).contains("N° FEDERACIÓN: _______________");
        assertThat(texto).contains("N° CENTRAL: _______________");
    }

    @Test
    @DisplayName("cada productor sale una vez, con todas sus columnas")
    void unaFilaPorProductor() throws IOException {
        byte[] pdf = generador.generar(informe(3, null));
        String texto = texto(pdf, 1);

        assertThat(texto).contains("PRODUCTOR 1").contains("APELLIDO 1").contains("8000001");
        assertThat(texto).contains("PRODUCTOR 2").contains("CP2");
        // La tercera es la única con observación: 3 % 3 == 0.
        assertThat(texto).contains("PRODUCTOR 3").contains("FALTA FOTO");
        assertThat(paginas(pdf)).isEqualTo(1);
    }

    @Test
    @DisplayName("el correlativo no se reinicia al cambiar de página")
    void correlativoContinuo() throws IOException {
        byte[] pdf = generador.generar(informe(80, null));

        // La fila 80 tiene que existir, y en la última página.
        assertThat(texto(pdf, paginas(pdf))).contains("PRODUCTOR 80");
        assertThat(textoCompleto(pdf)).contains("PRODUCTOR 40").contains("PRODUCTOR 41");
    }

    @Test
    @DisplayName("el acta va entera en la última página")
    void actaCompletaAlFinal() throws IOException {
        byte[] pdf = generador.generar(informe(80, null));
        String ultima = texto(pdf, paginas(pdf));

        // Texto y firmas juntos: un acta partida entre dos hojas no sirve como
        // constancia de nada.
        assertThat(ultima).contains("ACTA DE ENTREGA");
        assertThat(ultima).contains("2026");
        assertThat(ultima).contains("afiliados con sistema de coca");
        assertThat(ultima).contains("DIRIGENTE/ENTREGUE");
        assertThat(ultima).contains("CENTRAL/ENTREGUE");
        assertThat(ultima).contains("FEDERACIÓN/RECIBÍ");
    }

    @Test
    @DisplayName("sin presidente, el bloque de firma queda vacío para firmar a mano")
    void sinPresidente() throws IOException {
        String texto = textoCompleto(generador.generar(informe(5, null)));

        assertThat(texto).contains("DIRIGENTE/ENTREGUE");
        assertThat(texto).doesNotContain("JUAN MORALES");
    }

    @Test
    @DisplayName("con presidente sin pie de firma se imprime su nombre bajo la línea")
    void presidenteSinPieDeFirma() throws IOException {
        InformeSindicato.Dirigente dirigente =
                new InformeSindicato.Dirigente("JUAN MORALES", imagen(Color.BLUE), null);

        String texto = textoCompleto(generador.generar(informe(5, dirigente)));

        assertThat(texto).contains("JUAN MORALES");
    }

    @Test
    @DisplayName("con pie de firma cargado, la imagen reemplaza al nombre escrito")
    void presidenteConPieDeFirma() throws IOException {
        InformeSindicato.Dirigente dirigente = new InformeSindicato.Dirigente(
                "JUAN MORALES", imagen(Color.BLUE), imagen(Color.RED));

        byte[] pdf = generador.generar(informe(5, dirigente));

        // El pie de firma ya dice quién firma; repetirlo en texto sobraría.
        assertThat(textoCompleto(pdf)).doesNotContain("JUAN MORALES");
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("una firma corrupta no impide imprimir la nómina")
    void firmaCorrupta() throws IOException {
        InformeSindicato.Dirigente roto = new InformeSindicato.Dirigente(
                "JUAN MORALES", new byte[]{1, 2, 3}, null);

        byte[] pdf = generador.generar(informe(5, roto));

        // Se pierde la firma, no el informe: eso es lo que importa.
        assertThat(textoCompleto(pdf)).contains("PRODUCTOR 1").contains("DIRIGENTE/ENTREGUE");
    }

    @Test
    @DisplayName("un sindicato sin productores igual genera su acta")
    void sindicatoVacio() throws IOException {
        byte[] pdf = generador.generar(informe(0, null));

        String texto = texto(pdf, 1);
        assertThat(paginas(pdf)).isEqualTo(1);
        assertThat(texto).contains("todavía no tiene productores registrados");
        assertThat(texto).contains("ACTA DE ENTREGA");
    }

    @Test
    @DisplayName("deja un PDF de muestra en target/ para revisarlo a ojo")
    void muestraParaRevisar() throws IOException {
        InformeSindicato.Dirigente dirigente = new InformeSindicato.Dirigente(
                "JUAN MORALES", imagen(new Color(40, 60, 160)), imagen(new Color(160, 40, 40)));

        byte[] pdf = generador.generar(informe(47, dirigente));

        java.nio.file.Path destino = java.nio.file.Path.of("target", "informe-de-muestra.pdf");
        java.nio.file.Files.write(destino, pdf);
        assertThat(destino).exists();
    }
}
