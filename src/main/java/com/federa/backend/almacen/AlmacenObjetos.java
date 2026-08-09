package com.federa.backend.almacen;

/**
 * Almacén de objetos: guarda binarios identificados por una clave.
 * <p>
 * La interfaz está pensada con la semántica de un almacén de objetos y no de un
 * sistema de archivos —clave y contenido, sin directorios ni rutas absolutas—
 * justamente para que la implementación se pueda cambiar sin tocar a quien la
 * usa. Hoy escribe en el disco del servidor; migrar a S3 o MinIO es escribir
 * otra clase que cumpla este contrato.
 * <p>
 * Una <b>clave</b> es una ruta lógica separada por barras, del estilo
 * {@code productores/15/ORIGINAL-a1b2c3.jpg}. No es una ruta del sistema: la
 * implementación decide dónde aterriza.
 */
public interface AlmacenObjetos {

    /** Guarda el contenido bajo esa clave, reemplazando lo que hubiera. */
    void guardar(String clave, byte[] contenido);

    /** Lee el objeto. Lanza si no existe. */
    byte[] leer(String clave);

    /** Borra el objeto. No falla si ya no estaba: borrar dos veces da lo mismo. */
    void borrar(String clave);

    boolean existe(String clave);

    /**
     * Ruta pública por la que se sirve ese objeto, relativa al servidor.
     * <p>
     * Es lo que se guarda en la base y lo que consume el cliente. Se devuelve
     * relativa a propósito: guardar la URL absoluta ataría los datos al host y
     * rompería todo al cambiar de dominio o de puerto.
     */
    String urlPublica(String clave);
}
