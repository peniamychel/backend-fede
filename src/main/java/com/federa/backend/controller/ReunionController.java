package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.EstadoRequest;
import com.federa.backend.dto.LlamadaResponse;
import com.federa.backend.dto.NotaRequest;
import com.federa.backend.dto.ReunionRequest;
import com.federa.backend.dto.ReunionResponse;
import com.federa.backend.model.enums.TipoReunion;
import com.federa.backend.service.ReunionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiRutas.V1 + "/reuniones")
@Tag(name = "Reuniones", description =
        "Convocatorias y pase de lista. Hay cuatro tipos y cada uno llama a gente distinta: "
        + "la reunión de un sindicato a sus productores, el ampliado a los de toda la central, "
        + "y las de dirigentes solo a Secretarios Generales y Secretarios Relaciones. La lista se pasa escaneando el "
        + "QR de la credencial.")
public class ReunionController {

    private final ReunionService reunionService;

    public ReunionController(ReunionService reunionService) {
        this.reunionService = reunionService;
    }

    @GetMapping
    @Operation(summary = "Lista las reuniones, de la más reciente a la más antigua",
            description = """
                    Los tres filtros de nivel son excluyentes: se pasa el del nivel que \
                    interesa.

                    El `texto` busca por dos caminos, que son las dos formas en que alguien \
                    busca una asamblea meses después: por **lo que fue** —título, lugar, notas \
                    o número del acta— y por **a quién se vetó ahí** —su nombre, su cédula, \
                    cualquiera de sus dos códigos, o el motivo escrito—. Cuenta tanto la \
                    reunión que impuso el veto como la que lo levantó.""")
    public List<ReunionResponse> listar(
            @Parameter(description = "Solo las convocadas por este sindicato")
            @RequestParam(required = false) Long sindicatoId,
            @Parameter(description = "Solo las convocadas por esta central")
            @RequestParam(required = false) Long centralId,
            @Parameter(description = "Solo las convocadas por esta federación")
            @RequestParam(required = false) Long federacionId,
            @Parameter(description = "Solo las de este tipo", example = "AMPLIADO")
            @RequestParam(required = false) TipoReunion tipo,
            @Parameter(description = "Busca en el detalle de la reunión y en sus vetos",
                    example = "cupo")
            @RequestParam(required = false) String texto) {
        return reunionService.listar(sindicatoId, centralId, federacionId, tipo, texto);
    }

    @GetMapping("/conteo")
    @Operation(summary = "Cuántas reuniones hay de cada tipo",
            description = "Con el mismo filtro que el listado pero sin el tipo: la solapa de "
                    + "«Ampliado» tiene que decir cuántos ampliados hay aunque en ese momento "
                    + "se esté mirando otra. Los tipos sin ninguna salen en cero.")
    public Map<TipoReunion, Long> contar(
            @RequestParam(required = false) Long sindicatoId,
            @RequestParam(required = false) Long centralId,
            @RequestParam(required = false) Long federacionId,
            @RequestParam(required = false) String texto) {
        return reunionService.contarPorTipo(sindicatoId, centralId, federacionId, texto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Una reunión, con cuántos convoca y cuántos van")
    public ReunionResponse obtener(@PathVariable Long id) {
        return reunionService.obtener(id);
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
                    + "gente que la nueva convocatoria no llama. Devuelve 409 si se intenta, y "
                    + "también si se apagan los vetos en una reunión que ya decidió alguno.")
    public ReunionResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody ReunionRequest peticion) {
        return reunionService.actualizar(id, peticion);
    }

    @GetMapping("/{id}/llamadas")
    @Operation(summary = "Las vueltas de lista de la reunión",
            description = "En una asamblea se llama lista más de una vez: al empezar, más "
                    + "tarde para los que llegaron tarde, y a veces al final. Cada vuelta "
                    + "tiene su propia lista de presentes.")
    public List<LlamadaResponse> llamadas(@PathVariable Long id) {
        return reunionService.llamadasDe(id).stream()
                .map(l -> LlamadaResponse.desde(l, reunionService.presentesEn(l.getId())))
                .toList();
    }

    @PostMapping("/{id}/llamadas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre una vuelta de lista",
            description = "Devuelve 409 si la lista de la reunión está cerrada, o si ya hay "
                    + "otra vuelta abierta: primero se cierra esa.")
    public LlamadaResponse abrirLlamada(@PathVariable Long id,
                                        @RequestBody(required = false) NotaRequest peticion) {
        var llamada = reunionService.abrirLlamada(id, peticion == null ? null : peticion.nota());
        return LlamadaResponse.desde(llamada, 0);
    }

    @PatchMapping("/{id}/cierre")
    @Operation(summary = "Cierra o reabre el pase de lista de la reunión",
            description = "Con la lista cerrada ya no se pueden abrir más llamadas. Pasar "
                    + "lista es un acto con un momento: si quedara abierto para siempre, "
                    + "alguien podría sumarse una semana después y el acta diría que estuvo.")
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
}
