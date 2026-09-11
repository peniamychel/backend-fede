package com.federa.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimilitudNombresTest {

    @Test
    void ignoraTildesMayusculasYOrdenDePalabras() {
        assertThat(SimilitudNombres.porcentaje(
                "José María Pérez", "PEREZ JOSE MARIA")).isEqualTo(100);
    }

    @Test
    void toleraNombreFaltanteSinConfundirIdentidadesDistintas() {
        assertThat(SimilitudNombres.porcentaje(
                "MARIA PEREZ ROJAS", "MARIA ELENA PEREZ ROJAS"))
                .isGreaterThanOrEqualTo(50);
        assertThat(SimilitudNombres.porcentaje(
                "JUAN RAMOS", "MACARIO CONDORI"))
                .isLessThan(50);
    }

    @Test
    void elCincuentaPorCientoExactoSeConsideraCoincidencia() {
        assertThat(SimilitudNombres.porcentaje("JUAN QQQQQ", "JUAN ZZZZZ"))
                .isEqualTo(50);
    }
}
