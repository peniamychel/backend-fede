package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.ReunionResponse;
import com.federa.backend.exception.ArchivoInvalidoException;
import com.federa.backend.service.ActaReunionService;
import com.federa.backend.service.VetoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * El acta de una reunión, hoja por hoja.
 * <p>
 * Cuelga de la reunión porque es suya. Las hojas se suben de a una y quedan
 * ordenadas: lo habitual es fotografiar el cuaderno de actas con el teléfono en
 * la misma asamblea, y exigir un PDF armado antes sería pedir un paso que nadie
 * hace en el campo.
 */
@RestController
@RequestMapping(ApiRutas.V1 + "/reuniones/{reunionId}/acta")
@Tag(name = "Reuniones")
public class ActaReunionController {

    private final ActaReunionService actaService;
    private final VetoService vetoService;

    public ActaReunionController(ActaReunionService actaService, VetoService vetoService) {
        this.actaService = actaService;
        this.vetoService = vetoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Agrega una hoja al acta, con el número del acta",
            description = """
                    Se acepta un PDF o una imagen por hoja: la foto del cuaderno, o el acta \
                    escaneada. Se agrega al final, conservando el orden en que se suben.

                    El `codigo` es el número del acta en el libro del sindicato. **Hace falta \
                    con la primera hoja**: el archivo es una foto de una hoja del libro, y sin \
                    ese número nadie puede volver al original a cotejar lo que se decidió. Con \
                    las siguientes se puede omitir —son del mismo acta— o mandar otro para \
                    corregirlo. Devuelve 409 si falta en la primera.

                    **No se reduce.** Las fotos de los productores se achican porque en la \
                    credencial ocupan dos centímetros; un acta se lee, y bajarle la resolución \
                    la vuelve inservible justo cuando alguien necesita verificar qué se \
                    decidió. Tope: 10 MB por hoja.

                    Con al menos una hoja, la reunión ya puede decidir vetos.""")
    public ReunionResponse agregarHoja(
            @PathVariable Long reunionId,
            @Parameter(description = "PDF o imagen de una hoja del acta.", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("archivo") MultipartFile archivo,
            @Parameter(description = "Número del acta en el libro, como está escrito.",
                    example = "12/2026")
            @RequestParam(value = "codigo", required = false) String codigo) {

        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoInvalidoException("No llegó ninguna hoja, o vino vacía.");
        }
        try {
            return actaService.agregarHoja(reunionId, archivo.getBytes(),
                    archivo.getOriginalFilename(), archivo.getContentType(), codigo);
        } catch (IOException e) {
            throw new ArchivoInvalidoException("No se pudo leer el archivo subido.", e);
        }
    }

    @PutMapping("/codigo")
    @Operation(summary = "Corrige el número del acta",
            description = "Sin tocar las hojas: que se haya tipeado mal no es motivo para "
                    + "volver a subir las fotos. Devuelve 409 si la reunión todavía no tiene "
                    + "acta, o si el número llega vacío.")
    public ReunionResponse ponerCodigo(@PathVariable Long reunionId,
                                       @Valid @RequestBody CodigoActaRequest peticion) {
        return actaService.ponerCodigo(reunionId, peticion.codigo());
    }

    /** El número del acta, tal como está escrito en el libro. */
    @Schema(description = "Número del acta.")
    public record CodigoActaRequest(
            @Schema(description = "Como está escrito en el libro del sindicato.",
                    example = "12/2026", maxLength = 40,
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "hace falta el número del acta")
            @Size(max = 40, message = "el número del acta no puede pasar de 40 caracteres")
            String codigo
    ) {
    }

    @GetMapping("/hojas/{hojaId}")
    @Operation(summary = "Muestra una hoja del acta")
    public ResponseEntity<byte[]> verHoja(@PathVariable Long reunionId,
                                          @PathVariable Long hojaId) {
        ActaReunionService.Descarga hoja = actaService.leerHoja(reunionId, hojaId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + hoja.nombreArchivo() + "\"")
                .contentType(MediaType.parseMediaType(hoja.tipoMime()))
                .body(hoja.contenido());
    }

    @DeleteMapping("/hojas/{hojaId}")
    @Operation(summary = "Quita una hoja y renumera las que quedan",
            description = "Devuelve 409 si es la última hoja y en esa reunión se decidió "
                    + "algún veto: el acta es lo que los respalda.")
    public ReunionResponse quitarHoja(@PathVariable Long reunionId,
                                      @PathVariable Long hojaId) {
        return actaService.quitarHoja(reunionId, hojaId,
                vetoService.cuantosEnLaReunion(reunionId));
    }
}
