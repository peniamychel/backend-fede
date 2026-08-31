package com.federa.backend.service;

import com.federa.backend.exception.PlanillaInvalidaException;
import com.federa.backend.util.Textos;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lee la planilla del padrón y la convierte en filas de texto plano.
 * <p>
 * Solo lee y ubica columnas: no valida reglas del padrón ni toca la base. Eso
 * queda para {@link ImportacionService}, que es quien sabe de jerarquía.
 */
@Component
public class LectorPlanilla {

    /**
     * Todas las celdas se leen con esto y no con {@code getNumericCellValue()}.
     * <p>
     * Es el error clásico de las importaciones de Excel: las cédulas y los
     * números de lote suelen venir como celdas numéricas, y leerlas como número
     * devuelve {@code 1226.0}, que terminaría guardado tal cual. El
     * DataFormatter entrega lo que el usuario ve en la celda.
     */
    private static final DataFormatter FORMATO = new DataFormatter();

    /**
     * Columnas que la importación sabe interpretar.
     * <p>
     * Cada una lleva además el encabezado y el ejemplo con que aparece en la
     * plantilla descargable. Están acá y no en el generador a propósito: así la
     * plantilla que se entrega y las columnas que se aceptan salen de la misma
     * fuente y no pueden desincronizarse.
     */
    public enum Columna {
        ABREVIATURA("ABREVIATURA", false, "IVI", "ABREVIATURA", "ABREV", "SIGLA"),
        CENTRAL("CENTRAL", true, "IVIRGARZAMA", "CENTRAL"),
        SINDICATO("SINDICATO", true, "LIBERTAD", "SINDICATO"),
        NOMBRES("NOMBRES", true, "CONSTANTINA", "NOMBRES", "NOMBRE"),
        APELLIDOS("APELLIDOS", false, "HINOJOSA LA FUENTE", "APELLIDOS", "APELLIDO"),
        CI("C.I", false, "913516", "CI", "CEDULA", "CEDULADEIDENTIDAD", "CARNETIDENTIDAD"),
        LOTE("N° LOTE", false, "74", "NLOTE", "NROLOTE", "NUMLOTE", "NUMEROLOTE", "LOTE"),
        EXTENSION("EXTENSION", false, "A", "EXTENSION", "EXT", "LETRA"),
        CLASIFICACION("CLASIFICACION", false, "SISTEMA", "CLASIFICACION",
                "ESTADOLOTE", "ESTADODELLOTE"),
        OBSERVACIONES("OBSERVACIONES", false, "falta foto, el nombre no coincide con el CI",
                "OBSERVACIONES", "OBSERVACION", "OBS", "OBJ");

        private final String titulo;
        private final boolean obligatoria;
        private final String ejemplo;
        private final Set<String> sinonimos;

        Columna(String titulo, boolean obligatoria, String ejemplo, String... sinonimos) {
            this.titulo = titulo;
            this.obligatoria = obligatoria;
            this.ejemplo = ejemplo;
            this.sinonimos = Set.of(sinonimos);
        }

        /** Encabezado tal como se escribe en la plantilla descargable. */
        public String getTitulo() {
            return titulo;
        }

        public boolean esObligatoria() {
            return obligatoria;
        }

        public String getEjemplo() {
            return ejemplo;
        }
    }

    /**
     * Una fila de datos, todavía en crudo. {@code numero} es el que muestra
     * Excel, empezando en 1.
     */
    public record Fila(
            int numero,
            String central,
            String abreviatura,
            String sindicato,
            String nombres,
            String apellidos,
            String ci,
            String numeroLote,
            String extension,
            String clasificacion,
            String observaciones
    ) {
    }

    public List<Fila> leer(InputStream entrada) {
        try (Workbook libro = WorkbookFactory.create(entrada)) {
            if (libro.getNumberOfSheets() == 0) {
                throw new PlanillaInvalidaException("El archivo no tiene ninguna hoja.");
            }
            Sheet hoja = libro.getSheetAt(0);
            FormulaEvaluator evaluador = libro.getCreationHelper().createFormulaEvaluator();

            Row encabezado = hoja.getRow(hoja.getFirstRowNum());
            if (encabezado == null) {
                throw new PlanillaInvalidaException("La primera hoja está vacía.");
            }

            Map<Columna, Integer> posiciones = ubicarColumnas(encabezado, evaluador);
            return extraerFilas(hoja, evaluador, posiciones);

        } catch (IOException e) {
            throw new PlanillaInvalidaException(
                    "No se pudo leer el archivo: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (e instanceof PlanillaInvalidaException planilla) {
                throw planilla;
            }
            // POI lanza sus propias excepciones cuando el archivo no es un Excel.
            throw new PlanillaInvalidaException(
                    "El archivo no parece un Excel válido: " + e.getMessage(), e);
        }
    }

