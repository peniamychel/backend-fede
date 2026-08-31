package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.service.PlanillaRecoleccionDirectorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** Descarga de la planilla física para recolectar sellos y firmas. */
@RestController
@RequestMapping(ApiRutas.V1 + "/centrales")
@Tag(name = "Centrales")
public class PlanillaRecoleccionDirectorioController {

    private final PlanillaRecoleccionDirectorioService servicio;

    public PlanillaRecoleccionDirectorioController(
            PlanillaRecoleccionDirectorioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping(value = "/{id}/credenciales/impresion/planilla-recoleccion.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga la planilla para recolectar sellos, firmas y pies de firma")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        PlanillaRecoleccionDirectorioService.Descarga descarga = servicio.descargarPdf(id);
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(descarga.nombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(descarga.contenido());
    }
}
