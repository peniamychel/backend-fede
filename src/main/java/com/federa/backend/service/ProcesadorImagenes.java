package com.federa.backend.service;

import com.federa.backend.dto.RecorteRequest;
import com.federa.backend.exception.ArchivoInvalidoException;
import com.federa.backend.model.enums.TipoImagen;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Convierte la foto que sube el usuario en las variantes que se guardan.
 * <p>
 * El usuario sube una imagen sola y del tamaño que sea —lo que salga de la
 * cámara del teléfono—, y acá se derivan la versión de consulta y la miniatura.
 * La idea es que el peso lo resuelva el servidor: pedirle a quien carga el
 * padrón que abra un editor y comprima la foto antes de subirla es trasladarle
 * un trabajo que la máquina hace mejor y sin equivocarse.
 */
@Component
public class ProcesadorImagenes {

    /**
     * Calidades JPEG que se prueban, de mejor a peor, hasta que el archivo
     * entre en el peso objetivo.
     * <p>
     * Se empieza alto y se baja: por debajo de 0.45 los artefactos se notan en
     * las caras, que es justo lo que hay que poder reconocer en un padrón.
     */
    private static final float[] CALIDADES = {0.85f, 0.75f, 0.65f, 0.55f, 0.45f};

    /** Formato de salida. JPEG comprime fotografías mucho mejor que PNG. */
    private static final String FORMATO = "jpg";
    private static final String MIME = "image/jpeg";

    /** PNG conserva el canal alfa de las fotos a las que se les quitó el fondo. */
    private static final String FORMATO_PNG = "png";
    private static final String MIME_PNG = "image/png";

    /** No se baja de este lado: una credencial no gana nada con menos detalle. */
    private static final int LADO_MINIMO_PNG = 128;

    /** Veces que se reintenta achicando las dimensiones si la calidad no alcanzó. */
    private static final int INTENTOS_REDUCIENDO = 4;

    /** Resultado del procesamiento de una variante. */
    public record Variante(byte[] contenido, int ancho, int alto, String tipoMime) {
    }

