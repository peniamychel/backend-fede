package com.federa.backend.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utilidades de normalización de texto para los datos que provienen de la
 * planilla del padrón (MATRIX), donde el mismo valor aparece escrito de varias
 * formas: con/sin tildes, en minúsculas, con espacios de más y con errores de
 * tipeo.
 */
public final class Textos {

    private Textos() {
    }

    /**
     * Devuelve el texto en MAYÚSCULAS, sin tildes, sin espacios sobrantes.
     * Retorna {@code null} si el valor viene vacío o solo con espacios.
     */
    public static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            return null;
        }
        return Normalizer.normalize(limpio, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase();
    }

    /**
     * Prepara un texto para almacenarlo sin perder cómo se escribe el nombre.
     * Conserva Ñ, tildes y demás caracteres Unicode; solo corrige espacios,
     * deja una representación Unicode consistente y lo pasa a mayúsculas.
     * <p>
     * Para búsquedas, comparaciones o nombres de archivo se debe seguir usando
     * {@link #normalizar(String)}, que sí elimina los diacríticos.
     */
    public static String normalizarParaGuardar(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null
                : Normalizer.normalize(limpio, Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
    }

    /**
     * Convierte un texto en algo usable dentro del nombre de un archivo:
     * minúsculas, sin tildes, y con guiones en lugar de cualquier otra cosa.
     * <p>
     * {@code "José Ángel Muñóz"} queda {@code "jose-angel-munoz"}. Se corta a
     * {@code maximo} caracteres porque un nombre largo del padrón podría
     * empujar la ruta más allá de lo que admite el sistema de archivos.
     * <p>
     * Devuelve {@code "sin-nombre"} si no queda nada utilizable: un archivo
     * tiene que llamarse de alguna forma.
     */
    public static String paraNombreDeArchivo(String valor, int maximo) {
        String normalizado = normalizar(valor);
        if (normalizado == null) {
            return "sin-nombre";
        }
        String guionado = normalizado.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (guionado.isEmpty()) {
            return "sin-nombre";
        }
        if (guionado.length() > maximo) {
            guionado = guionado.substring(0, maximo).replaceAll("-+$", "");
        }
        return guionado.isEmpty() ? "sin-nombre" : guionado;
    }

    /**
     * Limpia el valor conservando el texto original (solo recorta espacios).
     * Retorna {@code null} si queda vacío o si es un marcador de dato ausente
     * como {@code "-"}, que aparece en las columnas C.I y N° LOTE.
     */
    public static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty() || "-".equals(limpio)) {
            return null;
        }
        return limpio;
    }

    /**
     * Patrón para buscar varias palabras consecutivas aunque estén separadas
     * entre las columnas de nombres y apellidos.
     * <p>
     * {@code "José   Pérez"} se convierte en {@code "%JOSE%PEREZ%"}.
     */
    public static String patronBusqueda(String valor) {
        String normalizado = normalizar(valor);
        return normalizado == null ? null : "%" + normalizado.replace(" ", "%") + "%";
    }
}
