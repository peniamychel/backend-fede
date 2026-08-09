package com.federa.backend.service;

import com.federa.backend.dto.CredencialProductor;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Dibuja las credenciales de los productores, del tamaño de una cédula.
 * <p>
 * La medida es la del estándar CR80, la misma de una cédula o una tarjeta de
 * banco: 85,6 × 54 mm, apaisada. Se respeta al punto para que salga de una
 * impresora de tarjetas, y para que impresa en hoja y recortada entre igual en
 * cualquier portacredencial.
 * <p>
 * Todo se dibuja con coordenadas absolutas sobre la hoja, y no con tablas: a
 * este tamaño cada punto cuenta, y una tabla decide por su cuenta dónde parte
 * el texto. Los dos métodos de dibujo reciben la esquina de la tarjeta, así que
 * sirven tanto para una credencial sola como para una hoja llena de ellas.
 */
@Component
public class CredencialProductorPdf {

    /** 85,6 mm en puntos. */
    public static final float ANCHO = 242.65f;
    /** 53,98 mm en puntos. */
    public static final float ALTO = 153.01f;

    private static final float BANDA_SUPERIOR = 26f;
    private static final float BANDA_INFERIOR = 22f;
    private static final float MARGEN = 8f;

    /**
     * Rejilla del pliego: dos columnas por cuatro filas, ocho por hoja.
     * <p>
     * No entran cinco filas. Cinco tarjetas son 765 puntos y una carta tiene
     * 792: con la separación para recortar se pasa, y la fila de arriba sale
     * cortada. Con cuatro sobran 72 puntos de margen arriba y abajo, que
     * además es zona que ninguna impresora se come.
     */
    static final int COLUMNAS = 2;
    static final int FILAS = 4;
    static final int POR_HOJA = COLUMNAS * FILAS;
    /** Separación entre tarjetas, para que la tijera tenga por dónde entrar. */
    static final float AIRE = 12f;

    private static final Color VERDE = new Color(20, 74, 58);
    private static final Color GRIS_FONDO = new Color(238, 238, 238);
    private static final Color GRIS_LINEA = new Color(170, 170, 170);
    private static final Color GRIS_ROTULO = new Color(115, 115, 115);

    private static final Font TITULO = fuente(7.5f, Font.BOLD, Color.WHITE);
    private static final Font SUBTITULO = fuente(5.5f, Font.NORMAL, new Color(210, 225, 218));
    private static final Font ROTULO = fuente(5.5f, Font.NORMAL, GRIS_ROTULO);
    private static final Font VALOR = fuente(8f, Font.BOLD, Color.BLACK);
    private static final Font PIE_FUERTE = fuente(6.5f, Font.BOLD, Color.BLACK);
    private static final Font PIE_SUAVE = fuente(6f, Font.NORMAL, new Color(70, 70, 70));
    private static final Font CARGO = fuente(6.5f, Font.BOLD, Color.BLACK);
    private static final Font NOMBRE_FIRMANTE = fuente(5.5f, Font.NORMAL, new Color(60, 60, 60));
    private static final Font NOTA = fuente(5f, Font.NORMAL, GRIS_ROTULO);

    // -------------------------------------------------------- documentos

    /**
     * Una credencial suelta: dos páginas del tamaño exacto de la tarjeta,
     * anverso y reverso.
     * <p>
     * Sirve para una impresora de tarjetas, y también para imprimir a doble
     * cara en tamaño real y recortar.
     */
    public byte[] generar(CredencialProductor credencial) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(new Rectangle(ANCHO, ALTO), 0, 0, 0, 0);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            documento.open();
            PdfContentByte lienzo = escritor.getDirectContent();

