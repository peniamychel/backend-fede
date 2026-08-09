package com.federa.backend.service;

import com.federa.backend.service.LectorPlanilla.Columna;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneradorPlantillaTest {

    private final GeneradorPlantilla generador = new GeneradorPlantilla();
    private final LectorPlanilla lector = new LectorPlanilla();

    @Test
    @DisplayName("la plantilla que se descarga la acepta el propio importador")
    void laPlantillaSeImporta() {
        byte[] plantilla = generador.generar();

        // Esta es la prueba que importa: si el generador y el lector se
        // desincronizaran, acá saltaría un PlanillaInvalidaException por
        // columnas faltantes.
        List<LectorPlanilla.Fila> filas =
                lector.leer(new ByteArrayInputStream(plantilla));

        assertThat(filas).hasSize(2);

        LectorPlanilla.Fila primera = filas.get(0);
        assertThat(primera.central()).isEqualTo(Columna.CENTRAL.getEjemplo());
        assertThat(primera.sindicato()).isEqualTo(Columna.SINDICATO.getEjemplo());
        assertThat(primera.nombres()).isEqualTo(Columna.NOMBRES.getEjemplo());
        assertThat(primera.ci()).isEqualTo(Columna.CI.getEjemplo());
        assertThat(primera.numeroLote()).isEqualTo(Columna.LOTE.getEjemplo());
    }

    @Test
    @DisplayName("los encabezados salen del enum, no de una lista aparte")
    void encabezadosDesdeElEnum() throws IOException {
        try (Workbook libro = WorkbookFactory.create(
                new ByteArrayInputStream(generador.generar()))) {

            Sheet hoja = libro.getSheetAt(0);
            var encabezado = hoja.getRow(0);

            Columna[] columnas = Columna.values();
            // getLastCellNum devuelve short, y AssertJ compara también el tipo:
            // sin el cast, 7 short no es igual a 7 int.
            assertThat((int) encabezado.getLastCellNum()).isEqualTo(columnas.length);
            for (int i = 0; i < columnas.length; i++) {
                assertThat(encabezado.getCell(i).getStringCellValue())
                        .isEqualTo(columnas[i].getTitulo());
            }
        }
    }

    @Test
    @DisplayName("trae una hoja de instrucciones aparte, que el lector ignora")
    void hojaDeInstrucciones() throws IOException {
        try (Workbook libro = WorkbookFactory.create(
                new ByteArrayInputStream(generador.generar()))) {

            assertThat(libro.getNumberOfSheets()).isEqualTo(2);
            assertThat(libro.getSheetAt(0).getSheetName()).isEqualTo("Padron");
            assertThat(libro.getSheetAt(1).getSheetName()).isEqualTo("Instrucciones");
        }
        // El lector solo mira la primera hoja, así que las instrucciones no
        // pueden colarse como datos.
        assertThat(lector.leer(new ByteArrayInputStream(generador.generar())))
                .hasSize(2);
    }
}
