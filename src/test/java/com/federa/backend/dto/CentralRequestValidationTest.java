package com.federa.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CentralRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aceptaCodigoMembretadoDeDosDigitos() {
        CentralRequest request = new CentralRequest("CENTRAL DE PRUEBA", "07", 1L);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void conservaLosCodigosAlfanumericosDeTresCaracteres() {
        CentralRequest request = new CentralRequest("CENTRAL DE PRUEBA", "1MO", 1L);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void dosLetrasNoSeConfundenConElCodigoNumericoDeDosDigitos() {
        CentralRequest request = new CentralRequest("CENTRAL DE PRUEBA", "IV", 1L);

        assertThat(validator.validate(request))
                .extracting(violacion -> violacion.getMessage())
                .contains("el código membretado debe tener 3 letras o números, o 2 dígitos");
    }
}
