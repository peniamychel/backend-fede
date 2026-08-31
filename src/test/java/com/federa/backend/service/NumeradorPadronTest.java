package com.federa.backend.service;

import com.federa.backend.model.Central;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.ProductorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La regla del 666 al repartir números del padrón.
 * <p>
 * No es una superstición del sistema: es de la gente. Nadie quiere que su
 * credencial diga 666, y en centrales de más de tres mil afiliados el número
 * aparecería cuatro veces. Lo que se fija acá es que el salto no rompa lo demás:
 * la serie sigue subiendo, no repite, y no se come más números de los que
 * molestan.
 */
class NumeradorPadronTest {

    @Test
    @DisplayName("un número común pasa tal cual")
    void comunPasa() {
        assertThat(NumeradorPadron.admisible(1)).isEqualTo(1);
        assertThat(NumeradorPadron.admisible(665)).isEqualTo(665);
        assertThat(NumeradorPadron.admisible(667)).isEqualTo(667);
        assertThat(NumeradorPadron.admisible(3000)).isEqualTo(3000);
    }

    @Test
    @DisplayName("el 666 pelado se saltea")
    void seisSeisSeis() {
        assertThat(NumeradorPadron.admisible(666)).isEqualTo(667);
        assertThat(NumeradorPadron.despuesDe(665)).isEqualTo(667);
    }

    @Test
    @DisplayName("y también donde aparece adentro de otro número")
    void adentroDeOtro() {
        // Es el caso que preocupa de verdad: una central de tres mil afiliados
        // llega al 1666, al 2666 y al 3666.
        assertThat(NumeradorPadron.admisible(1666)).isEqualTo(1667);
        assertThat(NumeradorPadron.admisible(2666)).isEqualTo(2667);
        assertThat(NumeradorPadron.admisible(3666)).isEqualTo(3667);
    }

    @Test
    @DisplayName("el tramo del 6660 al 6669 se saltea entero")
    void tramoCompleto() {
        // 6660 lleva 666 aunque no termine ahí. Son diez seguidos.
        assertThat(NumeradorPadron.admisible(6660)).isEqualTo(6670);
        assertThat(NumeradorPadron.admisible(6666)).isEqualTo(6670);
        assertThat(NumeradorPadron.admisible(6669)).isEqualTo(6670);
        assertThat(NumeradorPadron.admisible(6659)).isEqualTo(6659);
        assertThat(NumeradorPadron.admisible(6670)).isEqualTo(6670);
    }

    @Test
    @DisplayName("no se saltea nada que no lleve 666")
    void noSalteaDeMas() {
        for (int n : new int[] {66, 606, 616, 660, 665, 667, 676, 766, 1660, 1616, 6606, 6066}) {
            assertThat(NumeradorPadron.esAdmisible(n))
                    .as("el %d no tiene por qué saltearse", n)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("numerando de corrido, la serie sube, no repite y no trae 666")
    void serieCompleta() {
        // Cuatro mil afiliados: el tamaño de la central más grande que se espera.
        List<Integer> serie = new ArrayList<>();
        int numero = NumeradorPadron.admisible(1);
        for (int i = 0; i < 4000; i++) {
            serie.add(numero);
            numero = NumeradorPadron.despuesDe(numero);
        }

        assertThat(serie).doesNotHaveDuplicates();
        assertThat(serie).isSorted();
        assertThat(serie).noneMatch(n -> Integer.toString(n).contains("666"));
        // Se saltearon exactamente los cuatro que molestan hasta ahí, así que
        // el último es 4004 y no 4000.
        assertThat(serie).doesNotContain(666, 1666, 2666, 3666);
        assertThat(serie.get(serie.size() - 1)).isEqualTo(4004);
    }

    @Test
    @DisplayName("el número del que arranca no se altera si ya es admisible")
    void arranqueEstable() {
        // Importar dos planillas seguidas tiene que continuar la serie, no
        // moverla: el que sigue al 700 es el 701.
        assertThat(NumeradorPadron.despuesDe(700)).isEqualTo(701);
        assertThat(NumeradorPadron.admisible(700)).isEqualTo(700);
    }

    @Test
    @DisplayName("el siguiente sale del máximo de la central bajo bloqueo")
    void siguienteDeLaCentral() {
        ProductorRepository productores = mock(ProductorRepository.class);
        CentralRepository centrales = mock(CentralRepository.class);
        when(centrales.findByIdParaNumerar(13L))
                .thenReturn(Optional.of(Central.builder().id(13L).build()));
        when(productores.maxCorrelativoDeCentral(13L, null)).thenReturn(44);
        NumeradorPadron numerador = new NumeradorPadron(productores, centrales);

        assertThat(numerador.siguiente(13L)).isEqualTo(45);
        verify(centrales).findByIdParaNumerar(13L);
    }
}
