package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.ConvocadoResponse;
import com.federa.backend.dto.EstadoRequest;
import com.federa.backend.dto.RegistroAsistenciaResponse;
import com.federa.backend.dto.ReunionRequest;
import com.federa.backend.dto.ReunionResponse;
import com.federa.backend.service.ReunionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(ApiRutas.V1 + "/reuniones")
@Tag(name = "Reuniones", description =
        "Convocatorias y pase de lista. Hay cuatro tipos y cada uno llama a gente distinta: "
        + "la reunión de un sindicato a sus productores, el ampliado a los de toda la central, "
        + "y las de dirigentes solo a presidentes y secretarios. La lista se pasa escaneando el "
        + "QR de la credencial.")
public class ReunionController {

    private final ReunionService reunionService;

    public ReunionController(ReunionService reunionService) {
        this.reunionService = reunionService;
    }

    @GetMapping
    @Operation(summary = "Lista las reuniones, de la más reciente a la más antigua",
            description = "Los tres filtros son excluyentes: se pasa el del nivel que interesa.")
    public List<ReunionResponse> listar(
            @Parameter(description = "Solo las convocadas por este sindicato")
            @RequestParam(required = false) Long sindicatoId,
            @Parameter(description = "Solo las convocadas por esta central")
            @RequestParam(required = false) Long centralId,
            @Parameter(description = "Solo las convocadas por esta federación")
            @RequestParam(required = false) Long federacionId) {
        return reunionService.listar(sindicatoId, centralId, federacionId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Una reunión, con cuántos convoca y cuántos van")
    public ReunionResponse obtener(@PathVariable Long id) {
        return reunionService.obtener(id);
    }

    @GetMapping("/{id}/lista")
    @Operation(summary = "La lista de convocados, marcando quiénes ya llegaron",
            description = """
                    Sale ordenada por apellido, para poder buscar a alguien a ojo cuando el \
                    carnet no aparece. Cada línea dice si está presente y a qué hora se \
                    registró.

                    Quiénes son convocados depende del tipo: los productores del sindicato, los \
                    de toda la central en un ampliado, o solo los presidentes y secretarios en \
                    las de dirigentes.""")
    public List<ConvocadoResponse> lista(@PathVariable Long id) {
        return reunionService.lista(id);
    }

    @PostMapping
    @Operation(summary = "Convoca una reunión",
            description = "El tipo determina de qué nivel cuelga: `convocanteId` se interpreta "
                    + "como sindicato, central o federación según corresponda.")
    public ResponseEntity<ReunionResponse> crear(@Valid @RequestBody ReunionRequest peticion) {
        ReunionResponse creada = reunionService.crear(peticion);
        return ResponseEntity
                .created(URI.create(ApiRutas.V1 + "/reuniones/" + creada.id()))
                .body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corrige los datos de la reunión",
            description = "El tipo y quién convoca no se pueden cambiar: definen la lista de "
                    + "convocados, y cambiarlos con asistencias ya tomadas dejaría presentes a "
                    + "gente que la nueva convocatoria no llama. Devuelve 409 si se intenta.")
    public ReunionResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody ReunionRequest peticion) {
        return reunionService.actualizar(id, peticion);
    }

    @PostMapping("/{id}/asistencias")
    @Operation(summary = "Pasa lista: registra a alguien por el código de su credencial",
            description = """
                    Es lo que hace el escaneo del QR. También sirve escribiendo el código a \
                    mano, que es el respaldo cuando la cámara no coopera.

                    Escanear dos veces el mismo carnet devuelve 200 con resultado REPETIDO, no \
                    un error: para quien está pasando lista frente a una fila de gente, "ya \
                    estaba" es información, no un fallo.

                    Devuelve 404 si ninguna credencial tiene ese código, y 409 si la persona no \
                    está convocada a esta reunión o si la lista está cerrada.""")
    public RegistroAsistenciaResponse registrar(
            @PathVariable Long id,
            @Valid @RequestBody CodigoRequest peticion) {
        return reunionService.registrarPorCodigo(id, peticion.codigo());
    }

    @DeleteMapping("/{id}/asistencias/{productorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quita a alguien de la lista",
            description = "Para cuando se escaneó el carnet equivocado.")
    public void quitar(@PathVariable Long id, @PathVariable Long productorId) {
        reunionService.quitarAsistencia(id, productorId);
    }

    @PatchMapping("/{id}/cierre")
    @Operation(summary = "Cierra o reabre la lista",
            description = "Una lista cerrada no admite más asistencias. Pasar lista es un acto "
                    + "con un momento: si quedara abierta para siempre, alguien podría sumarse "
                    + "una semana después y el acta diría que estuvo.")
    public ReunionResponse cambiarCierre(@PathVariable Long id,
                                         @Valid @RequestBody EstadoRequest peticion) {
        return reunionService.cambiarCierre(id, peticion.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una reunión",
            description = "Devuelve 409 si ya tiene asistencias: borrarla perdería la lista.")
    public void eliminar(@PathVariable Long id) {
        reunionService.eliminar(id);
    }

    /** Solo el código, que es lo que devuelve el lector de QR. */
    @Schema(description = "Código de una credencial.")
    public record CodigoRequest(
            @Schema(description = "El texto que dice el QR, o el que se escribió a mano.",
                    example = "AB12CD34EF", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "hace falta el código de la credencial")
            String codigo
    ) {
    }
}
