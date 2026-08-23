package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.ConvocadoResponse;
import com.federa.backend.dto.LlamadaResponse;
import com.federa.backend.dto.RegistroAsistenciaResponse;
import com.federa.backend.service.ReunionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Pasar lista, vuelta por vuelta.
 * <p>
 * En una asamblea se llama lista más de una vez: al empezar, más tarde para los
 * que llegaron con retraso, y a veces al final para ver quién se quedó hasta el
 * cierre. Cada vuelta tiene su propia lista de presentes, así que estas
 * operaciones cuelgan de la llamada y no de la reunión.
 * <p>
 * Las vueltas se abren y se listan desde la reunión: {@code POST} y
 * {@code GET /reuniones/&#123;id&#125;/llamadas}.
 */
@RestController
@RequestMapping(ApiRutas.V1 + "/llamadas")
@Tag(name = "Reuniones")
public class LlamadaListaController {

    private final ReunionService reunionService;

    public LlamadaListaController(ReunionService reunionService) {
        this.reunionService = reunionService;
    }

    @GetMapping("/{id}/lista")
    @Operation(summary = "La lista de convocados, marcando quiénes ya llegaron",
            description = """
                    Sale ordenada por apellido, para poder buscar a alguien a ojo cuando el \
                    carnet no aparece. Cada línea dice si está presente en esta vuelta y a qué \
                    hora se registró.

                    Quiénes son convocados depende del tipo de reunión: los productores del \
                    sindicato, los de toda la central en un ampliado, o solo los secretarios \
                    Generales y Secretarios Relaciones en las de dirigentes.""")
    public List<ConvocadoResponse> lista(@PathVariable Long id) {
        return reunionService.lista(id);
    }

    @PostMapping("/{id}/asistencias")
    @Operation(summary = "Registra a alguien por el código de su credencial",
            description = """
                    Es lo que hace el escaneo del QR. También sirve escribiendo el código a \
                    mano, que es el respaldo cuando la cámara no coopera.

                    Escanear dos veces el mismo carnet devuelve 200 con resultado REPETIDO, no \
                    un error: para quien está pasando lista frente a una fila de gente, "ya \
                    estaba" es información, no un fallo.

                    Devuelve 404 si ninguna credencial tiene ese código, y 409 si la persona no \
                    está convocada a esta reunión o si esta vuelta ya se cerró.""")
    public RegistroAsistenciaResponse registrar(@PathVariable Long id,
                                                @Valid @RequestBody CodigoRequest peticion) {
        return reunionService.registrarPorCodigo(id, peticion.codigo());
    }

    @DeleteMapping("/{id}/asistencias/{productorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quita a alguien de esta vuelta",
            description = "Para cuando se escaneó el carnet equivocado.")
    public void quitar(@PathVariable Long id, @PathVariable Long productorId) {
        reunionService.quitarAsistencia(id, productorId);
    }

    @PutMapping("/{id}/cierre")
    @Operation(summary = "Cierra esta vuelta de lista",
            description = "Una vuelta cerrada no admite más registros: lo que se tome de acá "
                    + "en más va a la siguiente llamada. Devuelve 409 si ya estaba cerrada.")
    public LlamadaResponse cerrar(@PathVariable Long id) {
        return LlamadaResponse.desde(reunionService.cerrarLlamada(id),
                reunionService.presentesEn(id));
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
