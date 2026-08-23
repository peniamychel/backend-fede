package com.federa.backend.backup;

public class BackupFallidoException extends RuntimeException {

    public BackupFallidoException(String mensaje) {
        super(mensaje);
    }

    public BackupFallidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
