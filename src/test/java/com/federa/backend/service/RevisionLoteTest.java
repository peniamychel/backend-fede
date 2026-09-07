package com.federa.backend.service;

import com.federa.backend.dto.LoteRequest;
import com.federa.backend.dto.ProductorResponse;
import com.federa.backend.model.*;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.TipoImagen;
import com.federa.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RevisionLoteTest {
    private final Map<TipoImagen, String> foto = Map.of(TipoImagen.MINIATURA, "/foto.png");

    @Test
    void historicoSinClasificacionNiLoteQuedaEnRevisionAunqueTengaFoto() {
        var respuesta = ProductorResponse.desde(productor(), foto);
        assertThat(respuesta.revisionLotePendiente()).isTrue();
        assertThat(respuesta.clasificacion()).isNull();
        assertThat(respuesta.credencialLista()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(EstadoLote.class)
    void todasLasClasificacionesSinNumeroImpidenEstarListo(EstadoLote estado) {
        var p = productor();
        p.setClasificacionPendiente(estado);
        assertThat(ProductorResponse.desde(p, foto).clasificacion()).isEqualTo(estado);
        var lote = new Lote();
        lote.setNumero("  ");
        lote.setEstadoLote(estado);
        p.tomarLote(lote, LocalDate.now());
        var respuesta = ProductorResponse.desde(p, foto);
        assertThat(respuesta.revisionLotePendiente()).isTrue();
        assertThat(respuesta.credencialLista()).isFalse();
        assertThat(respuesta.clasificacion()).isEqualTo(estado);
        var requisitos = new RequisitosCredencial(new ReglasDirectorio(false, false));
        assertThat(requisitos.delProductor(p, true, !respuesta.revisionLotePendiente()))
                .extracting(com.federa.backend.dto.CredencialPrevia.Faltante::campo)
                .containsExactly("Número de lote");
    }

    @Test
    void numeroExistenteSinClasificacionNoSeConfundeConFaltaDeLote() {
        var p = productor();
        var lote = new Lote();
        lote.setNumero("22");
        p.tomarLote(lote, LocalDate.now());
        var respuesta = ProductorResponse.desde(p, foto);
        assertThat(respuesta.clasificacion()).isNull();
        assertThat(respuesta.revisionLotePendiente()).isFalse();
        assertThat(respuesta.credencialLista()).isTrue();
    }

    @Test
    void observadoNoQuedaListoAunqueTengaFotoYNumeroDeLote() {
        var p = productor();
        p.setObservacionManual("REVISAR DOCUMENTACIÓN");
        var lote = new Lote();
        lote.setNumero("22");
        p.tomarLote(lote, LocalDate.now());

        var respuesta = ProductorResponse.desde(p, foto);

        assertThat(respuesta.observado()).isTrue();
        assertThat(respuesta.observacion()).isEqualTo("REVISAR DOCUMENTACIÓN");
        assertThat(respuesta.credencialLista()).isFalse();
    }

    @Test
    void completarNumeroCierraRevisionYRetirarloLaReabreSinCambiarElCodigo() {
        var p = productor();
        var lote = new Lote();
        lote.setEstadoLote(EstadoLote.CON_SISTEMA);
        p.tomarLote(lote, LocalDate.now());
        assertThat(ProductorResponse.desde(p, foto).revisionLotePendiente()).isTrue();
        lote.setNumero("22");
        var completa = ProductorResponse.desde(p, foto);
        assertThat(completa.revisionLotePendiente()).isFalse();
        assertThat(completa.credencialLista()).isTrue();
        assertThat(completa.codigoPadron()).isEqualTo("2-13J-100");
        // Completar lote no reemplaza los demás requisitos.
        assertThat(ProductorResponse.desde(p, Map.of()).credencialLista()).isFalse();
        p.getTenencias().get(0).terminar(LocalDate.now());
        assertThat(ProductorResponse.desde(p, foto).revisionLotePendiente()).isTrue();
        assertThat(p.getCorrelativo()).isEqualTo(100);
    }

    @Test
    void elListadoUsaLasTenenciasPrecargadasIgualQueLaFicha() {
        var p = productor();
        var lote = new Lote();
        lote.setNumero("22");
        lote.setEstadoLote(EstadoLote.BLANCO);
        var t = p.tomarLote(lote, LocalDate.now());
        var ficha = ProductorResponse.desde(p, foto);
        p.getTenencias().clear();
        var lista = ProductorResponse.desde(p, foto, List.of(t));
        assertThat(lista).isEqualTo(ficha);
    }

    @Test
    void crearLoteTransfiereClasificacionPendienteSiNoSeIndicaOtra() {
        comprobarAsignacion(null, EstadoLote.CON_SISTEMA);
    }

    @Test
    void laClasificacionConfirmadaAlAsignarPuedeCorregirLaDelExcel() {
        comprobarAsignacion("BLANCO", EstadoLote.BLANCO);
    }

    private void comprobarAsignacion(String estado, EstadoLote esperado) {
        var p = productor();
        p.setClasificacionPendiente(EstadoLote.CON_SISTEMA);
        var productores = mock(ProductorService.class);
        var sindicatos = mock(SindicatoService.class);
        when(productores.buscar(1L)).thenReturn(p);
        when(sindicatos.buscar(7L)).thenReturn(p.getSindicato());
        var servicio = new LoteService(mock(LoteRepository.class),
                mock(TenenciaLoteRepository.class), mock(TenenciaSistemaRepository.class),
                mock(ProductorRepository.class), productores, sindicatos, mock(NumeradorPadron.class));
        servicio.crear(new LoteRequest("22", null, estado, null, 7L, null, 1L));
        assertThat(p.getClasificacionPendiente()).isNull();
        assertThat(p.getTenencias()).hasSize(1);
        assertThat(p.getTenencias().get(0).getLote().getEstadoLote()).isEqualTo(esperado);
        assertThat(ProductorResponse.desde(p, foto).revisionLotePendiente()).isFalse();
    }

    private Productor productor() {
        var f = new Federacion();
        f.setNumero("2");
        var c = new Central();
        c.setId(3L);
        c.setAbreviatura("13J");
        c.setFederacion(f);
        var s = new Sindicato();
        s.setId(7L);
        s.setCentral(c);
        var p = new Productor();
        p.setId(1L);
        p.setSindicato(s);
        p.setNombres("MARÍA");
        p.setApellidos("PÉREZ");
        p.setCi("123456");
        p.setCorrelativo(100);
        p.setEstado(true);
        return p;
    }
}
