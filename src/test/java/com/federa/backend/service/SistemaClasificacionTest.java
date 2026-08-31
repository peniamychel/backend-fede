package com.federa.backend.service;

import com.federa.backend.dto.TraspasoLoteRequest;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Sistema;
import com.federa.backend.model.TenenciaSistema;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.MotivoTraspaso;
import com.federa.backend.repository.SistemaRepository;
import com.federa.backend.repository.TenenciaSistemaRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SistemaClasificacionTest {

    private final SistemaRepository sistemas = mock(SistemaRepository.class);
    private final TenenciaSistemaRepository tenencias =
            mock(TenenciaSistemaRepository.class);
    private final LoteService lotes = mock(LoteService.class);
    private final SistemaService servicio = new SistemaService(sistemas, tenencias, lotes);

    @Test
    void instalarTambienClasificaLaParcelaComoSistema() {
        Sistema sistema = sistema();
        Lote lote = lote();
        when(sistemas.findById(1L)).thenReturn(Optional.of(sistema));
        when(tenencias.findBySistemaIdAndVigenteIsTrue(1L))
                .thenReturn(Optional.empty());
        when(tenencias.findByLoteIdAndVigenteIsTrue(9L)).thenReturn(Optional.empty());
        when(lotes.buscar(9L)).thenReturn(lote);

        servicio.trasladar(1L, 9L, peticion());

        assertThat(lote.getEstadoLote()).isEqualTo(EstadoLote.CON_SISTEMA);
        assertThat(lote.getEstadoOriginal()).isEqualTo("CON_SISTEMA");
    }

    @Test
    void retirarTambienClasificaLaParcelaComoSinSistema() {
        Sistema sistema = sistema();
        Lote lote = lote();
        lote.setEstadoLote(EstadoLote.CON_SISTEMA);
        TenenciaSistema actual = new TenenciaSistema();
        actual.setSistema(sistema);
        actual.setLote(lote);
        actual.iniciar(LocalDate.now());
        when(sistemas.findById(1L)).thenReturn(Optional.of(sistema));
        when(tenencias.findBySistemaIdAndVigenteIsTrue(1L))
                .thenReturn(Optional.of(actual), Optional.empty());

        servicio.trasladar(1L, null, peticion());

        assertThat(lote.getEstadoLote()).isEqualTo(EstadoLote.SIN_SISTEMA);
        assertThat(lote.getEstadoOriginal()).isEqualTo("SIN_SISTEMA");
    }

    private Sistema sistema() {
        Sistema sistema = new Sistema();
        sistema.setId(1L);
        sistema.setCodigo("S-1");
        return sistema;
    }

    private Lote lote() {
        Lote lote = new Lote();
        lote.setId(9L);
        lote.setNumero("66");
        return lote;
    }

    private TraspasoLoteRequest peticion() {
        return new TraspasoLoteRequest(null, null, MotivoTraspaso.OTRO, null);
    }
}
