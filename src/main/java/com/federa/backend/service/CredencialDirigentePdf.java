package com.federa.backend.service;

import com.federa.backend.dto.CredencialDirigente;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

import static com.federa.backend.service.DibujoTarjeta.GRIS_FONDO;
import static com.federa.backend.service.DibujoTarjeta.GRIS_LINEA;
import static com.federa.backend.service.DibujoTarjeta.GRIS_ROTULO;
import static com.federa.backend.service.DibujoTarjeta.LADO_CORTO;
import static com.federa.backend.service.DibujoTarjeta.LADO_LARGO;
import static com.federa.backend.service.DibujoTarjeta.VERDE;
import static com.federa.backend.service.DibujoTarjeta.VERDE_CLARO;
import static com.federa.backend.service.DibujoTarjeta.ajustar;
import static com.federa.backend.service.DibujoTarjeta.centrado;
import static com.federa.backend.service.DibujoTarjeta.colocarCentrada;
import static com.federa.backend.service.DibujoTarjeta.fuente;
import static com.federa.backend.service.DibujoTarjeta.imagen;
import static com.federa.backend.service.DibujoTarjeta.izquierda;
import static com.federa.backend.service.DibujoTarjeta.marco;
import static com.federa.backend.service.DibujoTarjeta.relleno;

/**
 * Credencial de quien ocupa un cargo del directorio.
 * <p>
 * Va <b>en vertical</b>, al revés que la del productor. No es un capricho de
 * diseño: es lo que distingue de un vistazo una credencial de dirigente de una
 * de afiliado, sin tener que leerlas. Mide lo mismo —CR80, 54 × 85,6 mm— así
 * que entra en el mismo portacredencial y sale de la misma impresora.
 * <p>
 * El reverso lleva la firma y el sello del propio titular, que es para lo que
 * sirve esta tarjeta: quien recibe un documento firmado por el presidente de un
 * sindicato puede contrastar la firma contra su credencial.
 */
@Component
public class CredencialDirigentePdf {

    /** En vertical el lado corto es el ancho. */
    static final float ANCHO = LADO_CORTO;
    static final float ALTO = LADO_LARGO;

    private static final float MARGEN = 8f;

    static final float BANDA_TITULO = 32f;
    static final float BANDA_PIE = 18f;
    static final float FOTO_ANCHO = 64f;
    static final float FOTO_ALTO = 80f;

    /**
     * Alturas del anverso, calculadas de arriba hacia abajo.
     * <p>
     * Existe como cálculo aparte, y no como números sueltos dentro del método
     * que dibuja, porque el error que más fácil se cuela acá es que un dato
     * termine debajo de la banda del pie. Pasó: el sindicato quedaba tapado, y
     * las pruebas de texto no lo vieron —el texto seguía estando en el PDF,
     * solo que cubierto—. Con las alturas separadas, la prueba puede verificar
     * que la última línea despeja la banda.
     */
    record Alturas(float foto, float franja, float apellidos, float nombres,
                   float ciRotulo, float ciValor, float lugarRotulo, float lugarValor) {

        static Alturas calcular() {
            float foto = ALTO - BANDA_TITULO - 7f - FOTO_ALTO;
            float franja = foto - 7f - 22f;
            float apellidos = franja - 13f;
            float datos = apellidos - 24f;
            return new Alturas(foto, franja, apellidos, apellidos - 11f,
                    datos, datos - 9f, datos - 22f, datos - 31f);
        }

        /** Lo que queda libre entre la última línea y la banda del pie. */
        float holguraAbajo() {
            return lugarValor - BANDA_PIE;
        }
    }

    private static final Font TITULO = fuente(7f, Font.BOLD, Color.WHITE);
    private static final Font SUBTITULO = fuente(5f, Font.NORMAL, new Color(210, 225, 218));
    private static final Font CARGO = fuente(9.5f, Font.BOLD, VERDE);
    private static final Font NIVEL = fuente(5.5f, Font.NORMAL, GRIS_ROTULO);
    private static final Font APELLIDOS = fuente(9f, Font.BOLD, Color.BLACK);
    private static final Font NOMBRES = fuente(8f, Font.NORMAL, Color.BLACK);
    private static final Font ROTULO = fuente(5f, Font.NORMAL, GRIS_ROTULO);
    private static final Font VALOR = fuente(7f, Font.BOLD, Color.BLACK);
    private static final Font PIE = fuente(5.5f, Font.NORMAL, new Color(70, 70, 70));
    private static final Font NOTA = fuente(4.8f, Font.NORMAL, GRIS_ROTULO);

