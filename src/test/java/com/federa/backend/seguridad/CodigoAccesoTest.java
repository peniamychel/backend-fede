package com.federa.backend.seguridad;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CodigoAccesoTest {
    @Test
    void generaCincoLetrasYNoGuardaElCodigoComoIdentificador() {
        Set<String> generados = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String codigo = CodigoAcceso.generar();
            assertTrue(CodigoAcceso.esValido(codigo));
            assertEquals(5, codigo.length());
            assertTrue(codigo.chars().anyMatch(Character::isUpperCase));
            assertTrue(codigo.chars().anyMatch(Character::isLowerCase));
            assertNotEquals(codigo, CodigoAcceso.huella(codigo));
            generados.add(codigo);
        }
        assertTrue(generados.size() > 95);
    }

    @Test
    void distingueMayusculasDeMinusculas() {
        assertNotEquals(CodigoAcceso.huella("aBcDe"), CodigoAcceso.huella("AbCdE"));
        assertFalse(CodigoAcceso.esValido("ABC1d"));
        assertFalse(CodigoAcceso.esValido("ABCD"));
    }
}
