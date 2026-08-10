package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.SistemaResponse;
import com.federa.backend.dto.TenenciaResponse;
import com.federa.backend.dto.TraspasoLoteRequest;
import com.federa.backend.service.SistemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(ApiRutas.V1 + "/sistemas")
@Tag(name = "Sistemas", description =
        "El agregado que un lote puede tener o no. Se puede vender y trasladar a otro lote, "
        + "así que tiene identidad propia e historial de por dónde pasó. Dos reglas: un "
        + "sistema está en un solo lote a la vez, y un lote lleva a lo sumo un sistema.")
public class SistemaController {

    private final SistemaService sistemaService;

    public SistemaController(SistemaService sistemaService) {
        this.sistemaService = sistemaService;
    }

    @GetMapping
    @Operation(summary = "Lista los sistemas",
            description = "Los dos filtros son excluyentes.")
    public List<SistemaResponse> listar(
            @Parameter(description = "Solo los que no están en ningún lote")
            @RequestParam(defaultValue = "false") boolean disponibles,
            @Parameter(description = "Solo los instalados en lotes de este sindicato")
            @RequestParam(required = false) Long sindicatoId) {
        return sistemaService.listar(disponibles, sindicatoId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Un sistema y dónde está hoy")
    public SistemaResponse obtener(@PathVariable Long id) {
        return sistemaService.obtener(id);
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Por dónde pasó el sistema",
            description = "Del período más reciente al primero, con el lote, las fechas y el "
                    + "motivo de cada traslado.")
    public List<TenenciaResponse> historial(@PathVariable Long id) {
        return sistemaService.historial(id);
    }

    @PostMapping
    @Operation(summary = "Da de alta un sistema",
            description = "Nace sin lote: instalarlo es un traslado aparte, con su fecha.")
    public ResponseEntity<SistemaResponse> crear(
            @Valid @RequestBody SistemaResponse.Peticion peticion) {
        SistemaResponse creado = sistemaService.crear(peticion);
        return ResponseEntity
                .created(URI.create(ApiRutas.V1 + "/sistemas/" + creado.id()))
                .body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corrige el código o la descripción",
            description = "No mueve el sistema: para eso está /traslado.")
    public SistemaResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody SistemaResponse.Peticion peticion) {
        return sistemaService.actualizar(id, peticion);
    }

    @PutMapping("/{id}/traslado")
    @Operation(summary = "Instala el sistema en un lote, o lo retira",
            description = """
                    Cierra el período en el lote actual el día anterior al nuevo y abre otro. \
                    El anterior queda en el historial con su motivo.

                    Omitir `loteId` retira el sistema sin instalarlo en otro lado: queda \
                    disponible.

                    Devuelve 409 si el lote de destino ya tiene un sistema. No se reemplaza en \
                    silencio: sacar un sistema es un hecho con su propia fecha y motivo, y \
                    dejarlo implícito perdería esa constancia.""")
    public SistemaResponse trasladar(
            @PathVariable Long id,
            @Parameter(description = "Lote de destino. Si se omite, el sistema se retira.")
            @RequestParam(required = false) Long loteId,
            @Valid @RequestBody TraspasoLoteRequest peticion) {
        return sistemaService.trasladar(id, loteId, peticion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Da de baja un sistema",
            description = "Devuelve 409 si está instalado en un lote.")
    public void eliminar(@PathVariable Long id) {
        sistemaService.eliminar(id);
    }
}
