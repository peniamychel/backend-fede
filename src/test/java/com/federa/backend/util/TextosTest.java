package com.federa.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Normalización de nombres para usarlos dentro del nombre de un archivo. */
class TextosTest {

    @Test
    @DisplayName("convierte una búsqueda de nombre completo en un patrón flexible")
    void patronDeBusquedaConVariasPalabras() {
        assertThat(Textos.patronBusqueda("  José   Ángel Pérez  "))
                .isEqualTo("%JOSE%ANGEL%PEREZ%");
    }

    @Test
    @DisplayName("no construye patrón para una búsqueda vacía")
    void patronDeBusquedaVacio() {
        assertThat(Textos.patronBusqueda("   ")).isNull();
    }

    @Test
    @DisplayName("quita tildes y pasa a minúsculas con guiones")
    void normalizaAcentosYEspacios() {
        assertThat(Textos.paraNombreDeArchivo("José Ángel Muñóz", 40))
                .isEqualTo("jose-angel-munoz");
        assertThat(Textos.paraNombreDeArchivo("JUAN   MORALES", 40))
                .isEqualTo("juan-morales");
    }

    @Test
    @DisplayName("descarta cualquier carácter que no sea letra o dígito")
    void descartaCaracteresRaros() {
        // La ñ, los puntos y las comas romperían la clave del almacén, que solo
        // admite letras, dígitos, guiones y puntos.
        assertThat(Textos.paraNombreDeArchivo("O'Connor, María (h)", 40))
                .isEqualTo("o-connor-maria-h");
        assertThat(Textos.paraNombreDeArchivo("../../etc/passwd", 40))
                .isEqualTo("etc-passwd");
    }

    @Test
    @DisplayName("recorta los nombres largos sin dejar un guion suelto al final")
    void recortaLargos() {
        String largo = "MARIA DE LOS ANGELES CONCEPCION HINOJOSA LA FUENTE QUISPE MAMANI";

        String resultado = Textos.paraNombreDeArchivo(largo, 20);

        assertThat(resultado).hasSizeLessThanOrEqualTo(20);
        assertThat(resultado).doesNotEndWith("-");
    }

    @Test
    @DisplayName("siempre devuelve algo usable, aunque no quede nada del nombre")
    void nuncaVacio() {
        // Un archivo tiene que llamarse de alguna forma: devolver cadena vacía
        // produciría una clave como "originales/abc123-.jpg".
        assertThat(Textos.paraNombreDeArchivo(null, 40)).isEqualTo("sin-nombre");
        assertThat(Textos.paraNombreDeArchivo("   ", 40)).isEqualTo("sin-nombre");
        assertThat(Textos.paraNombreDeArchivo("###", 40)).isEqualTo("sin-nombre");
    }
}
