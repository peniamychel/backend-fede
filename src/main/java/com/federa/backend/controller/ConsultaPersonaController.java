package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.ConsultaPersonaRequest;
import com.federa.backend.dto.ConsultaPersonaResponse;
import com.federa.backend.service.ConsultaPersonaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiRutas.V1 + "/personas")
@Tag(name = "Consulta de personas", description =
        "Consulta datos de identidad mediante el servicio SIE sin exponer su token al cliente.")
public class ConsultaPersonaController {

    private final ConsultaPersonaService servicio;

    public ConsultaPersonaController(ConsultaPersonaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/consulta")
    @Operation(summary = "Busca nombres y apellidos por cédula en SIE")
    public ConsultaPersonaResponse consultar(
            @Valid @RequestBody ConsultaPersonaRequest peticion) {
        return servicio.consultar(peticion.ci());
    }
}
