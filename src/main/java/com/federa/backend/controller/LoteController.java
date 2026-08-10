package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.LoteRequest;
import com.federa.backend.dto.LoteResponse;
import com.federa.backend.dto.TenenciaResponse;
import com.federa.backend.dto.TraspasoLoteRequest;
import com.federa.backend.dto.UbicacionRequest;
import com.federa.backend.service.LoteService;
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
@RequestMapping(ApiRutas.V1 + "/lotes")
@Tag(name = "Lotes", description =
        "Parcela asignada a un productor. El número es texto porque el padrón trae rangos "
        + "(30-31) y códigos (B.N47), y puede repetirse dentro de un sindicato: eso se reporta "
        + "como observación, no se bloquea.")
public class LoteController {

    private final LoteService loteService;

    private final SistemaService sistemaService;

    public LoteController(LoteService loteService, SistemaService sistemaService) {
        this.sistemaService = sistemaService;
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
    @Operation(summary = "Corrige los datos del lote",
            description = "No cambia al tenedor ni el sindicato: la tierra no se muda, y "
                    + "cambiar de manos es un traspaso con fecha y motivo, en /tenencia.")
    public LoteResponse actualizar(@PathVariable Long id, @Valid @RequestBody LoteRequest request) {
        return loteService.actualizar(id, request);
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Quiénes tuvieron este lote",
            description = """
                    Del período más reciente al primero, con la fecha en que cada uno lo tomó, \
                    hasta cuándo lo tuvo y por qué cambió de manos.

                    Es la razón de ser de este modelo: la parcela no se mueve, y lo que hay que \
                    poder responder es quién la tenía en tal fecha.""")
    public List<TenenciaResponse> historial(@PathVariable Long id) {
        return loteService.historial(id);
    }

    @GetMapping("/{id}/sistemas")
    @Operation(summary = "Qué sistemas pasaron por este lote")
    public List<TenenciaResponse> historialDeSistemas(@PathVariable Long id) {
        return sistemaService.historialDeLote(id);
    }

    @PutMapping("/{id}/tenencia")
    @Operation(summary = "Traspasa el lote a otro productor",
            description = """
                    Cierra el período del tenedor actual el día anterior al nuevo y abre otro. \
                    Nadie se borra: el anterior queda en el historial con el motivo del traspaso.

                    Omitir `productorId` deja el lote sin tenedor, que es una situación real: \
                    alguien vendió y el comprador todavía no está cargado en el padrón.

                    Devuelve 409 si el productor es de otro sindicato —la tierra no se muda, \
                    así que primero hay que pasarlo a ese sindicato— o si la fecha es anterior \
                    al inicio de la tenencia actual.""")
    public LoteResponse traspasar(@PathVariable Long id,
                                  @Valid @RequestBody TraspasoLoteRequest peticion) {
        return loteService.traspasar(id, peticion);
    }

    @GetMapping("/con-ubicacion")
    @Operation(summary = "Lotes de un sindicato que ya tienen punto marcado",
            description = "Pensado para dibujarlos todos juntos en un mapa. Deja fuera a los "
                    + "que todavía no se ubicaron.")
    public List<LoteResponse> conUbicacion(
            @Parameter(description = "Sindicato cuyas parcelas se quieren ver")
            @RequestParam Long sindicatoId) {
        return loteService.conUbicacion(sindicatoId);
    }

    @PutMapping("/{id}/ubicacion")
    @Operation(summary = "Marca o mueve el punto de la parcela",
            description = "Recibe las coordenadas en grados decimales, tal como las entrega el "
                    + "mapa. Las dos son obligatorias: media coordenada no ubica nada.")
    public LoteResponse marcarUbicacion(@PathVariable Long id,
                                        @Valid @RequestBody UbicacionRequest peticion) {
        return loteService.marcarUbicacion(id, peticion);
    }

    @DeleteMapping("/{id}/ubicacion")
    @Operation(summary = "Quita el punto de la parcela",
            description = "El lote sigue existiendo; solo deja de estar ubicado. Por eso "
                    + "devuelve el lote y no un 204.")
    public LoteResponse borrarUbicacion(@PathVariable Long id) {
        return loteService.borrarUbicacion(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un lote")
    public void eliminar(@PathVariable Long id) {
        loteService.eliminar(id);
    }
}
