package com.federa.backend.service;

import com.federa.backend.dto.NumeroLoteRequest;
import com.federa.backend.model.*;
import com.federa.backend.model.enums.*;
import com.federa.backend.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NumeroLoteProductorTest {
    final LoteRepository lotes = mock(LoteRepository.class);
    final TenenciaLoteRepository tenencias = mock(TenenciaLoteRepository.class);
    final ProductorRepository productores = mock(ProductorRepository.class);
    final ProductorService servicioProductores = mock(ProductorService.class);
    final SindicatoService sindicatos = mock(SindicatoService.class);
    final LoteService servicio = new LoteService(lotes, tenencias, mock(TenenciaSistemaRepository.class),
            productores, servicioProductores, sindicatos, mock(NumeradorPadron.class));

    @Test void cambiarNumeroConservaClasificacionSuperficieMercadoYSie() {
        Productor p = productor();
        p.setRevisionSieEstado(EstadoRevisionSieProductor.NO_ENCONTRADO);
        Lote lote = new Lote(); lote.setId(11L); lote.setSindicato(p.getSindicato());
        lote.setNumero("22"); lote.setEstadoLote(EstadoLote.FRACCIONADO);
        lote.setEstadoOriginal("FRACCIONADO"); lote.setMercado(Mercado.DETALLISTA);
        lote.setSuperficie(new BigDecimal("3.5"));
        when(productores.findByIdParaRevisionSie(5L)).thenReturn(Optional.of(p));
        when(lotes.findVigentesDeProductor(5L)).thenReturn(List.of(lote));
        TenenciaLote tenencia = new TenenciaLote();
        tenencia.setLote(lote);
        tenencia.setProductor(p);
        when(tenencias.findByLoteIdAndVigenteIsTrue(11L)).thenReturn(Optional.of(tenencia));
        servicio.guardarSoloNumero(5L, new NumeroLoteRequest("33", 11L));
        assertEquals("33", lote.getNumero());
        assertEquals(EstadoLote.FRACCIONADO, lote.getEstadoLote());
        assertEquals("FRACCIONADO", lote.getEstadoOriginal());
        assertEquals(Mercado.DETALLISTA, lote.getMercado());
        assertEquals(new BigDecimal("3.5"), lote.getSuperficie());
        assertEquals(EstadoRevisionSieProductor.NO_ENCONTRADO, p.getRevisionSieEstado());
        verify(tenencias).findVigentesDelNumero(7L, "22");
        verify(tenencias).findVigentesDelNumero(7L, "33");
    }

    @Test void asignarNumeroConservaClasificacionPendienteYSie() {
        Productor p = productor(); p.setClasificacionPendiente(EstadoLote.CON_SISTEMA);
        p.setRevisionSiePendiente(true);
        when(productores.findByIdParaRevisionSie(5L)).thenReturn(Optional.of(p));
        when(lotes.findVigentesDeProductor(5L)).thenReturn(List.of());
        when(sindicatos.buscar(7L)).thenReturn(p.getSindicato());
        when(servicioProductores.buscar(5L)).thenReturn(p);
        servicio.guardarSoloNumero(5L, new NumeroLoteRequest("22", null));
        var captor = org.mockito.ArgumentCaptor.forClass(Lote.class);
        verify(lotes).save(captor.capture());
        assertEquals("22", captor.getValue().getNumero());
        assertEquals(EstadoLote.CON_SISTEMA, captor.getValue().getEstadoLote());
        assertNull(p.getClasificacionPendiente());
        assertTrue(p.isRevisionSiePendiente());
    }

    @Test void reservaLetraBAlGuardarElMismoNumeroYPermiteVolverAAutomatica() {
        Productor p = productor();
        Lote lote = new Lote(); lote.setId(11L); lote.setSindicato(p.getSindicato());
        lote.setNumero("4");
        TenenciaLote tenencia = new TenenciaLote();
        tenencia.setId(1L); tenencia.setLote(lote); tenencia.setProductor(p);
        tenencia.setVigente(true);
        when(productores.findByIdParaRevisionSie(5L)).thenReturn(Optional.of(p));
        when(lotes.findVigentesDeProductor(5L)).thenReturn(List.of(lote));
        when(tenencias.findByLoteIdAndVigenteIsTrue(11L)).thenReturn(Optional.of(tenencia));
        when(tenencias.findVigentesDelNumero(7L, "4")).thenReturn(List.of(tenencia));

        servicio.guardarSoloNumero(5L, new NumeroLoteRequest("4", 11L, "B"));
        assertEquals("B", tenencia.getLetraReservada());
        assertEquals("B", p.getLetraCodigo());

        servicio.guardarSoloNumero(5L, new NumeroLoteRequest("4", 11L, ""));
        assertNull(tenencia.getLetraReservada());
        assertNull(p.getLetraCodigo());
    }

    @Test void noAceptaEditarUnaParcelaDeOtroProductor() {
        Productor p = productor();
        Lote lote = new Lote(); lote.setId(11L);
        when(productores.findByIdParaRevisionSie(5L)).thenReturn(Optional.of(p));
        when(lotes.findVigentesDeProductor(5L)).thenReturn(List.of(lote));
        assertThrows(com.federa.backend.exception.ReglaNegocioException.class,
                () -> servicio.guardarSoloNumero(5L, new NumeroLoteRequest("22", 99L)));
        verify(lotes, never()).flush();
    }

    Productor productor() {
        Central central = new Central(); central.setId(3L);
        Sindicato sindicato = new Sindicato(); sindicato.setId(7L); sindicato.setNombre("SINDICATO");
        sindicato.setCentral(central);
        Productor p = new Productor(); p.setId(5L); p.setNombres("MARIA"); p.setSindicato(sindicato);
        return p;
    }
}
