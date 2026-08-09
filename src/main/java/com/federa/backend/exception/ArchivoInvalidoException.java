package com.federa.backend.exception;

/**
 * El archivo subido no sirve: vino vacío, pesa de más, o no es del tipo que se
 * esperaba.
 * <p>
 * Es un 400 y no un 409: el problema está en lo que mandó el cliente, no en una
 * regla del padrón.
 */
public class ArchivoInvalidoException extends RuntimeException {

    public ArchivoInvalidoException(String mensaje) {
        super(mensaje);
    }

    public ArchivoInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
