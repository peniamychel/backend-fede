package com.federa.backend.service;

import com.federa.backend.exception.PlanillaInvalidaException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del lector de planillas. No levantan el contexto de Spring ni tocan
 * la base: arman un .xlsx en memoria y comprueban qué sale.
 */
class LectorPlanillaTest {

    private final LectorPlanilla lector = new LectorPlanilla();

    @Test
    @DisplayName("una cédula cargada como número no se lee como 1226.0")
    void cedulaNumerica() {
        InputStream planilla = construir(hoja -> {
            encabezados(hoja, "CENTRAL", "SINDICATO", "NOMBRES", "C.I");
            Row fila = hoja.createRow(1);
            fila.createCell(0).setCellValue("IVIRGARZAMA");
            fila.createCell(1).setCellValue("LIBERTAD");
            fila.createCell(2).setCellValue("CONSTANTINA");
            // Celda numérica de verdad: es como Excel guarda una cédula tecleada
            // sin formato de texto, y es donde se rompen casi todas las
            // importaciones.
            fila.createCell(3).setCellValue(1226);
        });

        List<LectorPlanilla.Fila> filas = lector.leer(planilla);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).ci()).isEqualTo("1226");
    }

    @Test
    @DisplayName("los encabezados se reconocen sin importar tildes, puntos ni mayúsculas")
    void encabezadosTolerantes() {
        InputStream planilla = construir(hoja -> {
            encabezados(hoja, "Central", "Abrev.", "sindicato", "Nombres", "Apellidos",
                    "C.I.", "N° LOTE", "Ext.", "Clasificación", "Observaciones");
            Row fila = hoja.createRow(1);
            fila.createCell(0).setCellValue("1RO MAYO");
            fila.createCell(1).setCellValue("1MO");
            fila.createCell(2).setCellValue("1RO MAYO");
            fila.createCell(3).setCellValue("JOAQUI");
            fila.createCell(4).setCellValue("ROBLES");
            fila.createCell(5).setCellValue("913516");
            fila.createCell(6).setCellValue("74");
            fila.createCell(7).setCellValue("B");
            fila.createCell(8).setCellValue("Sistema");
            fila.createCell(9).setCellValue("falta foto");
        });

        LectorPlanilla.Fila fila = lector.leer(planilla).get(0);

        assertThat(fila.central()).isEqualTo("1RO MAYO");
        assertThat(fila.abreviatura()).isEqualTo("1MO");
        assertThat(fila.apellidos()).isEqualTo("ROBLES");
        assertThat(fila.ci()).isEqualTo("913516");
        assertThat(fila.numeroLote()).isEqualTo("74");
        assertThat(fila.extension()).isEqualTo("B");
        assertThat(fila.clasificacion()).isEqualTo("Sistema");
        assertThat(fila.observaciones()).isEqualTo("falta foto");
    }

    @Test
    @DisplayName("el número de fila es el que muestra Excel, no el índice")
    void numeroDeFilaComoEnExcel() {
        InputStream planilla = construir(hoja -> {
            encabezados(hoja, "CENTRAL", "SINDICATO", "NOMBRES");
            Row fila = hoja.createRow(1);
            fila.createCell(0).setCellValue("CENTRAL A");
            fila.createCell(1).setCellValue("SINDICATO A");
            fila.createCell(2).setCellValue("PRIMERO");
        });

        // Encabezado en la fila 1 de Excel, primer dato en la 2.
        assertThat(lector.leer(planilla).get(0).numero()).isEqualTo(2);
    }

    @Test
    @DisplayName("las filas en blanco del final no cuentan como datos")
    void filasVaciasSalteadas() {
        InputStream planilla = construir(hoja -> {
            encabezados(hoja, "CENTRAL", "SINDICATO", "NOMBRES");
            Row fila = hoja.createRow(1);
            fila.createCell(0).setCellValue("CENTRAL A");
            fila.createCell(1).setCellValue("SINDICATO A");
            fila.createCell(2).setCellValue("PRIMERO");
            // Tres filas creadas pero vacías, como las que arrastra cualquier
            // .xlsx donde alguien tocó celdas y las borró.
            hoja.createRow(2);
            hoja.createRow(3).createCell(0).setCellValue("   ");
            hoja.createRow(4);
        });

        assertThat(lector.leer(planilla)).hasSize(1);
    }

    @Test
    @DisplayName("si falta una columna obligatoria dice cuál y qué encabezados encontró")
    void columnaObligatoriaFaltante() {
        InputStream planilla = construir(hoja ->
                encabezados(hoja, "CENTRAL", "NOMBRES", "C.I"));

        assertThatThrownBy(() -> lector.leer(planilla))
                .isInstanceOf(PlanillaInvalidaException.class)
                .hasMessageContaining("SINDICATO")
                .hasMessageContaining("CENTRAL");
    }

    @Test
    @DisplayName("un archivo que no es Excel da un error claro, no una excepción cruda de POI")
    void archivoQueNoEsExcel() {
        InputStream basura = new ByteArrayInputStream("esto no es un xlsx".getBytes());

        assertThatThrownBy(() -> lector.leer(basura))
                .isInstanceOf(PlanillaInvalidaException.class);
    }

    // ---------- utilidades ----------

    private interface Armado {
        void aplicar(Sheet hoja);
    }

    private InputStream construir(Armado armado) {
        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            armado.aplicar(libro.createSheet("Padron"));
            libro.write(salida);
            return new ByteArrayInputStream(salida.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo armar la planilla de prueba", e);
        }
    }

    private void encabezados(Sheet hoja, String... nombres) {
        Row fila = hoja.createRow(0);
        for (int i = 0; i < nombres.length; i++) {
            fila.createCell(i).setCellValue(nombres[i]);
        }
    }
}
