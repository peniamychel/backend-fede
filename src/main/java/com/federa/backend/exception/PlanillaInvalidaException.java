package com.federa.backend.exception;

/**
 * La planilla no se pudo leer: no es un Excel válido, está vacía, o le faltan
 * columnas obligatorias.
 * <p>
 * Hereda de {@link ArchivoInvalidoException} para compartir el manejo: los dos
 * casos son problemas del archivo que mandó el cliente y responden 400.
 */
public class PlanillaInvalidaException extends ArchivoInvalidoException {

    public PlanillaInvalidaException(String mensaje) {
        super(mensaje);
    }

    public PlanillaInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
