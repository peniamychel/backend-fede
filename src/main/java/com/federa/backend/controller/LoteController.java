package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.LoteRequest;
import com.federa.backend.dto.LoteResponse;
import com.federa.backend.service.LoteService;
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
@RequestMapping(ApiRutas.V1 + "/lotes")
@Tag(name = "Lotes", description =
        "Parcela asignada a un productor. El número es texto porque el padrón trae rangos "
        + "(30-31) y códigos (B.N47), y puede repetirse dentro de un sindicato: eso se reporta "
        + "como observación, no se bloquea.")
public class LoteController {

    private final LoteService loteService;

    public LoteController(LoteService loteService) {
        this.loteService = loteService;
    }

    @GetMapping
    @Operation(summary = "Lista lotes, opcionalmente acotados a un productor o un sindicato")
    public List<LoteResponse> listar(
            @Parameter(description = "Acota a un productor") @RequestParam(required = false) Long productorId,
            @Parameter(description = "Acota a un sindicato") @RequestParam(required = false) Long sindicatoId) {
        return loteService.listar(productorId, sindicatoId);
    }

    @GetMapping("/estado-desconocido")
    @Operation(summary = "Lotes cuyo estado no encajó en ninguna categoría conocida",
            description = "El texto original queda en estadoOriginal para poder decidir a mano "
                    + "a qué estado corresponde.")
    public List<LoteResponse> estadoDesconocido() {
        return loteService.conEstadoDesconocido();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un lote por id")
    public LoteResponse obtener(@PathVariable Long id) {
        return loteService.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Registra un lote",
            description = "estado, extension y mercado se mandan como los escribe la planilla "
                    + "(C-S, SISTEMA, FRANSIONADOS, detallista) y se normalizan acá. Un estado "
                    + "no reconocido se guarda como DESCONOCIDO en vez de rechazar la carga.")
    public ResponseEntity<LoteResponse> crear(@Valid @RequestBody LoteRequest request) {
        LoteResponse creado = loteService.crear(request);
        return ResponseEntity.created(URI.create(ApiRutas.V1 + "/lotes/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza un lote o lo reasigna a otro productor")
    public LoteResponse actualizar(@PathVariable Long id, @Valid @RequestBody LoteRequest request) {
        return loteService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un lote")
    public void eliminar(@PathVariable Long id) {
        loteService.eliminar(id);
    }
}
