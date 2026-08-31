package com.federa.backend.service;

import com.federa.backend.dto.InformeImpresionCentral;
import com.federa.backend.dto.InformeImpresionFederacion;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.repository.CentralRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InformeImpresionFederacionServiceTest {

    @Test
    void consolidaLosTotalesDeTodasLasCentrales() {
        FederacionService federaciones = mock(FederacionService.class);
        CentralRepository centrales = mock(CentralRepository.class);
        InformeImpresionCentralService informes = mock(InformeImpresionCentralService.class);
        Federacion federacion = Federacion.builder().id(1L).nombre("CARRASCO TROPICAL").build();
        Central primera = Central.builder().id(10L).nombre("13 DE JUNIO")
                .federacion(federacion).build();
        Central segunda = Central.builder().id(11L).nombre("CHIMORÉ")
                .federacion(federacion).build();
        when(federaciones.buscar(1L)).thenReturn(federacion);
        when(centrales.findByFederacionIdOrderByNombreAsc(1L))
                .thenReturn(List.of(primera, segunda));
        when(informes.obtener(10L)).thenReturn(informe(primera, 2, 1, 12, 9, 2, 1, 2));
        when(informes.obtener(11L)).thenReturn(informe(segunda, 3, 2, 8, 3, 4, 1, 3));

        InformeImpresionFederacion resultado = new InformeImpresionFederacionService(
                federaciones, centrales, informes).obtener(1L);

        assertThat(resultado.centrales()).isEqualTo(2);
        assertThat(resultado.sindicatos()).isEqualTo(5);
        assertThat(resultado.sindicatosSinSello()).isEqualTo(3);
        assertThat(resultado.total()).isEqualTo(20);
        assertThat(resultado.impresos()).isEqualTo(12);
        assertThat(resultado.pendientes()).isEqualTo(8);
        assertThat(resultado.pendientesConFoto()).isEqualTo(6);
        assertThat(resultado.sinFoto()).isEqualTo(2);
        assertThat(resultado.listosParaImprimir()).isEqualTo(5);
        assertThat(resultado.porcentajeAvance()).isEqualTo(60.0);
        assertThat(resultado.detalle()).extracting(InformeImpresionCentral::central)
                .containsExactly("13 DE JUNIO", "CHIMORÉ");
    }

    private static InformeImpresionCentral informe(Central central, int sindicatos,
                                                     int sinSello, int total, int impresos,
                                                     int conFoto, int sinFoto, int listos) {
        return new InformeImpresionCentral(
                central.getId(), central.getNombre(), central.getFederacion().getNombre(),
                sindicatos, sinSello, total, impresos, total - impresos,
                conFoto, sinFoto, listos,
                total == 0 ? 0 : Math.round(impresos * 1000d / total) / 10d,
                List.of());
    }
}
