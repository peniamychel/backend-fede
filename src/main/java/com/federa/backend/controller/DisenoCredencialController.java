package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.DisenoCredencial;
import com.federa.backend.service.DisenoCredencialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiRutas.V1 + "/configuracion/credencial")
@Tag(name = "Diseño de credenciales")
public class DisenoCredencialController {

    private final DisenoCredencialService service;

    public DisenoCredencialController(DisenoCredencialService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Obtiene el diseño y el catálogo de campos disponibles")
    public DisenoCredencial.Editor obtener() {
        return service.editor();
    }

    @PutMapping
    @Operation(summary = "Guarda el diseño que usarán la vista previa y los PDF")
    public DisenoCredencial.Editor guardar(@RequestBody DisenoCredencial diseno) {
        return service.guardar(diseno);
    }

    @PutMapping("/restablecer")
    @Operation(summary = "Restablece el diseño original")
    public DisenoCredencial.Editor restablecer() {
        return service.restablecer();
    }
}
