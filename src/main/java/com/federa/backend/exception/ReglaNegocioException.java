package com.federa.backend.exception;

/**
 * Se lanza cuando la operación es válida en forma pero rompe una regla del
 * padrón (por ejemplo, borrar una central que todavía tiene sindicatos).
 * Se traduce a HTTP 409.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
