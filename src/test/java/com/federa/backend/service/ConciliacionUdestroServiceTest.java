package com.federa.backend.service;

import com.federa.backend.dto.AplicarConciliacionUdestroRequest;
import com.federa.backend.model.*;
import com.federa.backend.model.enums.*;
import com.federa.backend.repository.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import jakarta.persistence.EntityManager;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConciliacionUdestroServiceTest {

    private final FederacionRepository federaciones = mock(FederacionRepository.class);
    private final CentralRepository centrales = mock(CentralRepository.class);
    private final SindicatoRepository sindicatos = mock(SindicatoRepository.class);
    private final ProductorRepository productores = mock(ProductorRepository.class);
    private final TenenciaLoteRepository tenencias = mock(TenenciaLoteRepository.class);
    private final ImagenProductorRepository imagenes = mock(ImagenProductorRepository.class);
    private final ConciliacionUdestroRepository conciliaciones =
            mock(ConciliacionUdestroRepository.class);
    private final FilaConciliacionUdestroRepository filas =
            mock(FilaConciliacionUdestroRepository.class);
    private final NumeradorPadron numerador = mock(NumeradorPadron.class);
    private final LoteService loteService = mock(LoteService.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private ConciliacionUdestroService servicio;
    private Federacion federacion;
    private Central central;
    private Sindicato sindicato;

    @BeforeEach
    void preparar() {
        federacion = new Federacion();
        federacion.setId(1L);
        federacion.setNombre("CARRASCO TROPICAL");
        central = new Central();
        central.setId(10L);
        central.setNombre("13 DE JUNIO");
        central.setFederacion(federacion);
        sindicato = new Sindicato();
        sindicato.setId(20L);
        sindicato.setNombre("1RO DE MAYO");
        sindicato.setCentral(central);

        when(federaciones.findByNombreIgnoreCase("CARRASCO TROPICAL"))
                .thenReturn(Optional.of(federacion));
        when(centrales.findByFederacionIdOrderByNombreAsc(1L)).thenReturn(List.of(central));
        when(sindicatos.findByCentralIdOrderByNombreAsc(10L)).thenReturn(List.of(sindicato));
        when(conciliaciones.save(any())).thenAnswer(inv -> {
            ConciliacionUdestro c = inv.getArgument(0);
            c.setId(90L);
            c.setCreatedAt(LocalDateTime.of(2026, 9, 9, 12, 0));
            return c;
        });
        servicio = new ConciliacionUdestroService(new LectorPlanilla(), federaciones,
                centrales, sindicatos, productores, tenencias, imagenes, conciliaciones,
                filas, numerador, loteService, entityManager);
    }

    @Test
    void analizaUnaMuestraSinModificarElPadron() throws Exception {
        Productor coincide = productor(1L, "ANA", "PÉREZ", "111", EstadoLote.BLANCO);
        Productor conflicto = productor(2L, "JUAN", "RAMOS", "222", EstadoLote.CON_SISTEMA);
        Productor ausenteSistema = productor(3L, "LUIS", "MAMANI", "333",
                EstadoLote.CON_SISTEMA);
        Productor ausenteFraccionado = productor(4L, "ROSA", "QUISPE", "444",
                EstadoLote.FRACCIONADO);
        Productor ausenteDesconocido = productor(5L, "MARTA", "LOPEZ", "666",
                EstadoLote.DESCONOCIDO);
        Productor ausenteBlanco = productor(6L, "PEDRO", "ROJAS", "777",
                EstadoLote.BLANCO);
        when(productores.findAllParaConciliacionUdestro()).thenReturn(List.of(
                coincide, conflicto, ausenteSistema, ausenteFraccionado,
                ausenteDesconocido, ausenteBlanco));

        var resumen = servicio.analizar(planilla(
                fila("ANA", "PÉREZ", "111"),
                fila("MARÍA", "CONDORI", "222"),
                fila("NUEVA", "PRODUCTORA", "555")), "muestra.xlsx");

        assertThat(resumen.filasExcel()).isEqualTo(3);
        assertThat(resumen.altasSistema()).isEqualTo(1);
        assertThat(resumen.cambiosASistema()).isEqualTo(1);
        assertThat(resumen.observadosPorIdentidad()).isEqualTo(1);
        assertThat(resumen.cambiosABlanco()).isEqualTo(2);
        assertThat(resumen.conservados()).isEqualTo(2);
        assertThat(resumen.conflictos()).isZero();
        assertThat(resumen.conflictosPendientes()).isZero();
        assertThat(resumen.errores()).isZero();
        assertThat(resumen.listaParaAplicar()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<FilaConciliacionUdestro>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(filas).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(FilaConciliacionUdestro::getAccion)
                .containsExactlyInAnyOrder(
                        AccionConciliacionUdestro.CAMBIAR_A_SISTEMA,
                        AccionConciliacionUdestro.OBSERVAR_Y_CAMBIAR_A_SISTEMA,
                        AccionConciliacionUdestro.ALTA_SISTEMA,
                        AccionConciliacionUdestro.CAMBIAR_A_BLANCO,
                        AccionConciliacionUdestro.CAMBIAR_A_BLANCO,
                        AccionConciliacionUdestro.CONSERVAR,
                        AccionConciliacionUdestro.CONSERVAR);
        assertThat(ausenteFraccionado.getClasificacionPendiente())
                .isEqualTo(EstadoLote.FRACCIONADO);
        verify(productores, never()).save(any());
        verify(productores, never()).flush();
    }

    @Test
    void aplicaClasificacionesYAltaSinTocarLaRevisionSieExistente() {
        Productor entra = productor(1L, "ANA", "PÉREZ", "111", EstadoLote.BLANCO);
        entra.setRevisionSiePendiente(false);
        entra.setRevisionSieEstado(EstadoRevisionSieProductor.VERIFICADO);
        Productor sale = productor(3L, "LUIS", "MAMANI", "333", EstadoLote.SIN_SISTEMA);
        sale.setRevisionSiePendiente(true);

        ConciliacionUdestro conciliacion = new ConciliacionUdestro();
        conciliacion.setId(90L);
        conciliacion.setFederacion(federacion);
        conciliacion.setNombreArchivo("muestra.xlsx");
        conciliacion.setSha256("0".repeat(64));
        conciliacion.setFilasExcel(2);
        conciliacion.setFase(EstadoConciliacionUdestro.BORRADOR);
        conciliacion.setCreatedAt(LocalDateTime.of(2026, 9, 9, 12, 0));

        FilaConciliacionUdestro aSistema = propuesta(conciliacion,
                AccionConciliacionUdestro.CAMBIAR_A_SISTEMA, entra, 2, "111");
        FilaConciliacionUdestro alta = propuesta(conciliacion,
                AccionConciliacionUdestro.ALTA_SISTEMA, null, 3, "555");
        alta.setCentralDestinoId(10L);
        alta.setCentralNombre("13 DE JUNIO");
        alta.setSindicatoDestinoId(20L);
        alta.setSindicatoNombre("1RO DE MAYO");
        alta.setNombresUdestro("NUEVA");
        alta.setApellidosUdestro("PRODUCTORA");
        FilaConciliacionUdestro aBlanco = propuesta(conciliacion,
                AccionConciliacionUdestro.CAMBIAR_A_BLANCO, sale, null, "333");

        when(conciliaciones.findByIdParaActualizar(90L)).thenReturn(Optional.of(conciliacion));
        when(filas.findByConciliacionIdOrderByIdAsc(90L))
                .thenReturn(List.of(aSistema, alta, aBlanco));
        when(productores.findAllById(any())).thenReturn(List.of(entra, sale));
        when(productores.findCedulasParaImportacion()).thenReturn(List.of(
                new Object[]{"111", 1L}, new Object[]{"333", 3L}));
        when(tenencias.findVigentesDeProductores(anyList())).thenReturn(List.of());
        when(numerador.siguiente(10L)).thenReturn(100);
        AtomicLong nuevoId = new AtomicLong(50);
        when(productores.save(any())).thenAnswer(inv -> {
            Productor p = inv.getArgument(0);
            p.setId(nuevoId.getAndIncrement());
            return p;
        });

        var resultado = servicio.aplicar(90L,
                new AplicarConciliacionUdestroRequest(false));

        assertThat(resultado.estado()).isEqualTo(EstadoConciliacionUdestro.APLICADA);
        assertThat(entra.getClasificacionPendiente()).isEqualTo(EstadoLote.CON_SISTEMA);
        assertThat(entra.getRevisionSieEstado()).isEqualTo(EstadoRevisionSieProductor.VERIFICADO);
        assertThat(entra.isRevisionSiePendiente()).isFalse();
        assertThat(sale.getClasificacionPendiente()).isEqualTo(EstadoLote.BLANCO);
        assertThat(sale.isRevisionSiePendiente()).isTrue();

        ArgumentCaptor<Productor> nuevo = ArgumentCaptor.forClass(Productor.class);
        verify(productores).save(nuevo.capture());
        assertThat(nuevo.getValue().getClasificacionPendiente())
                .isEqualTo(EstadoLote.CON_SISTEMA);
        assertThat(nuevo.getValue().isRevisionSiePendiente()).isTrue();
        assertThat(nuevo.getValue().getCorrelativo()).isEqualTo(100);
    }

    @Test
    void diferenciaMenorAlCincuentaObservaYClasificaSinCambiarSieNiIdentidad()
            throws Exception {
        Productor actual = productor(2L, "JUAN", "RAMOS", "222", EstadoLote.BLANCO);
        actual.setRevisionSiePendiente(false);
        actual.setRevisionSieEstado(EstadoRevisionSieProductor.VERIFICADO);
        when(productores.findAllParaConciliacionUdestro()).thenReturn(List.of(actual));

        servicio.analizar(planilla(fila("MARÍA", "CONDORI", "222")), "muestra.xlsx");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<FilaConciliacionUdestro>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(filas).saveAll(captor.capture());
        FilaConciliacionUdestro propuesta = captor.getValue().iterator().next();
        assertThat(propuesta.getAccion())
                .isEqualTo(AccionConciliacionUdestro.OBSERVAR_Y_CAMBIAR_A_SISTEMA);
        assertThat(propuesta.getSimilitudNombre()).isLessThan(50);

        ConciliacionUdestro conciliacion = propuesta.getConciliacion();
        when(conciliaciones.findByIdParaActualizar(90L)).thenReturn(Optional.of(conciliacion));
        when(filas.findByConciliacionIdOrderByIdAsc(90L)).thenReturn(List.of(propuesta));
        when(productores.findAllById(any())).thenReturn(List.of(actual));
        when(productores.findCedulasParaImportacion())
                .thenReturn(List.<Object[]>of(new Object[]{"222", 2L}));
        when(tenencias.findVigentesDeProductores(anyList())).thenReturn(List.of());

        servicio.aplicar(90L, new AplicarConciliacionUdestroRequest(false));

        assertThat(actual.getNombres()).isEqualTo("JUAN");
        assertThat(actual.getApellidos()).isEqualTo("RAMOS");
        assertThat(actual.getClasificacionPendiente()).isEqualTo(EstadoLote.CON_SISTEMA);
        assertThat(actual.getObservacionManual())
                .contains("CI 222", "UDESTRO indica: MARÍA CONDORI");
        assertThat(actual.isRevisionSiePendiente()).isFalse();
        assertThat(actual.getRevisionSieEstado())
                .isEqualTo(EstadoRevisionSieProductor.VERIFICADO);
    }

    @Test
    void diferenciaLeveConservaIdentidadActualYNoGeneraConflicto() throws Exception {
        Productor actual = productor(2L, "MARÍA ELENA", "PÉREZ ROJAS", "222",
                EstadoLote.BLANCO);
        when(productores.findAllParaConciliacionUdestro()).thenReturn(List.of(actual));

        var resumen = servicio.analizar(
                planilla(fila("MARIA", "PEREZ ROJAS", "222")), "muestra.xlsx");

        assertThat(resumen.cambiosASistema()).isEqualTo(1);
        assertThat(resumen.observadosPorIdentidad()).isZero();
        assertThat(resumen.conflictos()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<FilaConciliacionUdestro>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(filas).saveAll(captor.capture());
        FilaConciliacionUdestro propuesta = captor.getValue().iterator().next();
        assertThat(propuesta.getSimilitudNombre()).isGreaterThanOrEqualTo(50);
        assertThat(propuesta.getMotivo()).contains("Coincidencia de nombre");
    }

    private Productor productor(long id, String nombres, String apellidos, String ci,
                                EstadoLote clasificacion) {
        Productor p = new Productor();
        p.setId(id);
        p.setNombres(nombres);
        p.setApellidos(apellidos);
        p.setCi(ci);
        p.setSindicato(sindicato);
        p.setClasificacionPendiente(clasificacion);
        p.setUpdatedAt(LocalDateTime.of(2026, 9, 9, 10, 0));
        return p;
    }

    private FilaConciliacionUdestro propuesta(ConciliacionUdestro conciliacion,
            AccionConciliacionUdestro accion, Productor p, Integer fila, String ci) {
        FilaConciliacionUdestro propuesta = new FilaConciliacionUdestro();
        propuesta.setConciliacion(conciliacion);
        propuesta.setAccion(accion);
        propuesta.setNumeroFila(fila);
        propuesta.setCi(ci);
        if (p != null) {
            propuesta.setProductorId(p.getId());
            propuesta.setProductorActualizadoEn(p.getUpdatedAt());
            propuesta.setClasificacionAnterior(p.getClasificacionPendiente());
            if (fila != null) propuesta.setSimilitudNombre(100);
        }
        return propuesta;
    }

    private String[] fila(String nombres, String apellidos, String ci) {
        return new String[]{nombres, apellidos, ci};
    }

    private byte[] planilla(String[]... datos) throws Exception {
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Hoja1");
            var encabezado = hoja.createRow(0);
            String[] columnas = {"CENTRAL", "SINDICATO", "APELLIDOS", "NOMBRES", "C.I."};
            for (int i = 0; i < columnas.length; i++) encabezado.createCell(i).setCellValue(columnas[i]);
            for (int i = 0; i < datos.length; i++) {
                var fila = hoja.createRow(i + 1);
                fila.createCell(0).setCellValue("13 DE JUNIO");
                fila.createCell(1).setCellValue("1RO DE MAYO");
                fila.createCell(2).setCellValue(datos[i][1]);
                fila.createCell(3).setCellValue(datos[i][0]);
                fila.createCell(4).setCellValue(datos[i][2]);
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
