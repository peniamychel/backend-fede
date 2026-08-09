package com.federa.backend.almacen;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Engancha operaciones sobre archivos al final de la transacción de la base.
 * <p>
 * El disco no participa de la transacción: si se escribe un archivo y después
 * la transacción falla, el archivo queda igual; y si se borra el archivo viejo
 * antes de confirmar, se pierde ante cualquier error posterior. Estas dos
 * ayudas ordenan eso — escribir siempre primero, borrar solo cuando ya no hay
 * vuelta atrás.
 */
public final class TransaccionArchivos {

    private TransaccionArchivos() {
    }

    /**
     * Ejecuta la acción solo si la transacción confirma. Es donde va todo
     * borrado: mientras la transacción pueda deshacerse, el archivo todavía
     * hace falta.
     */
    public static void alConfirmar(Runnable accion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Sin transacción abierta no hay nada que esperar.
            accion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        accion.run();
                    }
                });
    }

    /**
     * Ejecuta la acción solo si la transacción se deshace. Sirve para retirar
     * los archivos que se escribieron para una operación que al final no fue.
     */
    public static void alDeshacer(Runnable accion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int estado) {
                        if (estado == STATUS_ROLLED_BACK) {
                            accion.run();
                        }
                    }
                });
    }
}
