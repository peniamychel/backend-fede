package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.CentralRequest;
import com.federa.backend.dto.CentralResponse;
import com.federa.backend.dto.EstadoRequest;
import com.federa.backend.dto.SindicatoResponse;
import com.federa.backend.service.CentralService;
import com.federa.backend.service.SindicatoService;
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
@RequestMapping(ApiRutas.V1 + "/centrales")
@Tag(name = "Centrales", description =
        "Agrupa sindicatos y pertenece a una federación. El padrón tiene 16. El nombre solo "
        + "es único dentro de su federación.")
public class CentralController {

    private final CentralService centralService;
    private final SindicatoService sindicatoService;

    public CentralController(CentralService centralService, SindicatoService sindicatoService) {
        this.centralService = centralService;
        this.sindicatoService = sindicatoService;
    }

    @GetMapping
    @Operation(summary = "Lista centrales, opcionalmente filtradas por federación")
    public List<CentralResponse> listar(
            @Parameter(description = "Si se omite, devuelve las centrales de todas las federaciones")
            @RequestParam(required = false) Long federacionId) {
        return centralService.listar(federacionId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una central por id")
    public CentralResponse obtener(@PathVariable Long id) {
        return centralService.obtener(id);
    }

    @GetMapping("/{id}/sindicatos")
    @Operation(summary = "Lista los sindicatos que pertenecen a la central")
    public List<SindicatoResponse> sindicatos(@PathVariable Long id) {
        centralService.obtener(id);
        return sindicatoService.listar(id);
    }

    @PostMapping
    @Operation(summary = "Crea una central",
            description = "El nombre se guarda normalizado en mayúsculas y sin tildes. "
                    + "Devuelve 409 si ya existe otra con ese nombre.")
    public ResponseEntity<CentralResponse> crear(@Valid @RequestBody CentralRequest request) {
        CentralResponse creada = centralService.crear(request);
        return ResponseEntity.created(URI.create(ApiRutas.V1 + "/centrales/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Renombra una central")
    public CentralResponse actualizar(@PathVariable Long id, @Valid @RequestBody CentralRequest request) {
        return centralService.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Habilita o deshabilita una central",
            description = "Deshabilitar no borra nada: la fila queda con todas sus "
                    + "relaciones y se puede volver a habilitar cuando haga falta. Es la "
                    + "alternativa para lo que no se deja eliminar por tener registros "
                    + "dependientes.")
    public CentralResponse cambiarEstado(@PathVariable Long id,
                                        @Valid @RequestBody EstadoRequest request) {
        return centralService.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una central",
            description = "Devuelve 409 si todavía tiene sindicatos: el borrado en cascada "
                    + "arrastraría también a sus productores y a sus lotes.")
    public void eliminar(@PathVariable Long id) {
        centralService.eliminar(id);
    }
}
