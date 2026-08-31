package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.ImportacionResponse;
import com.federa.backend.exception.PlanillaInvalidaException;
import com.federa.backend.service.GeneradorPlantilla;
import com.federa.backend.service.ImportacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping(ApiRutas.V1 + "/importaciones")
@Tag(name = "Importaciones", description =
        "Carga masiva del padrón desde la planilla MATRIX. El flujo son dos llamadas: "
        + "una simulada que devuelve el informe sin tocar la base, y otra real que confirma.")
public class ImportacionController {

    private static final String TIPO_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportacionService importacionService;
    private final GeneradorPlantilla generadorPlantilla;

    public ImportacionController(ImportacionService importacionService,
                                 GeneradorPlantilla generadorPlantilla) {
        this.importacionService = importacionService;
        this.generadorPlantilla = generadorPlantilla;
    }

    /**
     * Plantilla vacía con los encabezados que espera la importación.
     * <p>
     * Se genera a partir del mismo enum de columnas que usa el lector, así que
     * siempre coincide con lo que el endpoint de importación acepta.
     */
    @GetMapping(value = "/plantilla", produces = TIPO_XLSX)
    @Operation(summary = "Descarga la planilla de ejemplo",
            description = "Devuelve un .xlsx con la fila de encabezados, dos filas de ejemplo "
                    + "y una hoja de instrucciones. Las columnas obligatorias van resaltadas.")
    public ResponseEntity<byte[]> descargarPlantilla() {
        byte[] contenido = generadorPlantilla.generar();

        return ResponseEntity.ok()
                // attachment y no inline: el navegador tiene que descargarlo,
                // no intentar mostrarlo.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + GeneradorPlantilla.NOMBRE_ARCHIVO + "\"")
                .header(HttpHeaders.CONTENT_TYPE, TIPO_XLSX)
                .body(contenido);
    }

    /**
     * Devuelve 200 y no 201 a propósito: la respuesta es un informe, y la
     * llamada puede terminar sin haber creado nada.
     */
    @PostMapping(value = "/productores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa productores desde una planilla .xlsx",
            description = """
                    Columnas esperadas en la primera fila: CENTRAL, SINDICATO y NOMBRES son \
                    obligatorias; ABREVIATURA, APELLIDOS, C.I, N° LOTE, EXTENSION, \
                    CLASIFICACION y OBSERVACIONES son opcionales. Los \
                    encabezados se reconocen sin distinguir mayúsculas, tildes ni puntuación.

                    Con `simular=true` —el valor por defecto— no se escribe nada: el proceso \
                    corre entero, incluidos los INSERT, y la transacción se deshace al final. \
                    Por eso el informe de la simulación describe exactamente lo que haría la \
                    ejecución real.

                    Una celda de OBSERVACIONES con varios motivos separados por coma genera una \
                    observación por motivo.""")
    public ImportacionResponse importarProductores(

            @Parameter(description = "Planilla .xlsx del padrón.", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("archivo") MultipartFile archivo,

            @Parameter(description = "Federación destino. Es obligatoria porque la planilla trae "
                    + "la central pero no la federación a la que pertenece.", example = "3")
            @RequestParam Long federacionId,

            @Parameter(description = "Si es true no se persiste nada. Viene en true por defecto "
                    + "para que una llamada mal armada no modifique el padrón.")
            @RequestParam(defaultValue = "true") boolean simular,

            @Parameter(description = "Aprueba crear los sindicatos que la planilla mencione y "
                    + "no existan. Las centrales nunca se crean durante una importación: deben "
                    + "registrarse manualmente con su abreviatura.")
            @RequestParam(defaultValue = "false") boolean crearJerarquia,

            @Parameter(description = "Con false, una sola fila inválida aborta todo y no se "
                    + "escribe nada. Con true, se importan las válidas y el resto queda en el "
                    + "informe.")
            @RequestParam(defaultValue = "false") boolean ignorarFilasConError) {

        validar(archivo);

        try (InputStream entrada = archivo.getInputStream()) {
            return importacionService.importar(entrada, federacionId, simular, crearJerarquia,
                    ignorarFilasConError);
        } catch (IOException e) {
            throw new PlanillaInvalidaException("No se pudo abrir el archivo subido.", e);
        }
    }

    private void validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new PlanillaInvalidaException("No llegó ningún archivo, o vino vacío.");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new PlanillaInvalidaException(
                    "Solo se aceptan archivos .xlsx. Llegó: " + nombre);
        }
    }
}
