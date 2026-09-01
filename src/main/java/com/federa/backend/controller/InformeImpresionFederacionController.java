package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.InformeImpresionFederacion;
import com.federa.backend.service.InformeImpresionFederacionService;
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

/** Consulta del avance global de impresión de todas las centrales. */
@RestController
@RequestMapping(ApiRutas.V1 + "/federaciones")
@Tag(name = "Federaciones")
public class InformeImpresionFederacionController {

    private final InformeImpresionFederacionService servicio;

    public InformeImpresionFederacionController(InformeImpresionFederacionService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{id}/credenciales/impresion")
    @Operation(summary = "Resume el avance de impresión de todas las centrales")
    public InformeImpresionFederacion obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @GetMapping(value = "/{id}/credenciales/impresion/informe.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga el avance de impresión de toda la federación")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        InformeImpresionFederacionService.Descarga descarga = servicio.descargarPdf(id);
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(descarga.nombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(descarga.contenido());
    }
}
