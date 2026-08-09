package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.CentralResponse;
import com.federa.backend.dto.EstadoRequest;
import com.federa.backend.dto.FederacionRequest;
import com.federa.backend.dto.FederacionResponse;
import com.federa.backend.service.CentralService;
import com.federa.backend.service.FederacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(ApiRutas.V1 + "/federaciones")
@Tag(name = "Federaciones", description =
        "Nivel más alto de la organización. Una federación agrupa muchas centrales y cada "
        + "central pertenece a una sola.")
public class FederacionController {

    private final FederacionService federacionService;
    private final CentralService centralService;

    public FederacionController(FederacionService federacionService, CentralService centralService) {
        this.federacionService = federacionService;
        this.centralService = centralService;
    }

    @GetMapping
    @Operation(summary = "Lista todas las federaciones, ordenadas por nombre")
    public List<FederacionResponse> listar() {
        return federacionService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una federación por id")
    public FederacionResponse obtener(@PathVariable Long id) {
        return federacionService.obtener(id);
    }

    @GetMapping("/{id}/centrales")
    @Operation(summary = "Lista las centrales que pertenecen a la federación")
    public List<CentralResponse> centrales(@PathVariable Long id) {
        federacionService.obtener(id);
        return centralService.listar(id);
    }

    @PostMapping
    @Operation(summary = "Crea una federación",
            description = "El nombre se guarda normalizado en mayúsculas y sin tildes. "
                    + "Devuelve 409 si ya existe otra con ese nombre.")
    public ResponseEntity<FederacionResponse> crear(@Valid @RequestBody FederacionRequest request) {
        FederacionResponse creada = federacionService.crear(request);
        return ResponseEntity.created(URI.create(ApiRutas.V1 + "/federaciones/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Renombra una federación")
    public FederacionResponse actualizar(@PathVariable Long id,
                                         @Valid @RequestBody FederacionRequest request) {
        return federacionService.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Habilita o deshabilita una federación",
            description = "Deshabilitar no borra nada: la fila queda con todas sus "
                    + "relaciones y se puede volver a habilitar cuando haga falta. Es la "
                    + "alternativa para lo que no se deja eliminar por tener registros "
                    + "dependientes.")
    public FederacionResponse cambiarEstado(@PathVariable Long id,
                                        @Valid @RequestBody EstadoRequest request) {
        return federacionService.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una federación",
            description = "Devuelve 409 si todavía tiene centrales: el borrado en cascada "
                    + "arrastraría el padrón entero.")
    public void eliminar(@PathVariable Long id) {
        federacionService.eliminar(id);
    }
}
