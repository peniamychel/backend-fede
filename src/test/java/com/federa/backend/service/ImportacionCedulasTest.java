package com.federa.backend.service;

import com.federa.backend.dto.ImportacionResponse;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.federa.backend.model.enums.EstadoLote;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImportacionCedulasTest {
    private final ProductorRepository productores = mock(ProductorRepository.class);
    private final LoteRepository lotes = mock(LoteRepository.class);
    private final SindicatoRepository sindicatos = mock(SindicatoRepository.class);
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);
    private SimpleTransactionStatus estado;
    private ImportacionService servicio;

    @BeforeEach
    void preparar() {
        var federaciones = mock(FederacionService.class);
        var centrales = mock(CentralRepository.class);
        var numerador = mock(NumeradorPadron.class);
        var federacion = new Federacion();
        federacion.setId(1L);
        federacion.setNombre("CARRASCO TROPICAL");
        var central = new Central();
        central.setId(10L);
        central.setNombre("13 DE JUNIO");
        central.setFederacion(federacion);
        var sindicato = new Sindicato();
        sindicato.setId(20L);
        sindicato.setNombre("1RO DE MAYO");
        sindicato.setCentral(central);
        when(federaciones.buscar(1L)).thenReturn(federacion);
        when(centrales.findByFederacionIdOrderByNombreAsc(1L)).thenReturn(List.of(central));
        when(sindicatos.findByCentralIdOrderByNombreAsc(10L)).thenReturn(List.of(sindicato));
        when(numerador.siguiente(10L)).thenReturn(100);
        estado = new SimpleTransactionStatus();
        when(transacciones.getTransaction(any())).thenReturn(estado);
        servicio = new ImportacionService(new LectorPlanilla(), federaciones, centrales,
                sindicatos, productores, lotes, numerador, mock(LoteService.class), transacciones);
    }

    @Test
    void rechazaCedulaRegistradaAunqueElNombreYSindicatoSeanDiferentes() throws Exception {
        when(productores.findCedulasParaImportacion())
                .thenReturn(List.<Object[]>of(new Object[]{"123456", 800L}));
        var resultado = importar(true, false, "123456");
        assertThat(resultado.filasRechazadas()).isEqualTo(1);
        assertThat(resultado.productores()).isZero();
        assertThat(resultado.lotes()).isZero();
        assertThat(resultado.errores().get(0).mensaje()).contains("ID 800");
        verify(productores, never()).saveAll(any());
        verify(sindicatos, never()).save(any());
    }

    @Test
    void rechazaTodasLasFilasConCedulaRepetidaEnElExcel() throws Exception {
        var resultado = importar(true, false, "123456", "123456", "789012");
        assertThat(resultado.filasValidas()).isEqualTo(1);
        assertThat(resultado.filasRechazadas()).isEqualTo(2);
        assertThat(resultado.lotes()).isEqualTo(1);
        assertThat(resultado.errores()).allSatisfy(error -> {
            assertThat(error.columna()).isEqualTo("ci");
            assertThat(error.mensaje()).contains("filas 2, 3");
        });
        assertThat(estado.isRollbackOnly()).isTrue();
    }

    @Test
    void importarSoloValidasNuncaGuardaLasCedulasDuplicadasNiSusLotes() throws Exception {
        when(productores.findCedulasParaImportacion())
                .thenReturn(List.<Object[]>of(new Object[]{"111", 800L}));
        var resultado = importar(false, true, "111", "222", "222", "333");
        assertThat(resultado.simulacion()).isFalse();
        assertThat(resultado.productores()).isEqualTo(1);
        assertThat(resultado.filasRechazadas()).isEqualTo(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Productor>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(productores).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Productor::getCi).containsExactly("333");
        assertThat(resultado.lotes()).isEqualTo(1);
        assertThat(estado.isRollbackOnly()).isFalse();
    }

    @Test
    void sinIgnorarErroresAbortaTodaLaImportacion() throws Exception {
        var resultado = importar(false, false, "111", "111", "222");
        assertThat(resultado.simulacion()).isTrue();
        assertThat(estado.isRollbackOnly()).isTrue();
        verify(productores, never()).saveAll(any());
        verify(lotes, never()).saveAll(any());
    }

    @Test
    void comparaEspaciosYMayusculasTambienContraLaBase() throws Exception {
        when(productores.findCedulasParaImportacion())
                .thenReturn(List.<Object[]>of(new Object[]{" 123456 - 1v ", 800L}));
        var resultado = importar(true, false, "123456-1V", "987-2a", " 987 - 2A ");
        assertThat(resultado.filasRechazadas()).isEqualTo(3);
        assertThat(resultado.productores()).isZero();
    }

    @Test
    void cedulasAusentesNoSeConsideranDuplicadasYComplementosDistintosSeConservan() throws Exception {
        var resultado = importar(true, false, "", "-", "123456", "123456-1V", "123456-2V");
        assertThat(resultado.filasRechazadas()).isZero();
        assertThat(resultado.filasValidas()).isEqualTo(5);
        assertThat(ImportacionService.claveCedula("\u00a0123\u00a0- 1v "))
                .isEqualTo("123-1V");
        assertThat(ImportacionService.claveCedula("00123")).isEqualTo("00123");
    }

    @Test
    void repetirLaCargaReconsultaLasCedulasRegistradas() throws Exception {
        when(productores.findCedulasParaImportacion())
                .thenReturn(List.of())
                .thenReturn(List.<Object[]>of(new Object[]{"123456", 900L}));
        assertThat(importar(false, false, "123456").productores()).isEqualTo(1);
        var repetida = importar(false, true, "123456");
        assertThat(repetida.productores()).isZero();
        assertThat(repetida.filasRechazadas()).isEqualTo(1);
    }

    @Test
    void vuelveAValidarSiOtraAltaOcurreDespuesDeLaVistaPrevia() throws Exception {
        when(productores.findCedulasParaImportacion())
                .thenReturn(List.of())
                .thenReturn(List.<Object[]>of(new Object[]{"123456", 900L}));
        assertThat(importar(true, false, "123456").filasValidas()).isEqualTo(1);
        assertThat(importar(false, false, "123456").filasRechazadas()).isEqualTo(1);
        verify(productores, never()).saveAll(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SISTEMA", "BLANCO", "FRACCIONADO", "DETALLISTA", "COMUNITARIO"})
    void conservaClasificacionSinCrearUnLoteFicticio(String clasificacion) throws Exception {
        var resultado = importarFilas(false, false, clasificacion, "", "123456");
        assertThat(resultado.filasRechazadas()).isZero();
        assertThat(resultado.productores()).isEqualTo(1);
        assertThat(resultado.lotes()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Productor>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(productores).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(p -> {
            assertThat(p.getClasificacionPendiente()).isEqualTo(EstadoLote.desde(clasificacion));
            assertThat(p.getTenencias()).isEmpty();
        });
    }

    @Test
    void sinClasificacionYSinLoteNoInventaUnaClasificacion() throws Exception {
        var resultado = importarFilas(false, false, "", "", "123456");
        assertThat(resultado.productores()).isEqualTo(1);
        assertThat(resultado.lotes()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Productor>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(productores).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(p ->
                assertThat(p.getClasificacionPendiente()).isNull());
    }

    @Test
    void conNumeroLaClasificacionSeGuardaEnElLote() throws Exception {
        var resultado = importarFilas(false, false, "SISTEMA", "22", "123456");
        assertThat(resultado.lotes()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<com.federa.backend.model.Lote>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(lotes).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(l -> {
            assertThat(l.getNumero()).isEqualTo("22");
            assertThat(l.getEstadoLote()).isEqualTo(EstadoLote.CON_SISTEMA);
        });
    }

    private ImportacionResponse importar(boolean simular, boolean ignorar, String... cedulas)
            throws Exception {
        return importarFilas(simular, ignorar, null, null, cedulas);
    }

    private ImportacionResponse importarFilas(boolean simular, boolean ignorar,
            String clasificacion, String numero, String... cedulas)
            throws Exception {
        try (var libro = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("PADRON");
            var encabezado = hoja.createRow(0);
            String[] columnas = {"CENTRAL", "SINDICATO", "NOMBRES", "APELLIDOS", "C.I", "N° LOTE", "CLASIFICACION"};
            for (int i = 0; i < columnas.length; i++) encabezado.createCell(i).setCellValue(columnas[i]);
            for (int i = 0; i < cedulas.length; i++) {
                var fila = hoja.createRow(i + 1);
                fila.createCell(0).setCellValue("13 DE JUNIO");
                fila.createCell(1).setCellValue("1RO DE MAYO");
                fila.createCell(2).setCellValue("PERSONA " + i);
                fila.createCell(3).setCellValue("APELLIDO " + i);
                // Alterna celdas numéricas y de texto, como sucede en archivos reales.
                if (i % 2 == 0 && cedulas[i].matches("[1-9][0-9]{0,8}")) {
                    fila.createCell(4).setCellValue(Double.parseDouble(cedulas[i]));
                } else {
                    fila.createCell(4).setCellValue(cedulas[i]);
                }
                fila.createCell(5).setCellValue(numero == null ? String.valueOf(i + 1) : numero);
                if (clasificacion != null) fila.createCell(6).setCellValue(clasificacion);
            }
            libro.write(bytes);
            return servicio.importar(new ByteArrayInputStream(bytes.toByteArray()),
                    1L, simular, true, ignorar);
        }
    }
}
