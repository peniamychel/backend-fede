package com.federa.backend.util;

import com.federa.backend.model.Central;
import com.federa.backend.model.Productor;

/**
 * El código con el que se identifica a un productor dentro del padrón:
 * {@code 2-IVI-1}.
 * <p>
 * Tres partes separadas por guion: el número de la federación, la sigla de la
 * central y el número del productor dentro de esa central. Se arma al momento
 * de mostrarlo y no se guarda armado, así que el día que a una central le
 * pongan la sigla, todos sus productores muestran su código sin tocar una fila.
 */
public final class CodigoPadron {

    private CodigoPadron() {
    }

    /**
     * Devuelve el código del productor, o {@code null} si todavía no se puede
     * armar.
     * <p>
     * Falta alguna de las tres partes mientras la federación no tenga número o
     * la central no tenga sigla, que hoy es el caso de todas. Se devuelve null y
     * no un código a medias como {@code "--1"}: media respuesta acá se
     * imprimiría en una credencial y quedaría circulando en papel.
     */
    public static String de(Productor productor) {
        if (productor == null || productor.getCorrelativo() == null) {
            return null;
        }
        Central central = productor.getSindicato().getCentral();
        return de(central.getFederacion().getNumero(), central.getAbreviatura(),
                productor.getCorrelativo());
    }

    /** La misma regla, con las partes sueltas. */
    public static String de(String numeroFederacion, String abreviaturaCentral,
                            Integer correlativo) {
        if (vacio(numeroFederacion) || vacio(abreviaturaCentral) || correlativo == null) {
            return null;
        }
        return numeroFederacion + "-" + abreviaturaCentral + "-" + correlativo;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
