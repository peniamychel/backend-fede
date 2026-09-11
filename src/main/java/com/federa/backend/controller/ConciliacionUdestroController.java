package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.*;
import com.federa.backend.exception.PlanillaInvalidaException;
import com.federa.backend.model.enums.AccionConciliacionUdestro;
import com.federa.backend.service.ConciliacionUdestroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(ApiRutas.V1 + "/conciliaciones-udestro")
@Tag(name = "Conciliación UDESTRO",
        description = "Compara la nómina completa de productores con SISTEMA y solo modifica "
                + "el padrón después de resolver conflictos y confirmar el borrador.")
public class ConciliacionUdestroController {

    private final ConciliacionUdestroService servicio;

    public ConciliacionUdestroController(ConciliacionUdestroService servicio) {
        this.servicio = servicio;
    }

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Analiza la planilla y guarda una propuesta sin tocar productores")
    public ConciliacionUdestroResponse analizar(@RequestPart("archivo") MultipartFile archivo) {
        validar(archivo);
        try {
            return servicio.analizar(archivo.getBytes(), archivo.getOriginalFilename());
        } catch (IOException e) {
            throw new PlanillaInvalidaException("No se pudo abrir el archivo subido.", e);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta el resumen actualizado de una conciliación")
    public ConciliacionUdestroResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @GetMapping("/ultimo-borrador")
    @Operation(summary = "Recupera el último borrador para continuar su revisión")
    public ConciliacionUdestroResponse ultimoBorrador() {
        return servicio.ultimoBorrador();
    }

    @GetMapping("/{id}/filas")
    @Operation(summary = "Lista las acciones, errores o conflictos de la propuesta")
    public PagedModel<FilaConciliacionUdestroResponse> filas(
            @PathVariable Long id,
            @RequestParam(required = false) AccionConciliacionUdestro accion,
            @org.springframework.data.web.PageableDefault(size = 50, sort = "id")
            Pageable pageable) {
        return servicio.listarFilas(id, accion, pageable);
    }

    @PatchMapping("/{id}/filas/{filaId}")
    @Operation(summary = "Guarda la decisión sobre un conflicto sin aplicarla al padrón")
    public FilaConciliacionUdestroResponse decidir(
            @PathVariable Long id,
            @PathVariable Long filaId,
            @Valid @RequestBody DecisionUdestroRequest request) {
        return servicio.decidir(id, filaId, request);
    }

    @PostMapping("/{id}/aplicar")
    @Operation(summary = "Revalida y aplica una conciliación aprobada una sola vez")
    public ConciliacionUdestroResponse aplicar(
            @PathVariable Long id,
            @RequestBody(required = false) AplicarConciliacionUdestroRequest request) {
        return servicio.aplicar(id, request == null
                ? new AplicarConciliacionUdestroRequest(false) : request);
    }

    private void validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new PlanillaInvalidaException("No llegó ningún archivo, o vino vacío.");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new PlanillaInvalidaException("Solo se aceptan archivos .xlsx.");
        }
    }
}
