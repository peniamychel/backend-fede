package com.federa.backend.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Reglas comunes de los códigos cortos usados para entrar al sistema. */
public final class CodigoAcceso {
    private static final Pattern FORMATO = Pattern.compile("[A-Za-z]{5}");
    private static final String ALFABETO =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigoAcceso() {}

    public static boolean esValido(String codigo) {
        return codigo != null && FORMATO.matcher(codigo).matches();
    }

    public static String generar() {
        char[] codigo = new char[5];
        codigo[0] = MAYUSCULAS.charAt(RANDOM.nextInt(MAYUSCULAS.length()));
        codigo[1] = MINUSCULAS.charAt(RANDOM.nextInt(MINUSCULAS.length()));
        for (int i = 2; i < codigo.length; i++) {
            codigo[i] = ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length()));
        }
        for (int i = codigo.length - 1; i > 0; i--) {
            int destino = RANDOM.nextInt(i + 1);
            char temporal = codigo[i];
            codigo[i] = codigo[destino];
            codigo[destino] = temporal;
        }
        return new String(codigo);
    }

    /**
     * Identificador irreversible para localizar al usuario sin guardar el
     * código en texto claro. La contraseña real continúa protegida con BCrypt.
     */
    public static String huella(String codigo) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(codigo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