    /**
     * Decodifica lo que subió el usuario.
     * <p>
     * Que ImageIO pueda abrirlo es la única validación de formato que vale: la
     * extensión y el {@code Content-Type} los falsifica cualquiera. Con
     * TwelveMonkeys en el classpath, además de JPEG y PNG entran WebP y los
     * JPEG en CMYK de algunas cámaras.
     */
    public BufferedImage leer(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new ArchivoInvalidoException("El archivo vino vacío.");
        }
        try {
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(contenido));
            if (imagen == null) {
                throw new ArchivoInvalidoException(
                        "El archivo no es una imagen que se pueda leer.");
            }
            return imagen;
        } catch (IOException e) {
            throw new ArchivoInvalidoException(
                    "No se pudo leer la imagen: " + e.getMessage(), e);
        }
    }

    /**
     * Devuelve solo la región pedida de la imagen.
     * <p>
     * Se aplica antes de escalar y comprimir: recortar primero significa que
     * los 1600 píxeles del lado mayor se gastan en la parte que interesa y no
     * en el fondo que se va a descartar.
     */
    public BufferedImage recortar(BufferedImage origen, RecorteRequest recorte) {
        if (!recorte.esValido()) {
            throw new ArchivoInvalidoException(
                    "El recorte tiene medidas inválidas: no puede tener ancho o alto en cero, "
                    + "ni empezar fuera de la imagen.");
        }
        if (!recorte.entraEn(origen.getWidth(), origen.getHeight())) {
            throw new ArchivoInvalidoException(String.format(
                    "El recorte (%d,%d %dx%d) se sale de la imagen, que mide %dx%d.",
                    recorte.x(), recorte.y(), recorte.ancho(), recorte.alto(),
                    origen.getWidth(), origen.getHeight()));
        }
        // getSubimage devuelve una vista que comparte los píxeles del original;
        // no copia nada, y el escalado posterior ya produce una imagen nueva.
        return origen.getSubimage(recorte.x(), recorte.y(), recorte.ancho(), recorte.alto());
    }

    /** Genera la variante correspondiente a un tipo de imagen de productor. */
    public Variante generar(BufferedImage origen, TipoImagen tipo) {
        return generar(origen, tipo.getLadoMaximo(), tipo.getPesoObjetivo());
    }

    /**
     * Genera PNG manteniendo transparencia.
     * <p>
     * Es específico de la foto del productor: las firmas siguen en JPEG porque
     * sus servicios generan claves .jpg y no necesitan canal alfa. Como PNG no
     * tiene un control de calidad con pérdida, se reduce el lado de forma
     * gradual hasta cumplir el tope de peso.
     */
    public Variante generarPng(BufferedImage origen, TipoImagen tipo) {
        return generarPng(origen, tipo.getLadoMaximo(), tipo.getPesoObjetivo());
    }

    /**
     * Genera un PNG de dimensiones libres manteniendo el canal alfa.
     * Sirve también para firmas y sellos preparados sin fondo en el cliente.
     * El peso indicado es un objetivo: al alcanzar el lado mínimo se devuelve
     * la mejor aproximación para no rechazar imágenes válidas muy detalladas.
     */
    public Variante generarPng(BufferedImage origen, int ladoMaximo, int pesoObjetivo) {
        int lado = Math.min(ladoMaximo, Math.max(origen.getWidth(), origen.getHeight()));

        while (true) {
            byte[] bytes = comprimirPng(origen, lado);
            // El peso es un objetivo de optimización, no una condición para
            // aceptar la foto. Un PNG con cabello, texturas o muchos bordes
            // puede seguir superándolo incluso a 128 px porque la compresión
            // es sin pérdida. En ese punto se conserva la mejor aproximación:
            // una miniatura algo más pesada sigue siendo válida y no debe
            // impedir que se guarde también la foto principal.
            if (bytes.length <= pesoObjetivo || lado <= LADO_MINIMO_PNG) {
                return describir(bytes, MIME_PNG);
            }
            lado = Math.max(LADO_MINIMO_PNG, (int) (lado * 0.8));
        }
    }

    /**
     * Reduce la imagen hasta que entre en el lado y el peso pedidos.
     * <p>
     * Primero se acota el lado mayor, que es lo que más baja el peso, y después
     * se prueba bajando la calidad. Si con la peor calidad todavía no entra
     * —pasa con fotos enormes de mucho detalle—, se achica otro poco y se
     * repite.
     * <p>
     * {@code ladoMaximo} acota el lado más largo <b>conservando la
     * proporción</b>: una firma apaisada entra en 200×200 quedando, por
     * ejemplo, 200×70. Forzarla a un cuadrado la estiraría y dejaría de
     * parecerse a la firma real.
     */
    public Variante generar(BufferedImage origen, int ladoMaximo, int pesoObjetivo) {
        BufferedImage plana = aplanar(origen);
        int lado = Math.min(ladoMaximo, Math.max(plana.getWidth(), plana.getHeight()));

        byte[] ultimo = null;
        for (int intento = 0; intento < INTENTOS_REDUCIENDO; intento++) {
            for (float calidad : CALIDADES) {
                byte[] bytes = comprimir(plana, lado, calidad);
                ultimo = bytes;
                if (bytes.length <= pesoObjetivo) {
                    return describir(bytes, MIME);
                }
            }
            // Ninguna calidad alcanzó: la imagen tiene demasiado detalle para
            // ese tamaño. Se achica un 25% y se vuelve a probar.
            lado = (int) (lado * 0.75);
            if (lado < 200) {
                break;
            }
        }
        // Devolver la mejor aproximación es preferible a fallar: una foto un
        // poco más pesada de lo previsto sigue sirviendo, y el caso es raro.
        return describir(ultimo, MIME);
    }

    private byte[] comprimir(BufferedImage imagen, int ladoMayor, float calidad) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Thumbnails.of(imagen)
                    // size() respeta la proporción y no agranda si ya es chica.
                    .size(ladoMayor, ladoMayor)
                    .keepAspectRatio(true)
                    .outputFormat(FORMATO)
                    .outputQuality(calidad)
                    .toOutputStream(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new ArchivoInvalidoException(
                    "No se pudo procesar la imagen: " + e.getMessage(), e);
        }
    }

    private byte[] comprimirPng(BufferedImage imagen, int ladoMayor) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Thumbnails.of(imagen)
                    .size(ladoMayor, ladoMayor)
                    .keepAspectRatio(true)
                    .outputFormat(FORMATO_PNG)
                    .toOutputStream(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new ArchivoInvalidoException(
                    "No se pudo procesar la transparencia de la imagen: " + e.getMessage(), e);
        }
    }

    private Variante describir(byte[] bytes, String tipoMime) {
        try {
            BufferedImage resultado = ImageIO.read(new ByteArrayInputStream(bytes));
            return new Variante(bytes, resultado.getWidth(), resultado.getHeight(), tipoMime);
        } catch (IOException e) {
            throw new ArchivoInvalidoException("No se pudo releer la imagen generada.", e);
        }
    }

    /**
     * Pega la imagen sobre fondo blanco si tiene transparencia.
     * <p>
     * JPEG no guarda canal alfa: sin este paso, un PNG con fondo transparente
     * sale con el fondo negro o con los colores invertidos, según el lector.
     */
    private BufferedImage aplanar(BufferedImage origen) {
        if (!origen.getColorModel().hasAlpha()) {
            return origen;
        }
        BufferedImage plana = new BufferedImage(
                origen.getWidth(), origen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = plana.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, plana.getWidth(), plana.getHeight());
            g.drawImage(origen, 0, 0, null);
        } finally {
            g.dispose();
        }
        return plana;
    }
}
