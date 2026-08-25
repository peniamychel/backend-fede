package com.federa.backend.model.enums;

/**
 * Las dos variantes que el servidor guarda de <b>una sola</b> imagen subida.
 * <p>
 * El usuario sube una foto y nada más; estas dos se derivan al guardarla. La
 * miniatura existe para que el listado del padrón no tenga que bajar la foto
 * completa de cada fila.
 */
public enum TipoImagen {

    /** Versión chica para listados; apunta a 30 KB sin rechazar PNG complejos. */
    MINIATURA("miniaturas", 128, 30 * 1024),

    /** Foto cuadrada sin fondo, suficiente para la credencial; apunta a 300 KB. */
    ORIGINAL("originales", 600, 300 * 1024);

    private final String directorio;
    private final int ladoMaximo;
    private final int pesoObjetivo;

    TipoImagen(String directorio, int ladoMaximo, int pesoObjetivo) {
        this.directorio = directorio;
        this.ladoMaximo = ladoMaximo;
        this.pesoObjetivo = pesoObjetivo;
    }

    /**
     * Carpeta del almacén donde vive esta variante.
     * <p>
     * Son dos y solo dos, fijas. Antes se creaba una carpeta por productor y
     * eso dejaba miles de directorios —uno por cada fila del padrón—, la
     * mayoría con dos archivos, y encima quedaban vacíos al borrar un
     * productor. Con dos carpetas planas eso no puede pasar: el nombre del
     * archivo ya identifica de quién es.
     */
    public String getDirectorio() {
        return directorio;
    }

    /**
     * Lado mayor en píxeles. Una foto más grande se escala hasta acá
     * conservando la proporción; una más chica se deja como está, porque
     * agrandarla solo sumaría peso sin sumar detalle.
     */
    public int getLadoMaximo() {
        return ladoMaximo;
    }

    /**
     * Peso al que hay que llegar bajando la calidad.
     * <p>
     * Es un objetivo del guardado, no un requisito de la subida: se acepta la
     * imagen que sea y el servidor la reduce. Antes esto rechazaba archivos
     * grandes, que era pasarle al usuario un trabajo que la máquina hace mejor.
     */
    public int getPesoObjetivo() {
        return pesoObjetivo;
    }

    public static TipoImagen desde(String valor) {
        for (TipoImagen t : values()) {
            if (t.name().equalsIgnoreCase(valor)) {
                return t;
            }
        }
        throw new IllegalArgumentException(
                "Tipo de imagen inválido: " + valor + ". Se espera MINIATURA u ORIGINAL.");
    }
}
