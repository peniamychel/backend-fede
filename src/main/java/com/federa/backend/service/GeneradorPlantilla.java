package com.federa.backend.service;

import com.federa.backend.service.LectorPlanilla.Columna;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Arma la planilla de ejemplo que se descarga desde la app.
 * <p>
 * Recorre {@link Columna}, que es el mismo enum con el que
 * {@link LectorPlanilla} reconoce los encabezados al importar. Por eso la
 * plantilla entregada no puede quedar desincronizada de lo que el importador
 * acepta: si se agrega o se renombra una columna, esta plantilla la refleja
 * sin tocar nada acá.
 */
@Component
public class GeneradorPlantilla {

    public static final String NOMBRE_ARCHIVO = "plantilla-padron.xlsx";

    /** Segunda fila de ejemplo, para que se vea que los datos se repiten. */
    private static final String[] SEGUNDO_EJEMPLO = {
            "IVI", "IVIRGARZAMA", "LIBERTAD", "JUAN", "PEREZ", "8005906-1V", "75",
            "", "SIN SISTEMA", ""
    };

    public byte[] generar() {
        try (Workbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            hojaDatos(libro);
            hojaInstrucciones(libro);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la plantilla", e);
        }
    }

    private void hojaDatos(Workbook libro) {
        Sheet hoja = libro.createSheet("Padron");
        Columna[] columnas = Columna.values();

        CellStyle obligatoria = estiloEncabezado(libro, IndexedColors.LIGHT_ORANGE);
        CellStyle opcional = estiloEncabezado(libro, IndexedColors.GREY_25_PERCENT);
        CellStyle ejemplo = estiloEjemplo(libro);

        Row encabezado = hoja.createRow(0);
        for (int i = 0; i < columnas.length; i++) {
            var celda = encabezado.createCell(i);
            celda.setCellValue(columnas[i].getTitulo());
            celda.setCellStyle(columnas[i].esObligatoria() ? obligatoria : opcional);
        }

        Row primera = hoja.createRow(1);
        for (int i = 0; i < columnas.length; i++) {
            var celda = primera.createCell(i);
            celda.setCellValue(columnas[i].getEjemplo());
            celda.setCellStyle(ejemplo);
        }

        Row segunda = hoja.createRow(2);
        for (int i = 0; i < columnas.length && i < SEGUNDO_EJEMPLO.length; i++) {
            var celda = segunda.createCell(i);
            celda.setCellValue(SEGUNDO_EJEMPLO[i]);
            celda.setCellStyle(ejemplo);
        }

        for (int i = 0; i < columnas.length; i++) {
            hoja.autoSizeColumn(i);
            // autoSizeColumn deja las columnas pegadas al texto; un poco de aire
            // evita que los encabezados se vean cortados en Excel.
            hoja.setColumnWidth(i, Math.min(hoja.getColumnWidth(i) + 900, 12000));
        }
        hoja.createFreezePane(0, 1);
    }

    private void hojaInstrucciones(Workbook libro) {
        Sheet hoja = libro.createSheet("Instrucciones");

        Font negrita = libro.createFont();
        negrita.setBold(true);
        CellStyle titulo = libro.createCellStyle();
        titulo.setFont(negrita);

        int f = 0;
        f = escribir(hoja, f, "Cómo llenar esta planilla", titulo);
        f++;
        f = escribir(hoja, f, "1. Borrá las dos filas de ejemplo de la hoja «Padron» "
                + "antes de cargar tus datos.", null);
        f = escribir(hoja, f, "2. No cambies ni muevas la fila de encabezados.", null);
        f = escribir(hoja, f, "3. Una fila por productor.", null);
        f++;

        f = escribir(hoja, f, "Columnas obligatorias (naranja)", titulo);
        for (Columna c : Columna.values()) {
            if (c.esObligatoria()) {
                f = escribir(hoja, f, "   " + c.getTitulo(), null);
            }
        }
        f++;

        f = escribir(hoja, f, "Columnas opcionales (gris)", titulo);
        for (Columna c : Columna.values()) {
            if (!c.esObligatoria()) {
                f = escribir(hoja, f, "   " + c.getTitulo(), null);
            }
        }
        f++;

        f = escribir(hoja, f, "Detalles que conviene saber", titulo);
        f = escribir(hoja, f, "• La federación no va en la planilla: se elige en la app "
                + "al importar.", null);
        f = escribir(hoja, f, "• ABREVIATURA debe coincidir con la sigla registrada en la "
                + "central. El importador nunca crea centrales.", null);
        f = escribir(hoja, f, "• CLASIFICACION admite: SIN SISTEMA, SISTEMA, BLANCO, "
                + "FRACCIONADO, DETALLISTA o COMUNITARIO.", null);
        f = escribir(hoja, f, "• EXTENSION (A-H) es una referencia. La letra definitiva se "
                + "recalcula automáticamente y SISTEMA tiene prioridad.", null);
        f = escribir(hoja, f, "• Un guion « - » en C.I o N° LOTE se entiende como dato "
                + "ausente, igual que dejar la celda vacía.", null);
        f = escribir(hoja, f, "• Nombres y apellidos se guardan en MAYÚSCULAS y sin tildes; "
                + "no hace falta que los escribas así.", null);
        f = escribir(hoja, f, "• Las cédulas y los carnés pueden repetirse: el padrón real "
                + "tiene 27 y 208 repetidos, y no se rechazan.", null);
        f = escribir(hoja, f, "• En OBSERVACIONES podés poner varios motivos separados por "
                + "coma; cada uno se guarda por separado para poder resolverlos de a uno.", null);
        f = escribir(hoja, f, "• Si una central no existe, esas filas no se importan: primero "
                + "hay que crearla manualmente con su abreviatura.", null);
        f = escribir(hoja, f, "• Los sindicatos que no existan se muestran en una lista y solo "
                + "se crean después de que los apruebes.", null);
        f++;
        escribir(hoja, f, "Al subirla, la app primero analiza sin escribir nada. "
                + "Recién confirmás después de ver el resultado.", titulo);

        hoja.setColumnWidth(0, 24000);
    }

    private int escribir(Sheet hoja, int fila, String texto, CellStyle estilo) {
        var celda = hoja.createRow(fila).createCell(0);
        celda.setCellValue(texto);
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
        return fila + 1;
    }

    private CellStyle estiloEncabezado(Workbook libro, IndexedColors color) {
        Font fuente = libro.createFont();
        fuente.setBold(true);

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(color.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderBottom(BorderStyle.THIN);
        return estilo;
    }

    private CellStyle estiloEjemplo(Workbook libro) {
        Font fuente = libro.createFont();
        fuente.setItalic(true);
        fuente.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        return estilo;
    }
}
