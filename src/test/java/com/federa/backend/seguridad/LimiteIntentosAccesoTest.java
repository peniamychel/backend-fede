package com.federa.backend.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class LimiteIntentosAccesoTest {
    @Test
    void bloqueaElNovenoIntentoYSeLimpiaConExito() {
        LimiteIntentosAcceso limite = new LimiteIntentosAcceso();
        String origen = "192.0.2.10";
        for (int i = 0; i < 8; i++) {
            limite.verificar(origen);
            limite.fallo(origen);
        }

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> limite.verificar(origen));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());

        limite.exito(origen);
        assertDoesNotThrow(() -> limite.verificar(origen));
    }
}