            dibujarAnverso(lienzo, 0, 0, credencial);
            // Sin esto la página se descarta: para el documento está vacía,
            // porque todo se pintó directamente sobre la hoja.
            escritor.setPageEmpty(false);
            documento.newPage();
            dibujarReverso(lienzo, 0, 0, credencial);
            escritor.setPageEmpty(false);

            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "No se pudo generar la credencial de " + credencial.apellidos(), e);
        }
        return salida.toByteArray();
    }

    /**
     * Varias credenciales en hojas carta, para imprimir de a tandas.
     * <p>
     * Van dos por fila y cuatro filas, ocho por hoja. Primero la hoja de
     * anversos y después la de sus reversos, con las columnas invertidas: al
     * dar vuelta el papel por el lado largo, cada reverso cae detrás de su
     * anverso. Si se imprime a una sola cara, las hojas de reverso se pueden
     * descartar.
     */
    public byte[] generarPliego(List<CredencialProductor> credenciales) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.LETTER, 0, 0, 0, 0);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            documento.open();
            PdfContentByte lienzo = escritor.getDirectContent();

            for (int desde = 0; desde < credenciales.size(); desde += POR_HOJA) {
                List<CredencialProductor> tanda = credenciales.subList(
                        desde, Math.min(desde + POR_HOJA, credenciales.size()));

                if (desde > 0) {
                    documento.newPage();
                }
                dibujarHoja(lienzo, tanda, false);
                escritor.setPageEmpty(false);

                documento.newPage();
                dibujarHoja(lienzo, tanda, true);
                escritor.setPageEmpty(false);
            }
            documento.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el pliego de credenciales", e);
        }
        return salida.toByteArray();
    }

    private void dibujarHoja(PdfContentByte lienzo, List<CredencialProductor> tanda,
                             boolean reverso) {
        for (int i = 0; i < tanda.size(); i++) {
            float[] esquina = posicionEnHoja(i, reverso);
            if (reverso) {
                dibujarReverso(lienzo, esquina[0], esquina[1], tanda.get(i));
            } else {
                dibujarAnverso(lienzo, esquina[0], esquina[1], tanda.get(i));
            }
        }
    }

    /**
     * Esquina inferior izquierda de la tarjeta número {@code indice} dentro de
     * la hoja, como {x, y}.
     * <p>
     * La rejilla va centrada en la carta, y la fila arranca siempre en la misma
     * altura aunque la hoja quede a medio llenar: así todas las hojas se
     * recortan igual.
     * <p>
     * En el reverso la columna se invierte. Ese es el detalle del que depende
     * la impresión a doble cara: al voltear el papel por el lado largo, lo que
     * se imprimió a la izquierda queda a la derecha, así que hay que
     * imprimirlo del otro lado para que caiga detrás de su anverso.
     */
    static float[] posicionEnHoja(int indice, boolean reverso) {
        float anchoBloque = COLUMNAS * ANCHO + (COLUMNAS - 1) * AIRE;
        float altoBloque = FILAS * ALTO + (FILAS - 1) * AIRE;
        float izquierda = (PageSize.LETTER.getWidth() - anchoBloque) / 2f;
        float arriba = (PageSize.LETTER.getHeight() + altoBloque) / 2f;

        int fila = (indice % POR_HOJA) / COLUMNAS;
        int columna = indice % COLUMNAS;
        int columnaImpresa = reverso ? COLUMNAS - 1 - columna : columna;

        return new float[]{
                izquierda + columnaImpresa * (ANCHO + AIRE),
                arriba - (fila + 1) * ALTO - fila * AIRE};
    }

    // ---------------------------------------------------------- anverso

    /** Pinta el anverso con la esquina inferior izquierda en (x, y). */
    public void dibujarAnverso(PdfContentByte lienzo, float x, float y,
                               CredencialProductor c) {
        // Banda del título.
        lienzo.setColorFill(VERDE);
        lienzo.rectangle(x, y + ALTO - BANDA_SUPERIOR, ANCHO, BANDA_SUPERIOR);
        lienzo.fill();
        centrado(lienzo, c.federacion(), ajustar(c.federacion(), TITULO, ANCHO - 12),
                x + ANCHO / 2f, y + ALTO - 12f);
        centrado(lienzo, "CREDENCIAL DE PRODUCTOR", SUBTITULO,
                x + ANCHO / 2f, y + ALTO - 21f);

        // Foto, con recuadro para que se note aunque falte.
        float fotoAncho = 46f;
        float fotoAlto = 60f;
        float fotoX = x + MARGEN + 1f;
        float fotoY = y + ALTO - BANDA_SUPERIOR - 7f - fotoAlto;
        lienzo.setColorStroke(GRIS_LINEA);
        lienzo.setLineWidth(0.5f);
        lienzo.rectangle(fotoX, fotoY, fotoAncho, fotoAlto);
        lienzo.stroke();
        Image foto = imagen(c.foto(), fotoAncho - 2f, fotoAlto - 2f);
        if (foto != null) {
            foto.setAbsolutePosition(
                    fotoX + (fotoAncho - foto.getScaledWidth()) / 2f,
                    fotoY + (fotoAlto - foto.getScaledHeight()) / 2f);
            agregar(lienzo, foto);
        } else {
            centrado(lienzo, "SIN FOTO", ROTULO, fotoX + fotoAncho / 2f,
                    fotoY + fotoAlto / 2f - 2f);
        }

        // El QR, a la derecha del todo. Es lo que se escanea para pasar lista,
        // así que va en un borde: apoyar la tarjeta contra el lector no
        // depende de acertarle al centro.
        float ladoQr = 34f;
        float qrX = x + ANCHO - MARGEN - ladoQr;
        float qrY = y + 60f;
        Image qr = imagen(c.qr(), ladoQr, ladoQr);
        if (qr != null) {
            qr.setAbsolutePosition(qrX, qrY);
            agregar(lienzo, qr);
            // El código escrito debajo: cuando la cámara no coopera —de noche,
            // en el campo— se teclea a mano y la lista sigue avanzando.
            centrado(lienzo, c.codigo(), ROTULO, qrX + ladoQr / 2f, qrY - 7f);
        }

        // Columna de datos, entre la foto y el QR.
        float datosX = fotoX + fotoAncho + 9f;
        float datosAncho = qrX - 8f - datosX;
        float tope = y + ALTO - BANDA_SUPERIOR - 6f;

        campo(lienzo, "APELLIDOS", c.apellidos(), datosX, tope, datosAncho);
        campo(lienzo, "NOMBRES", c.nombres(), datosX, tope - 24f, datosAncho);
        campo(lienzo, "C.I.", c.ci(), datosX, tope - 48f, datosAncho);

        // La última fila va partida: los dos números son cortos y entran
        // juntos, y así no se desperdicia una fila entera.
        float mitad = datosAncho / 2f;
        campo(lienzo, "N° PRODUCTOR", c.carnetProductor(), datosX, tope - 72f, mitad - 4f);
        campo(lienzo, "N° LOTE", c.lotes(), datosX + mitad, tope - 72f, mitad);

        // Banda del pie, con la pertenencia.
        lienzo.setColorFill(GRIS_FONDO);
        lienzo.rectangle(x, y, ANCHO, BANDA_INFERIOR);
        lienzo.fill();
        izquierda(lienzo, "SINDICATO " + c.sindicato(),
                ajustar("SINDICATO " + c.sindicato(), PIE_FUERTE, ANCHO - 2 * MARGEN),
                x + MARGEN, y + 12.5f);
        izquierda(lienzo, "CENTRAL " + c.central(),
                ajustar("CENTRAL " + c.central(), PIE_SUAVE, ANCHO - 2 * MARGEN),
                x + MARGEN, y + 4.5f);

        marco(lienzo, x, y);
    }

    /** Rótulo chico arriba y el valor debajo, encogido si no entra. */
    private void campo(PdfContentByte lienzo, String rotulo, String valor,
                       float x, float tope, float ancho) {
        izquierda(lienzo, rotulo, ROTULO, x, tope - 6f);
        String texto = valor == null || valor.isBlank() ? "—" : valor;
        izquierda(lienzo, texto, ajustar(texto, VALOR, ancho), x, tope - 16f);
    }

    // ---------------------------------------------------------- reverso

    /** Pinta el reverso con la esquina inferior izquierda en (x, y). */
    public void dibujarReverso(PdfContentByte lienzo, float x, float y,
                               CredencialProductor c) {
        lienzo.setColorFill(VERDE);
        lienzo.rectangle(x, y + ALTO - 20f, ANCHO, 20f);
        lienzo.fill();
        centrado(lienzo, "SINDICATO " + c.sindicato(),
                ajustar("SINDICATO " + c.sindicato(), TITULO, ANCHO - 12),
                x + ANCHO / 2f, y + ALTO - 13f);

        float ancho = (ANCHO - 3 * MARGEN) / 2f;
        firmante(lienzo, x + MARGEN, y, ancho, "PRESIDENTE", c.presidente());
        firmante(lienzo, x + MARGEN * 2 + ancho, y, ancho, "SECRETARIO", c.secretario());

        if (c.emitidaEl() != null && !c.emitidaEl().isBlank()) {
            centrado(lienzo, "Emitida el " + c.emitidaEl(), NOTA, x + ANCHO / 2f, y + 33f);
        }

        // Pie con la advertencia. Va chico a propósito: es letra de respaldo,
        // no algo que haya que leer de lejos.
        lienzo.setColorFill(GRIS_FONDO);
        lienzo.rectangle(x, y, ANCHO, 24f);
        lienzo.fill();
        centrado(lienzo, "Acredita la afiliación al padrón de la federación.", NOTA,
                x + ANCHO / 2f, y + 14f);
        centrado(lienzo, "Es personal e intransferible.", NOTA, x + ANCHO / 2f, y + 6f);

        marco(lienzo, x, y);
    }

    /**
     * Un bloque de firma: la firma arriba, la línea, el cargo, y debajo el
     * sello.
     * <p>
     * Sin firma cargada queda el espacio en blanco, que es lo que hace falta
     * para firmar a mano. Sin sello se imprime el nombre, que cumple la misma
     * función de decir quién firma.
     */
    private void firmante(PdfContentByte lienzo, float x, float y, float ancho,
                          String cargo, CredencialProductor.Firmante firmante) {
        Image firma = imagen(firmante == null ? null : firmante.firma(), ancho, 38f);
        if (firma != null) {
            firma.setAbsolutePosition(
                    x + (ancho - firma.getScaledWidth()) / 2f, y + 94f);
            agregar(lienzo, firma);
        }

        lienzo.setColorStroke(Color.BLACK);
        lienzo.setLineWidth(0.6f);
        lienzo.moveTo(x, y + 92f);
        lienzo.lineTo(x + ancho, y + 92f);
        lienzo.stroke();

        centrado(lienzo, cargo, CARGO, x + ancho / 2f, y + 83f);

        // El sello va inmediatamente debajo del cargo, no al fondo de la
        // tarjeta: pegado a su firma se lee como un bloque, suelto abajo
        // parecía de otra cosa.
        Image sello = imagen(firmante == null ? null : firmante.sello(), ancho, 28f);
        if (sello != null) {
            sello.setAbsolutePosition(
                    x + (ancho - sello.getScaledWidth()) / 2f, y + 50f);
            agregar(lienzo, sello);
        } else if (firmante != null) {
            String nombre = firmante.nombre();
            centrado(lienzo, nombre, ajustar(nombre, NOMBRE_FIRMANTE, ancho),
                    x + ancho / 2f, y + 74f);
        }
    }

    // -------------------------------------------------------- auxiliares

    /**
     * Contorno de la tarjeta, que además marca por dónde recortar.
     * <p>
     * Se dibuja al final, encima de las bandas de color. Y es un rectángulo
     * recto y no redondeado: las bandas llegan hasta el borde, así que unas
     * esquinas curvas dejaban ver el color por fuera del contorno. Además, una
     * línea recta es mejor guía para la tijera.
     */
    private void marco(PdfContentByte lienzo, float x, float y) {
        lienzo.setColorStroke(GRIS_LINEA);
        lienzo.setLineWidth(0.5f);
        lienzo.rectangle(x + 0.25f, y + 0.25f, ANCHO - 0.5f, ALTO - 0.5f);
        lienzo.stroke();
    }

    /**
     * Devuelve la fuente encogida lo necesario para que el texto entre.
     * <p>
     * Un apellido largo en una credencial no se puede cortar: el documento
     * tiene que decir el nombre entero. Por eso se achica la letra en vez de
     * recortar el texto, con un piso de 4,5 puntos para que siga siendo legible.
     */
    private Font ajustar(String texto, Font base, float anchoMaximo) {
        if (texto == null || texto.isBlank()) {
            return base;
        }
        float ancho = ColumnText.getWidth(new Phrase(texto, base));
        if (ancho <= anchoMaximo || ancho <= 0f) {
            return base;
        }
        float tamano = Math.max(base.getSize() * anchoMaximo / ancho, 4.5f);
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, base.getStyle(),
                base.getColor());
    }

    private Image imagen(byte[] contenido, float ancho, float alto) {
        if (contenido == null || contenido.length == 0) {
            return null;
        }
        try {
            Image imagen = Image.getInstance(contenido);
            imagen.scaleToFit(ancho, alto);
            return imagen;
        } catch (Exception e) {
            // Una imagen rota no puede impedir imprimir la credencial: sin ella
            // la tarjeta sigue sirviendo, sin credencial el productor no tiene
            // nada.
            return null;
        }
    }

    private void agregar(PdfContentByte lienzo, Image imagen) {
        try {
            lienzo.addImage(imagen);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo colocar una imagen en la credencial", e);
        }
    }

    private void centrado(PdfContentByte lienzo, String texto, Font fuente, float x, float y) {
        ColumnText.showTextAligned(lienzo, Element.ALIGN_CENTER,
                new Phrase(texto == null ? "" : texto, fuente), x, y, 0f);
    }

    private void izquierda(PdfContentByte lienzo, String texto, Font fuente, float x, float y) {
        ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT,
                new Phrase(texto == null ? "" : texto, fuente), x, y, 0f);
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }
}
