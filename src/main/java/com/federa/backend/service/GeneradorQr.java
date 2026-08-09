package com.federa.backend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Dibuja el QR que va en las credenciales.
 * <p>
 * OpenPDF sabe hacer códigos de barras PDF417 y Datamatrix, pero no QR, así que
 * el código lo arma ZXing y el PDF solo coloca la imagen.
 */
@Component
public class GeneradorQr {

    /** Lado del PNG en píxeles. */
    private static final int LADO = 320;

    /**
     * Corrección de errores media: aguanta que se pierda alrededor de un 15%
     * del código.
     * <p>
     * Es la que corresponde para una tarjeta que va a andar en un bolsillo: un
     * QR rayado o con una esquina gastada se sigue leyendo. Subir a alta
     * agrandaría el dibujo, y en un carnet no sobra espacio.
     */
    private static final ErrorCorrectionLevel CORRECCION = ErrorCorrectionLevel.M;

    /**
     * PNG del código, en blanco y negro.
     *
     * @throws IllegalStateException si el contenido no se puede codificar. No
     *                               debería pasar con un código de diez
     *                               caracteres, y no es algo que quien llama
     *                               pueda resolver.
     */
    public byte[] generar(String contenido) {
        Map<EncodeHintType, Object> pistas = new EnumMap<>(EncodeHintType.class);
        pistas.put(EncodeHintType.ERROR_CORRECTION, CORRECCION);
        // Sin margen: el que hace falta lo pone la tarjeta alrededor, y el
        // borde blanco que agrega ZXing por defecto se comería el dibujo.
        pistas.put(EncodeHintType.MARGIN, 0);
        pistas.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        try {
            BitMatrix matriz = new QRCodeWriter()
                    .encode(contenido, BarcodeFormat.QR_CODE, LADO, LADO, pistas);

            BufferedImage imagen = new BufferedImage(
                    matriz.getWidth(), matriz.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matriz.getWidth(); x++) {
                for (int y = 0; y < matriz.getHeight(); y++) {
                    imagen.setRGB(x, y, matriz.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("No se pudo generar el QR de " + contenido, e);
        }
    }
}
