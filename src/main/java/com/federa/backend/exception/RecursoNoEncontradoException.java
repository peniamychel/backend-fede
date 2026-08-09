package com.federa.backend.exception;

/** Se lanza cuando un id solicitado no existe. Se traduce a HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Long id) {
        super("No existe " + recurso + " con id " + id);
    }

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
