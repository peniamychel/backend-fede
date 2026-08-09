package com.federa.backend.dto;

/**
 * Datos de una credencial, ya resueltos.
 * <p>
 * Igual que {@link InformeSindicato}, separa juntar los datos de dibujarlos:
 * el servicio consulta y el generador pinta. Así la credencial se puede probar
 * con datos inventados, sin base ni archivos.
 *
 * @param foto        miniatura del productor, o null si no tiene. Se usa la
 *                    miniatura y no el original porque en la tarjeta ocupa
 *                    menos de dos centímetros: el original solo agregaría peso.
 * @param presidente  presidente en funciones del sindicato, o null.
 * @param secretario  secretario en funciones del sindicato, o null.
 * @param emitidaEl   fecha de emisión ya formateada. Va impresa en el reverso:
 *                    una credencial sin fecha no deja saber si está al día.
 */
public record CredencialProductor(
        String federacion,
        String central,
        String sindicato,
        String nombres,
        String apellidos,
        String ci,
        String carnetProductor,
        String lotes,
        byte[] foto,
        Firmante presidente,
        Firmante secretario,
        String emitidaEl,

        /** Codigo de la credencial, el que dice el QR. */
        String codigo,

        /** PNG del QR ya dibujado. */
        byte[] qr) {

    /**
     * Quien firma el reverso.
     *
     * @param firma JPEG de la firma, o null.
     * @param sello JPEG del pie de firma, o null. Es lo que en la credencial
     *              hace de sello: dice quién firmó y con qué cargo.
     */
    public record Firmante(String nombre, byte[] firma, byte[] sello) {
    }
}
