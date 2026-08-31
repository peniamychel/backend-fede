package com.federa.backend.model.enums;

import com.federa.backend.util.Textos;

/**
 * Columna "ESTADO DEL LOTE" del padrón.
 * <p>
 * La planilla trae 14 escrituras distintas para lo que en realidad son 6
 * estados. {@link #desde(String)} hace esa normalización. El texto original
 * igualmente se conserva en {@code Lote.estadoOriginal} para no perder
 * información ante una escritura no prevista.
 *
 * <pre>
 * SISTEMA (662), CON SISTEMA (110), C-S (40), SISTEMAS (26),
 * SI (17), CON SISTEMA DETALLISTA (1)   -> CON_SISTEMA
 * SIN SISTEMA (29), NO (11)             -> SIN_SISTEMA
 * BLANCO (141)                          -> BLANCO
 * FRANSIONADOS (16), FRACCIONADO (7),
 * FRACCION (2)                          -> FRACCIONADO
 * DETALLISTA (5)                        -> DETALLISTA
 * COMUNITARIO                           -> COMUNITARIO
 * NUEVO (2)                             -> NUEVO
 * </pre>
 */
public enum EstadoLote {

    /** Lote con un sistema físico instalado (incluye "SISTEMA", "C-S", "SI"). */
    CON_SISTEMA,

    /** Lote sin un sistema físico instalado (incluye "NO"). */
    SIN_SISTEMA,

    /** Lote en blanco / sin asignar. */
    BLANCO,

    /** Lote fraccionado entre varios productores. */
    FRACCIONADO,

    /** Lote clasificado como detallista. */
    DETALLISTA,

    /** Lote destinado al sistema comunitario. */
    COMUNITARIO,

    /** Alta reciente, todavía sin clasificar. */
    NUEVO,

    /** Escritura no contemplada; revisar {@code Lote.estadoOriginal}. */
    DESCONOCIDO;

    public static EstadoLote desde(String valor) {
        String v = Textos.normalizar(valor);
        if (v == null) {
            return null;
        }
        return switch (v) {
            case "SISTEMA", "SISTEMAS", "CON SISTEMA", "CON_SISTEMA", "C-S", "CS", "SI",
                 "CON SISTEMA DETALLISTA" -> CON_SISTEMA;
            case "SIN SISTEMA", "SIN_SISTEMA", "NO" -> SIN_SISTEMA;
            case "BLANCO" -> BLANCO;
            case "FRACCIONADO", "FRACCION", "FRANSIONADOS", "FRACCIONADOS" -> FRACCIONADO;
            case "DETALLISTA" -> DETALLISTA;
            case "COMUNITARIO", "COMUNITARIA" -> COMUNITARIO;
            case "NUEVO" -> NUEVO;
            default -> DESCONOCIDO;
        };
    }
}
