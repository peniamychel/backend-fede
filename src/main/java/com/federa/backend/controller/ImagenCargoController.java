package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.CargoResponse;
import com.federa.backend.exception.ArchivoInvalidoException;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.service.ImagenCargoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(ApiRutas.V1 + "/cargos/{cargoId}/imagenes")
@Tag(name = "Firmas del directorio", description =
        "Firma de cada período del directorio. Va atada al período y no a la persona: la firma "
        + "con la que alguien autorizó documentos pertenece a ese mandato.")
public class ImagenCargoController {

    private final ImagenCargoService imagenCargoService;

    public ImagenCargoController(ImagenCargoService imagenCargoService) {
        this.imagenCargoService = imagenCargoService;
    }

    @PostMapping(value = "/{tipo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube la firma",
            description = """
                    `tipo` es FIRMA. Se acepta cualquier tamaño de archivo: la \
                    imagen se guarda como PNG transparente y se reduce a 200 píxeles de lado mayor, conservando la \
                    proporción —una firma apaisada queda 200 de ancho y lo que corresponda de \
                    alto, en vez de estirarse a un cuadrado.

                    Si ya había una, se reemplaza y el archivo anterior se borra del \
                    almacén.""")
    public CargoResponse subir(
            @PathVariable Long cargoId,

            @Parameter(description = "FIRMA, en cualquier combinación de mayúsculas.",
                    example = "firma")
            @PathVariable TipoImagenCargo tipo,

            @Parameter(description = "Archivo de imagen, en cualquier tamaño.", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("archivo") MultipartFile archivo) {

        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoInvalidoException("No llegó ninguna imagen, o vino vacía.");
        }

        try {
            return imagenCargoService.guardar(cargoId, tipo, archivo.getBytes(),
                    archivo.getOriginalFilename());
        } catch (IOException e) {
            throw new ArchivoInvalidoException("No se pudo leer el archivo subido.", e);
        }
    }

    @DeleteMapping("/{tipo}")
    @Operation(summary = "Borra una imagen histórica del cargo",
            description = "Devuelve el período actualizado, no un 204: el cargo sigue "
                    + "existiendo, solo pierde esa imagen.")
    public CargoResponse eliminar(@PathVariable Long cargoId,
                                  @PathVariable TipoImagenCargo tipo) {
        return imagenCargoService.eliminar(cargoId, tipo);
    }
}
