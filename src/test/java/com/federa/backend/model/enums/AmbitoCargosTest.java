package com.federa.backend.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AmbitoCargosTest {

    @Test
    void respetaLosCargosYElOrdenDeCadaNivel() {
        assertThat(Ambito.SINDICATO.getCargos()).containsExactly(
                TipoCargo.SECRETARIO_GENERAL,
                TipoCargo.SECRETARIO_RELACIONES,
                TipoCargo.HACIENDAS,
                TipoCargo.VOCAL);
        assertThat(Ambito.CENTRAL.getCargos()).containsExactly(
                TipoCargo.SECRETARIO_GENERAL,
                TipoCargo.SECRETARIO_RELACIONES,
                TipoCargo.HACIENDAS,
                TipoCargo.VOCAL);
        assertThat(Ambito.FEDERACION.getCargos()).containsExactly(
                TipoCargo.EJECUTIVO,
                TipoCargo.SECRETARIO_GENERAL,
                TipoCargo.SECRETARIO_RELACIONES,
                TipoCargo.HACIENDAS,
                TipoCargo.VOCAL);
    }

    @Test
    void losFirmantesDependenDelNivel() {
        assertThat(Ambito.SINDICATO.puedeFirmar(TipoCargo.SECRETARIO_GENERAL)).isTrue();
        assertThat(Ambito.SINDICATO.puedeFirmar(TipoCargo.SECRETARIO_RELACIONES)).isFalse();
        assertThat(Ambito.FEDERACION.puedeFirmar(TipoCargo.EJECUTIVO)).isTrue();
        assertThat(Ambito.FEDERACION.puedeFirmar(TipoCargo.SECRETARIO_GENERAL)).isFalse();
        assertThat(Ambito.FEDERACION.puedeFirmar(TipoCargo.SECRETARIO_RELACIONES)).isFalse();
        assertThat(Ambito.CENTRAL.puedeFirmar(TipoCargo.HACIENDAS)).isFalse();
    }
}
