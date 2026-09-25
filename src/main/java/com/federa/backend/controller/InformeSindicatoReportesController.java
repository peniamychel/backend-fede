package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.InformeImpresionCentral;
import com.federa.backend.service.InformeImpresionCentralService;
import com.federa.backend.service.InformeFaseImpresionService;
import com.federa.backend.service.InformePreImpresionCentralService;
import com.federa.backend.service.InformeSindicatoService;
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
import java.util.List;

/** Informes limitados a un sindicato. */
@RestController
@RequestMapping(ApiRutas.V1 + "/sindicatos")
@Tag(name = "Sindicatos")
public class InformeSindicatoReportesController {

    private final InformeImpresionCentralService avance;
    private final InformePreImpresionCentralService revision;
    private final InformeFaseImpresionService fases;
    private final InformeSindicatoService nomina;

    public InformeSindicatoReportesController(
            InformeImpresionCentralService avance,
            InformePreImpresionCentralService revision,
            InformeFaseImpresionService fases,
            InformeSindicatoService nomina) {
        this.avance = avance;
        this.revision = revision;
        this.fases = fases;
        this.nomina = nomina;
    }

    @GetMapping("/{id}/informes/avance")
    @Operation(summary = "Avance de impresión de un solo sindicato")
    public InformeImpresionCentral.FilaSindicato avance(@PathVariable Long id) {
        return avance.obtenerSindicato(id);
    }

    @GetMapping(value = "/{id}/informes/revision-padron.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga la revisión del padrón de un solo sindicato")
    public ResponseEntity<byte[]> revisionPadron(@PathVariable Long id) {
        InformePreImpresionCentralService.Descarga descarga =
                revision.descargarRevisionPadronSindicatoPdf(id);
        return pdf(descarga.nombreArchivo(), descarga.contenido());
    }

    @GetMapping(value = "/{id}/informes/pre-impresion.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga el informe pre-impresión de un solo sindicato")
    public ResponseEntity<byte[]> preImpresion(@PathVariable Long id) {
        InformePreImpresionCentralService.Descarga descarga =
                revision.descargarPreImpresionSindicatoPdf(id);
        return pdf(descarga.nombreArchivo(), descarga.contenido());
    }

    @GetMapping(value = "/{id}/informes/nomina.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga la nómina de productores de un solo sindicato")
    public ResponseEntity<byte[]> nomina(@PathVariable Long id) {
        InformeSindicatoService.Descarga descarga = nomina.generar(id);
        return pdf(descarga.nombreArchivo(), descarga.contenido());
    }

    @GetMapping("/{id}/informes/fases")
    @Operation(summary = "Fases de impresión disponibles para el informe del sindicato")
    public List<InformeFaseImpresionService.FaseDisponible> fases(@PathVariable Long id) {
        return fases.fasesDisponiblesSindicato(id);
    }

    @GetMapping(value = "/{id}/informes/fases/{faseId}/informe.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga el informe de una fase para un solo sindicato")
    public ResponseEntity<byte[]> informeFase(
            @PathVariable Long id, @PathVariable Long faseId) {
        InformeFaseImpresionService.Descarga descarga =
                fases.descargarSindicatoPdf(id, faseId);
        return pdf(descarga.nombreArchivo(), descarga.contenido());
    }

    private ResponseEntity<byte[]> pdf(String nombre, byte[] contenido) {
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(nombre, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(contenido);
    }
}
