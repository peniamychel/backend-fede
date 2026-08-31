package com.federa.backend.service;

import com.federa.backend.dto.LoteRequest;
import com.federa.backend.dto.LoteResponse;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.TenenciaLote;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.repository.LoteRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.TenenciaLoteRepository;
import com.federa.backend.repository.TenenciaSistemaRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoteCodigoCompartidoTest {

    private final LoteRepository lotes = mock(LoteRepository.class);
    private final TenenciaLoteRepository tenencias = mock(TenenciaLoteRepository.class);
    private final TenenciaSistemaRepository sistemas = mock(TenenciaSistemaRepository.class);
    private final ProductorRepository productores = mock(ProductorRepository.class);
    private final NumeradorPadron numerador = mock(NumeradorPadron.class);
    private final LoteService servicio = new LoteService(
            lotes,
            tenencias,
            sistemas,
            productores,
            mock(ProductorService.class),
            mock(SindicatoService.class),
            numerador);

    @Test
    void dosProductoresConservanSusCorrelativosYRecibenAyB() {
        Lote referencia = lote(10L);
        referencia.setExtension(ExtensionLote.A);
        Lote segundoLote = lote(11L);
        segundoLote.setExtension(ExtensionLote.B);
        Productor primero = productor(1L, 78);
        Productor segundo = productor(2L, 79);
        when(tenencias.findVigentesDelNumero(7L, "78"))
                .thenReturn(List.of(tenencia(1L, referencia, primero),
                        tenencia(2L, segundoLote, segundo)));

        servicio.recalcularCodigosDelGrupo(referencia);

        assertThat(primero.getCorrelativo()).isEqualTo(78);
        assertThat(segundo.getCorrelativo()).isEqualTo(79);
        assertThat(primero.getLetraCodigo()).isEqualTo("A");
        assertThat(segundo.getLetraCodigo()).isEqualTo("B");
    }

    @Test
    void laApiMuestraLaLetraEnElLoteYNoEnElCodigoPadron() {
        Federacion federacion = new Federacion();
        federacion.setNumero("2");
        Central central = new Central();
        central.setId(3L);
        central.setAbreviatura("13J");
        central.setFederacion(federacion);
        Sindicato sindicato = new Sindicato();
        sindicato.setId(7L);
        sindicato.setNombre("SINDICATO");
        sindicato.setCentral(central);
        Lote lote = new Lote();
        lote.setId(10L);
        lote.setNumero("22");
        lote.setSindicato(sindicato);
        Productor productor = productor(1L, 78);
        productor.setSindicato(sindicato);
        productor.setLetraCodigo("A");
        TenenciaLote tenencia = tenencia(1L, lote, productor);

        LoteResponse respuesta = LoteResponse.desde(lote, tenencia, null);

        assertThat(respuesta.codigo()).isEqualTo("22 A");
        assertThat(respuesta.tenedor().codigoPadron()).isEqualTo("2-13J-78");
        assertThat(respuesta.tenedor().letra()).isEqualTo("A");
    }

    @Test
    void productorConSistemaRecibeLaAPeseAHaberSidoAsignadoDespues() {
        Lote loteSinSistema = lote(10L);
        loteSinSistema.setEstadoLote(EstadoLote.SIN_SISTEMA);
        Lote loteConSistema = lote(11L);
        loteConSistema.setEstadoLote(EstadoLote.CON_SISTEMA);
        Productor carlos = productor(1L, 78);
        Productor maria = productor(2L, 79);
        when(tenencias.findVigentesDelNumero(7L, "78"))
                .thenReturn(List.of(
                        tenencia(1L, loteSinSistema, carlos),
                        tenencia(2L, loteConSistema, maria)));

        servicio.recalcularCodigosDelGrupo(loteSinSistema);

        assertThat(maria.getLetraCodigo()).isEqualTo("A");
        assertThat(carlos.getLetraCodigo()).isEqualTo("B");
        // La prioridad cambia solamente las letras. Cada productor conserva el
        // código único que recibió al registrarse.
        assertThat(maria.getCorrelativo()).isEqualTo(79);
        assertThat(carlos.getCorrelativo()).isEqualTo(78);
    }

    @Test
    void reconoceLasClasificacionesOficialesQueEnviaElFrontend() {
        assertThat(EstadoLote.desde("CON_SISTEMA")).isEqualTo(EstadoLote.CON_SISTEMA);
        assertThat(EstadoLote.desde("SIN_SISTEMA")).isEqualTo(EstadoLote.SIN_SISTEMA);
    }

    @Test
    void reparaClasificacionesOficialesGuardadasAntesDeLaCorreccion() {
        Lote conSistema = lote(10L);
        conSistema.setEstadoLote(EstadoLote.DESCONOCIDO);
        conSistema.setEstadoOriginal("CON_SISTEMA");
        Lote sinSistema = lote(11L);
        sinSistema.setEstadoLote(EstadoLote.DESCONOCIDO);
        sinSistema.setEstadoOriginal("SIN_SISTEMA");
        Lote realmenteDesconocido = lote(12L);
        realmenteDesconocido.setEstadoLote(EstadoLote.DESCONOCIDO);
        realmenteDesconocido.setEstadoOriginal("OTRO VALOR");
        when(lotes.findConEstadoDesconocido())
                .thenReturn(List.of(conSistema, sinSistema, realmenteDesconocido));

        int corregidos = servicio.normalizarClasificacionesExistentes();

        assertThat(corregidos).isEqualTo(2);
        assertThat(conSistema.getEstadoLote()).isEqualTo(EstadoLote.CON_SISTEMA);
        assertThat(sinSistema.getEstadoLote()).isEqualTo(EstadoLote.SIN_SISTEMA);
        assertThat(realmenteDesconocido.getEstadoLote()).isEqualTo(EstadoLote.DESCONOCIDO);
        verify(lotes).flush();
    }

    @Test
    void alQuedarUnoSoloDesapareceLaLetra() {
        Lote referencia = lote(10L);
        Productor unico = productor(1L, 78);
        unico.setLetraCodigo("A");
        when(tenencias.findVigentesDelNumero(7L, "78"))
                .thenReturn(List.of(tenencia(1L, referencia, unico)));

        servicio.recalcularCodigosDelGrupo(referencia);

        assertThat(unico.getLetraCodigo()).isNull();
        assertThat(unico.getCorrelativo()).isEqualTo(78);
    }

    @Test
    void noPermiteMasDeOchoProductoresEnElMismoNumero() {
        Lote referencia = lote(10L);
        List<TenenciaLote> grupo = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grupo.add(tenencia((long) i, lote(10L + i), productor((long) i, 78 + i)));
        }
        when(tenencias.findVigentesDelNumero(7L, "78")).thenReturn(grupo);

        assertThatThrownBy(() -> servicio.recalcularCodigosDelGrupo(referencia))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("máximo de 8")
                .hasMessageContaining("A-H");
    }

    @Test
    void normalizaLosGruposQueYaExistianAntesDeLaRegla() {
        Lote primeroLote = lote(10L);
        Lote segundoLote = lote(11L);
        Productor primero = productor(1L, 78);
        Productor segundo = productor(2L, 79);
        List<TenenciaLote> grupo = List.of(
                tenencia(1L, primeroLote, primero),
                tenencia(2L, segundoLote, segundo));
        when(tenencias.findVigentesConNumero()).thenReturn(grupo);
        when(tenencias.findVigentesDelNumero(7L, "78")).thenReturn(grupo);

        int normalizados = servicio.normalizarCodigosCompartidosExistentes();

        assertThat(normalizados).isEqualTo(1);
        assertThat(primero.getLetraCodigo()).isEqualTo("A");
        assertThat(segundo.getLetraCodigo()).isEqualTo("B");
        assertThat(primero.getCorrelativo()).isEqualTo(78);
        assertThat(segundo.getCorrelativo()).isEqualTo(79);
    }

    @Test
    void corrigeSoloBHEntreLosCorrelativosDuplicados() {
        Lote loteA = lote(10L);
        Lote loteB = lote(11L);
        Lote loteC = lote(12L);
        Sindicato sindicato = loteA.getSindicato();
        loteB.setSindicato(sindicato);
        loteC.setSindicato(sindicato);
        Productor a = productor(1L, 369);
        Productor b = productor(2L, 369);
        Productor c = productor(3L, 369);
        Productor sinLetra = productor(4L, 500);
        for (Productor productor : List.of(a, b, c, sinLetra)) {
            productor.setSindicato(sindicato);
        }
        a.setLetraCodigo("A");
        b.setLetraCodigo("B");
        c.setLetraCodigo("C");
        when(tenencias.findVigentesConNumero()).thenReturn(List.of(
                tenencia(1L, loteA, a), tenencia(2L, loteB, b), tenencia(3L, loteC, c)));
        when(productores.findBySindicatoCentralIdOrderByApellidosAscNombresAsc(3L))
                .thenReturn(List.of(a, b, c, sinLetra));
        when(numerador.siguiente(3L)).thenReturn(501);

        int primera = servicio.corregirCorrelativosDuplicadosDeLotesCompartidos();
        int segunda = servicio.corregirCorrelativosDuplicadosDeLotesCompartidos();

        assertThat(primera).isEqualTo(2);
        assertThat(segunda).isZero();
        assertThat(a.getCorrelativo()).isEqualTo(369);
        assertThat(b.getCorrelativo()).isEqualTo(501);
        assertThat(c.getCorrelativo()).isEqualTo(502);
        assertThat(sinLetra.getCorrelativo()).isEqualTo(500);
        verify(productores).flush();
    }

    @Test
    void cambiarNumeroRecalculaElGrupoAnteriorYElNuevo() {
        Lote movido = lote(10L);
        Lote anterior = lote(11L);
        Productor primero = productor(1L, 78);
        Productor segundo = productor(2L, 79);
        primero.setLetraCodigo("A");
        segundo.setLetraCodigo("B");
        segundo.setSindicato(movido.getSindicato());
        TenenciaLote tenenciaMovida = tenencia(2L, movido, segundo);

        when(lotes.findById(10L)).thenReturn(java.util.Optional.of(movido));
        when(tenencias.findByLoteIdAndVigenteIsTrue(10L))
                .thenReturn(java.util.Optional.of(tenenciaMovida));
        when(tenencias.findVigentesDelNumero(7L, "78"))
                .thenReturn(List.of(tenencia(1L, anterior, primero)));
        when(tenencias.findVigentesDelNumero(7L, "99"))
                .thenReturn(List.of(tenenciaMovida));
        servicio.actualizar(10L, new LoteRequest(
                "99", null, "SIN_SISTEMA", null, 7L, null, null));

        assertThat(movido.getNumero()).isEqualTo("99");
        assertThat(primero.getLetraCodigo()).isNull();
        assertThat(segundo.getLetraCodigo()).isNull();
        assertThat(segundo.getCorrelativo()).isEqualTo(79);
        verify(numerador, never()).siguiente(3L);
    }

    @Test
    void cambiarLaClasificacionASistemaReordenaLasLetras() {
        Lote primeroLote = lote(10L);
        primeroLote.setEstadoLote(EstadoLote.SIN_SISTEMA);
        Lote segundoLote = lote(11L);
        segundoLote.setEstadoLote(EstadoLote.SIN_SISTEMA);
        Productor carlos = productor(1L, 78);
        Productor maria = productor(2L, 79);
        carlos.setLetraCodigo("A");
        maria.setLetraCodigo("B");
        List<TenenciaLote> grupo = List.of(
                tenencia(1L, primeroLote, carlos),
                tenencia(2L, segundoLote, maria));
        when(lotes.findById(11L)).thenReturn(Optional.of(segundoLote));
        when(tenencias.findVigentesDelNumero(7L, "78")).thenReturn(grupo);

        servicio.actualizar(11L, new LoteRequest(
                "78", null, "CON_SISTEMA", null, 7L, null, null));

        assertThat(maria.getLetraCodigo()).isEqualTo("A");
        assertThat(carlos.getLetraCodigo()).isEqualTo("B");
        assertThat(carlos.getCorrelativo()).isEqualTo(78);
        assertThat(maria.getCorrelativo()).isEqualTo(79);
    }

    @Test
    void noEliminaUnaParcelaConProductorAsignado() {
        Lote lote = lote(10L);
        Productor productor = productor(1L, 78);
        productor.setNombres("ANA");
        productor.setApellidos("MAMANI");
        when(lotes.findById(10L)).thenReturn(Optional.of(lote));
        when(tenencias.findByLoteIdAndVigenteIsTrue(10L))
                .thenReturn(Optional.of(tenencia(1L, lote, productor)));

        assertThatThrownBy(() -> servicio.eliminar(10L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("está asignado a ANA MAMANI")
                .hasMessageContaining("Primero dejalo sin tenedor");
        verify(lotes, never()).delete(lote);
    }

    @Test
    void eliminaUnaParcelaSinProductorAsignado() {
        Lote lote = lote(10L);
        when(lotes.findById(10L)).thenReturn(Optional.of(lote));
        when(tenencias.findByLoteIdAndVigenteIsTrue(10L))
                .thenReturn(Optional.empty());
        when(sistemas.findByLoteIdAndVigenteIsTrue(10L))
                .thenReturn(Optional.empty());

        servicio.eliminar(10L);

        verify(lotes).delete(lote);
    }

    private Lote lote(Long id) {
        Central central = new Central();
        central.setId(3L);
        Sindicato sindicato = new Sindicato();
        sindicato.setId(7L);
        sindicato.setNombre("SINDICATO");
        sindicato.setCentral(central);
        Lote lote = new Lote();
        lote.setId(id);
        lote.setNumero("78");
        lote.setSindicato(sindicato);
        return lote;
    }

    private Productor productor(Long id, int correlativo) {
        Productor productor = new Productor();
        productor.setId(id);
        productor.setNombres("PRODUCTOR " + id);
        productor.setCorrelativo(correlativo);
        return productor;
    }

    private TenenciaLote tenencia(Long id, Lote lote, Productor productor) {
        TenenciaLote tenencia = new TenenciaLote();
        tenencia.setId(id);
        tenencia.setLote(lote);
        tenencia.setProductor(productor);
        tenencia.setVigente(true);
        return tenencia;
    }
}