    private Map<Columna, Integer> ubicarColumnas(Row encabezado, FormulaEvaluator evaluador) {
        Map<Columna, Integer> posiciones = new EnumMap<>(Columna.class);
        List<String> encontrados = new ArrayList<>();

        for (int i = encabezado.getFirstCellNum(); i < encabezado.getLastCellNum(); i++) {
            String texto = valor(encabezado.getCell(i), evaluador);
            if (texto == null) {
                continue;
            }
            encontrados.add(texto);
            String clave = clave(texto);
            for (Columna columna : Columna.values()) {
                // La primera aparición gana: si la planilla repite un
                // encabezado, la de más a la izquierda es la buena.
                if (columna.sinonimos.contains(clave) && !posiciones.containsKey(columna)) {
                    posiciones.put(columna, i);
                }
            }
        }

        List<String> faltantes = new ArrayList<>();
        for (Columna columna : Columna.values()) {
            if (columna.esObligatoria() && !posiciones.containsKey(columna)) {
                faltantes.add(columna.name());
            }
        }
        if (!faltantes.isEmpty()) {
            throw new PlanillaInvalidaException(
                    "Faltan columnas obligatorias en la primera fila: " + String.join(", ", faltantes)
                            + ". Encabezados encontrados: " + String.join(" | ", encontrados));
        }
        return posiciones;
    }

    private List<Fila> extraerFilas(Sheet hoja, FormulaEvaluator evaluador,
                                    Map<Columna, Integer> posiciones) {
        List<Fila> filas = new ArrayList<>();

        for (int i = hoja.getFirstRowNum() + 1; i <= hoja.getLastRowNum(); i++) {
            Row fila = hoja.getRow(i);
            if (fila == null) {
                continue;
            }

            String central = celda(fila, posiciones, Columna.CENTRAL, evaluador);
            String abreviatura = celda(fila, posiciones, Columna.ABREVIATURA, evaluador);
            String sindicato = celda(fila, posiciones, Columna.SINDICATO, evaluador);
            String nombres = celda(fila, posiciones, Columna.NOMBRES, evaluador);
            String apellidos = celda(fila, posiciones, Columna.APELLIDOS, evaluador);
            String ci = celda(fila, posiciones, Columna.CI, evaluador);
            String lote = celda(fila, posiciones, Columna.LOTE, evaluador);
            String extension = celda(fila, posiciones, Columna.EXTENSION, evaluador);
            String clasificacion = celda(fila, posiciones, Columna.CLASIFICACION, evaluador);
            String observaciones = celda(fila, posiciones, Columna.OBSERVACIONES, evaluador);

            // Las filas totalmente vacías se saltean sin ruido: un .xlsx suele
            // arrastrar cientos al final y no son errores del usuario.
            if (central == null && abreviatura == null && sindicato == null && nombres == null
                    && apellidos == null && ci == null && lote == null && extension == null
                    && clasificacion == null && observaciones == null) {
                continue;
            }

            // i es base cero y Excel numera desde 1.
            filas.add(new Fila(i + 1, central, abreviatura, sindicato, nombres, apellidos, ci,
                    lote, extension, clasificacion, observaciones));
        }
        return filas;
    }

    private String celda(Row fila, Map<Columna, Integer> posiciones, Columna columna,
                         FormulaEvaluator evaluador) {
        Integer indice = posiciones.get(columna);
        if (indice == null) {
            return null;
        }
        return valor(fila.getCell(indice), evaluador);
    }

    /** Texto visible de la celda, o null si está vacía. */
    private String valor(Cell celda, FormulaEvaluator evaluador) {
        if (celda == null) {
            return null;
        }
        String texto = FORMATO.formatCellValue(celda, evaluador).trim();
        return texto.isEmpty() ? null : texto;
    }

    /**
     * Clave de comparación de encabezados: sin tildes, en mayúsculas y sin nada
     * que no sea letra o dígito, para que "N° LOTE", "Nro. Lote" y "numero lote"
     * caigan en el mismo lugar.
     */
    private String clave(String encabezado) {
        String normalizado = Textos.normalizar(encabezado);
        return normalizado == null ? "" : normalizado.replaceAll("[^A-Z0-9]", "");
    }
}
