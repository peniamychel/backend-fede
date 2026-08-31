package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.InformeImpresionFederacion;
import com.federa.backend.service.InformeImpresionFederacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
