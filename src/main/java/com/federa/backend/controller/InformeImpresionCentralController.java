package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.InformeImpresionCentral;
import com.federa.backend.dto.InformeNominalImpresionCentral;
import com.federa.backend.service.InformeImpresionCentralService;
import com.federa.backend.service.InformeNominalImpresionCentralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Consulta y descarga del avance global de impresión de una central. */
@RestController
@RequestMapping(ApiRutas.V1 + "/centrales")
@Tag(name = "Centrales")
public class InformeImpresionCentralController {

    private final InformeImpresionCentralService servicio;
    private final InformeNominalImpresionCentralService servicioNominal;

    public InformeImpresionCentralController(InformeImpresionCentralService servicio,
                                               InformeNominalImpresionCentralService servicioNominal) {
        this.servicio = servicio;
        this.servicioNominal = servicioNominal;
    }

    @GetMapping("/{id}/credenciales/impresion")
    @Operation(summary = "Resume el avance de impresión de todos los sindicatos de la central")
    public InformeImpresionCentral obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @GetMapping(value = "/{id}/credenciales/impresion/informe.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga en PDF el avance de impresión de la central")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        InformeImpresionCentralService.Descarga descarga = servicio.descargarPdf(id);
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(descarga.nombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(descarga.contenido());
    }

    @PostMapping("/{id}/credenciales/impresion/informe-nominal")
    @Operation(summary = "Lista impresos y no impresos por datos faltantes")
    public InformeNominalImpresionCentral obtenerNominal(
            @PathVariable Long id, @RequestBody SeleccionSindicatos seleccion) {
        return servicioNominal.obtener(id, seleccion.sindicatoIds());
    }

    @PostMapping(value = "/{id}/credenciales/impresion/informe-nominal.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga el informe nominal para los sindicatos seleccionados")
    public ResponseEntity<byte[]> descargarNominal(
            @PathVariable Long id, @RequestBody SeleccionSindicatos seleccion) {
        InformeNominalImpresionCentralService.Descarga descarga =
                servicioNominal.descargarPdf(id, seleccion.sindicatoIds());
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(descarga.nombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(descarga.contenido());
    }

    public record SeleccionSindicatos(List<Long> sindicatoIds) {
    }
}
