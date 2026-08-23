package com.federa.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El código del padrón: {@code 2-IVI-1}.
 * <p>
 * Lo que se prueba acá es sobre todo cuándo <b>no</b> hay código. Hoy ninguna
 * central tiene sigla y la federación no tiene número, así que el caso incompleto
 * no es raro: es el único que existe hasta que alguien los cargue.
 */
class CodigoPadronTest {

    @Test
    @DisplayName("junta las tres partes con guiones")
    void arma() {
        assertThat(CodigoPadron.de("2", "IVI", 1)).isEqualTo("2-IVI-1");
    }

    @Test
    @DisplayName("la sigla puede empezar con un número")
    void siglaConDigito() {
        // La de 1RO DE MAYO es 1MO.
        assertThat(CodigoPadron.de("2", "1MO", 45)).isEqualTo("2-1MO-45");
    }

    @Test
    @DisplayName("sin número de federación no hay código")
    void sinNumeroDeFederacion() {
        assertThat(CodigoPadron.de(null, "IVI", 1)).isNull();
        assertThat(CodigoPadron.de("  ", "IVI", 1)).isNull();
    }

    @Test
    @DisplayName("sin sigla de central no hay código")
    void sinSigla() {
        assertThat(CodigoPadron.de("2", null, 1)).isNull();
        assertThat(CodigoPadron.de("2", "  ", 1)).isNull();
    }

    @Test
    @DisplayName("sin número de productor no hay código")
    void sinCorrelativo() {
        // Le pasa a las filas que todavía no pasaron por la migración.
        assertThat(CodigoPadron.de("2", "IVI", null)).isNull();
    }

    @Test
    @DisplayName("no devuelve un código a medias")
    void nadaDeCodigosIncompletos() {
        // La tentación sería devolver "--1" o "2--1" y que se vea algo. Eso
        // termina impreso en una credencial que después circula en papel.
        assertThat(CodigoPadron.de(null, null, 1)).isNull();
    }
}
