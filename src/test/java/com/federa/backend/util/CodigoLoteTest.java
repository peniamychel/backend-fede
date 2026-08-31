package com.federa.backend.util;

import com.federa.backend.model.enums.ExtensionLote;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodigoLoteTest {

    @Test
    void poneLaLetraCompartidaJuntoAlNumeroDeLote() {
        assertThat(CodigoLote.de("22", null, "a")).isEqualTo("22 A");
    }

    @Test
    void sinLetraCompartidaDejaSoloElNumero() {
        assertThat(CodigoLote.de("22", null, null)).isEqualTo("22");
    }

    @Test
    void conservaUnaSubdivisionHistoricaSiExiste() {
        assertThat(CodigoLote.de("22", ExtensionLote.B, "a")).isEqualTo("22-B A");
    }
}