    /**
     * Dos páginas del tamaño exacto de la tarjeta, anverso y reverso.
     */
    public byte[] generar(CredencialDirigente credencial) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(new Rectangle(ANCHO, ALTO), 0, 0, 0, 0);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            documento.open();
            PdfContentByte lienzo = escritor.getDirectContent();

            dibujarAnverso(lienzo, credencial);
            // Sin esto la página se descarta: para el documento está vacía,
            // porque todo se pintó directamente sobre la hoja.
            escritor.setPageEmpty(false);
            documento.newPage();
            dibujarReverso(lienzo, credencial);
            escritor.setPageEmpty(false);

            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "No se pudo generar la credencial de " + credencial.apellidos(), e);
        }
        return salida.toByteArray();
    }

    // ----------------------------------------------------------- anverso

    private void dibujarAnverso(PdfContentByte lienzo, CredencialDirigente c) {
        Alturas alturas = Alturas.calcular();

        // Banda del título.
        relleno(lienzo, VERDE, 0, ALTO - BANDA_TITULO, ANCHO, BANDA_TITULO);
        centrado(lienzo, c.federacion(), ajustar(c.federacion(), TITULO, ANCHO - 10),
                ANCHO / 2f, ALTO - 13f);
        centrado(lienzo, "CREDENCIAL DE DIRIGENTE", SUBTITULO, ANCHO / 2f, ALTO - 23f);

        // Foto a la izquierda y QR a la derecha. La foto deja de ir centrada
        // para hacerle lugar: el QR tiene que quedar contra un borde, porque
        // pasar lista es apoyar la tarjeta en el lector.
        float fotoX = 12f;
        lienzo.setColorStroke(GRIS_LINEA);
        lienzo.setLineWidth(0.5f);
        lienzo.rectangle(fotoX, alturas.foto(), FOTO_ANCHO, FOTO_ALTO);
        lienzo.stroke();
        Image foto = imagen(c.foto(), FOTO_ANCHO - 2f, FOTO_ALTO - 2f);
        if (foto != null) {
            colocarCentrada(lienzo, foto, fotoX, alturas.foto(), FOTO_ANCHO, FOTO_ALTO);
        } else {
            centrado(lienzo, "SIN FOTO", ROTULO, fotoX + FOTO_ANCHO / 2f,
                    alturas.foto() + FOTO_ALTO / 2f - 2f);
        }

        float ladoQr = 42f;
        float qrX = ANCHO - 12f - ladoQr;
        float qrY = alturas.foto() + 8f;
        Image qr = imagen(c.qr(), ladoQr, ladoQr);
        if (qr != null) {
            qr.setAbsolutePosition(qrX, qrY);
            try {
                lienzo.addImage(qr);
            } catch (DocumentException e) {
                throw new IllegalStateException("No se pudo colocar el QR", e);
            }
            // El código escrito debajo, para teclearlo cuando la cámara falle.
            centrado(lienzo, c.codigo(), ROTULO, qrX + ladoQr / 2f, qrY - 7f);
        }

        // La franja del cargo: es lo que esta tarjeta viene a decir, así que va
        // destacada y no como un dato más de la lista.
        relleno(lienzo, VERDE_CLARO, 0, alturas.franja(), ANCHO, 22f);
        centrado(lienzo, c.cargo().toUpperCase(),
                ajustar(c.cargo().toUpperCase(), CARGO, ANCHO - 12),
                ANCHO / 2f, alturas.franja() + 12f);
        centrado(lienzo, c.nivel().toUpperCase(), NIVEL,
                ANCHO / 2f, alturas.franja() + 4f);

        centrado(lienzo, c.apellidos(), ajustar(c.apellidos(), APELLIDOS, ANCHO - 12),
                ANCHO / 2f, alturas.apellidos());
        centrado(lienzo, c.nombres(), ajustar(c.nombres(), NOMBRES, ANCHO - 12),
                ANCHO / 2f, alturas.nombres());

        centrado(lienzo, "C.I.", ROTULO, ANCHO / 2f, alturas.ciRotulo());
        centrado(lienzo, c.ci().isEmpty() ? "—" : c.ci(), VALOR,
                ANCHO / 2f, alturas.ciValor());

        centrado(lienzo, c.nivel().toUpperCase(), ROTULO, ANCHO / 2f,
                alturas.lugarRotulo());
        centrado(lienzo, c.lugar(), ajustar(c.lugar(), VALOR, ANCHO - 12),
                ANCHO / 2f, alturas.lugarValor());

        // Banda del pie: la central, o nada si el cargo ya es de la federación.
        relleno(lienzo, GRIS_FONDO, 0, 0, ANCHO, BANDA_PIE);
        String pie = c.central() == null ? c.federacion() : "Central " + c.central();
        centrado(lienzo, pie, ajustar(pie, PIE, ANCHO - 12), ANCHO / 2f, 6.5f);

        marco(lienzo, 0, 0, ANCHO, ALTO);
    }

    // ----------------------------------------------------------- reverso

    private void dibujarReverso(PdfContentByte lienzo, CredencialDirigente c) {
        relleno(lienzo, VERDE, 0, ALTO - 22f, ANCHO, 22f);
        centrado(lienzo, "ACREDITACIÓN", TITULO, ANCHO / 2f, ALTO - 14f);

        // Su propia firma: es la razón de ser del reverso.
        float ancho = ANCHO - 2 * MARGEN;
        Image firma = imagen(c.firma(), ancho, 40f);
        if (firma != null) {
            colocarCentrada(lienzo, firma, MARGEN, 172f, ancho, 40f);
        }
        lienzo.setColorStroke(Color.BLACK);
        lienzo.setLineWidth(0.6f);
        lienzo.moveTo(MARGEN, 170f);
        lienzo.lineTo(ANCHO - MARGEN, 170f);
        lienzo.stroke();
        centrado(lienzo, c.cargo().toUpperCase(),
                ajustar(c.cargo().toUpperCase(), fuente(6.5f, Font.BOLD, Color.BLACK),
                        ancho),
                ANCHO / 2f, 161f);

        Image sello = imagen(c.sello(), ancho, 32f);
        if (sello != null) {
            colocarCentrada(lienzo, sello, MARGEN, 120f, ancho, 32f);
        } else {
            String nombre = (c.nombres() + ' ' + c.apellidos()).trim();
            centrado(lienzo, nombre, ajustar(nombre, PIE, ancho), ANCHO / 2f, 151f);
        }

        // Caja con los datos del período. Va en recuadro para que se lea como
        // un bloque y no como texto suelto entre la firma y la nota.
        float cajaY = 36f;
        float cajaAlto = 74f;
        lienzo.setColorStroke(GRIS_LINEA);
        lienzo.setLineWidth(0.5f);
        lienzo.rectangle(MARGEN, cajaY, ancho, cajaAlto);
        lienzo.stroke();

        float fila = cajaY + cajaAlto - 12f;
        dato(lienzo, c.nivel().toUpperCase(), c.lugar(), fila, ancho);
        if (c.central() != null) {
            fila -= 20f;
            dato(lienzo, "CENTRAL", c.central(), fila, ancho);
        }
        fila -= 20f;
        dato(lienzo, "EN FUNCIONES", c.periodo(), fila, ancho);

        relleno(lienzo, GRIS_FONDO, 0, 0, ANCHO, 28f);
        centrado(lienzo, "Acredita el cargo mientras dure el período indicado.", NOTA,
                ANCHO / 2f, 18f);
        centrado(lienzo, "Es personal e intransferible.", NOTA, ANCHO / 2f, 11f);
        centrado(lienzo, "Emitida el " + c.emitidaEl(), NOTA, ANCHO / 2f, 4f);

        marco(lienzo, 0, 0, ANCHO, ALTO);
    }

    private void dato(PdfContentByte lienzo, String rotulo, String valor, float y,
                      float ancho) {
        izquierda(lienzo, rotulo, ROTULO, MARGEN + 6f, y);
        izquierda(lienzo, valor == null || valor.isBlank() ? "—" : valor,
                ajustar(valor, VALOR, ancho - 12f), MARGEN + 6f, y - 9f);
    }
}
