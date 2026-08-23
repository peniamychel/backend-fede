package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.LevantarVetoRequest;
import com.federa.backend.dto.VetoRequest;
import com.federa.backend.dto.VetoResponse;
import com.federa.backend.service.VetoService;
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
@RequestMapping(ApiRutas.V1 + "/vetos")
@Tag(name = "Vetos", description =
        "Productores observados por decisión de asamblea. Mientras el veto rige, su "
        + "credencial no se emite. Vetar y levantar son decisiones de reunión, y la reunión "
        + "tiene que tener su acta cargada.")
public class VetoController {

    private final VetoService vetoService;

    public VetoController(VetoService vetoService) {
        this.vetoService = vetoService;
    }

    @GetMapping
    @Operation(summary = "Busca vetados",
            description = """
                    Un solo endpoint para las cuatro formas en que se pregunta: con la cédula \
                    en la mano, con el código de la credencial, por nombre y apellido, o \
                    mirando un sindicato entero.

                    Por defecto trae solo los que están vetados hoy, que es lo que se necesita \
                    al controlar a alguien. Con `vigentes=false` sale también el historial de \
                    los ya levantados.""")
    public List<VetoResponse> buscar(
            @Parameter(description = "Busca en nombres, apellidos y cédula, y compara exacto "
                    + "contra el código de la credencial")
            @RequestParam(required = false) String texto,
            @Parameter(description = "Acota a un sindicato")
            @RequestParam(required = false) Long sindicatoId,
            @RequestParam(defaultValue = "true") boolean vigentes) {
        return vetoService.buscar(texto, sindicatoId, vigentes);
    }

    @GetMapping("/productor/{productorId}")
    @Operation(summary = "Todo lo que le pasó a una persona",
            description = "El veto vigente si lo tiene, y los ya levantados. Un veto no se "
                    + "borra al levantarse: queda cerrado, con las dos decisiones a la vista.")
    public List<VetoResponse> historial(@PathVariable Long productorId) {
        return vetoService.historialDe(productorId);
    }

    @GetMapping("/reunion/{reunionId}")
    @Operation(summary = "Lo que esa reunión decidió sobre vetos",
            description = "Los que impuso y los que levantó, juntos: en una asamblea se hacen "
                    + "las dos cosas. Cuál es cuál se sabe mirando si la reunión figura en "
                    + "`reunion` o en `reunionLevanta`.")
    public List<VetoResponse> deLaReunion(@PathVariable Long reunionId) {
        return vetoService.deLaReunion(reunionId);
    }

    @PostMapping
    @Operation(summary = "Veta a un productor",
            description = """
                    Devuelve 409 si la reunión no tiene el acta cargada, si falta el motivo, o \
                    si esa persona ya tiene un veto abierto.

                    A partir de acá su credencial no se emite y la vista previa dice que está \
                    observado. No se lo da de baja ni se lo borra: sigue siendo afiliado, con \
                    su parcela y su historial.""")
    public ResponseEntity<VetoResponse> vetar(@Valid @RequestBody VetoRequest peticion) {
        VetoResponse creado = vetoService.vetar(peticion);
        return ResponseEntity.created(URI.create(ApiRutas.V1 + "/vetos/" + creado.id()))
                .body(creado);
    }

    @PutMapping("/{id}/levantar")
    @Operation(summary = "Lo saca de la lista de vetados",
            description = """
                    Se decide en **otra** reunión, no en la que lo vetó, y esa reunión también \
                    tiene que tener su acta cargada.

                    Devuelve 409 si el veto ya estaba levantado, si la reunión es la misma que \
                    lo vetó, o si le falta el acta.""")
    public VetoResponse levantar(@PathVariable Long id,
                                 @Valid @RequestBody LevantarVetoRequest peticion) {
        return vetoService.levantar(id, peticion.reunionId(), peticion.motivo(),
                peticion.hasta());
    }
}
