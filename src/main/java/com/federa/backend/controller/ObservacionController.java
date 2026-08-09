package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.ObservacionRequest;
import com.federa.backend.dto.ObservacionResponse;
import com.federa.backend.service.ObservacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping(ApiRutas.V1 + "/observaciones")
@Tag(name = "Observaciones", description =
        "Qué hay que corregir de un productor, en texto libre. Una celda de la planilla podía "
        + "juntar varios motivos separados por coma; acá cada motivo es una fila para poder "
        + "resolverlos de a uno.")
public class ObservacionController {

    private final ObservacionService observacionService;

    public ObservacionController(ObservacionService observacionService) {
        this.observacionService = observacionService;
    }

    @GetMapping
    @Operation(summary = "Listado paginado de observaciones",
            description = "Los filtros son opcionales y combinables: las pendientes de un "
                    + "sindicato, las que mencionan cierta palabra, las de un productor.")
    public PagedModel<ObservacionResponse> listar(
            @Parameter(description = "Acota a un productor") @RequestParam(required = false) Long productorId,
            @Parameter(description = "Acota a un sindicato") @RequestParam(required = false) Long sindicatoId,
            @Parameter(description = "Acota a una central") @RequestParam(required = false) Long centralId,
            @Parameter(description = "Deja solo las que siguen sin resolver")
            @RequestParam(defaultValue = "false") boolean soloPendientes,
            @Parameter(description = "Busca dentro del mensaje, sin distinguir tildes ni mayúsculas")
            @RequestParam(required = false) String texto,
            @PageableDefault(size = 25) Pageable pageable) {
        return new PagedModel<>(observacionService.listar(
                productorId, sindicatoId, centralId, soloPendientes, texto, pageable));
    }

    @GetMapping("/pendientes/total")
    @Operation(summary = "Cuántas observaciones quedan sin resolver en todo el padrón")
    public long pendientes() {
        return observacionService.pendientes();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una observación por id")
    public ObservacionResponse obtener(@PathVariable Long id) {
        return observacionService.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Anota una observación sobre un productor")
    public ResponseEntity<ObservacionResponse> crear(@Valid @RequestBody ObservacionRequest request) {
        ObservacionResponse creada = observacionService.crear(request);
        return ResponseEntity.created(URI.create(ApiRutas.V1 + "/observaciones/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corrige el mensaje de una observación o la reasigna a otro productor")
    public ObservacionResponse actualizar(@PathVariable Long id,
                                          @Valid @RequestBody ObservacionRequest request) {
        return observacionService.actualizar(id, request);
    }

    @PatchMapping("/{id}/resolver")
    @Operation(summary = "Marca la observación como resuelta y registra el momento")
    public ObservacionResponse resolver(@PathVariable Long id) {
        return observacionService.resolver(id);
    }

    @PatchMapping("/{id}/reabrir")
    @Operation(summary = "Vuelve a dejar la observación como pendiente")
    public ObservacionResponse reabrir(@PathVariable Long id) {
        return observacionService.reabrir(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una observación")
    public void eliminar(@PathVariable Long id) {
        observacionService.eliminar(id);
    }
}
