package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.ImagenResponse;
import com.federa.backend.dto.ImagenSubidaResponse;
import com.federa.backend.dto.RecorteRequest;
import com.federa.backend.exception.ArchivoInvalidoException;
import com.federa.backend.service.ImagenProductorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(ApiRutas.V1 + "/productores/{productorId}/imagenes")
@Tag(name = "Imágenes de productores", description =
        "Fotografía del productor. Se sube una sola imagen, del tamaño que sea, y el servidor "
        + "guarda dos variantes derivadas: la foto reducida y una miniatura para los listados.")
public class ProductorImagenController {

    private final ImagenProductorService imagenService;

    public ProductorImagenController(ImagenProductorService imagenService) {
        this.imagenService = imagenService;
    }

    @GetMapping
    @Operation(summary = "Variantes que tiene cargadas el productor",
            description = "Devuelve la metadata y la URL de cada archivo. Los bytes se piden a "
                    + "esa URL, que la sirve el controlador de archivos.")
    public List<ImagenResponse> listar(@PathVariable Long productorId) {
        return imagenService.listar(productorId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube la foto del productor",
            description = """
                    Se sube **una sola** imagen y el servidor deriva las dos variantes que \
                    guarda: la foto cuadrada PNG optimizada hacia 300 KB, conservando transparencia, \
                    y una miniatura PNG para los listados.

                    No hay límite de tamaño para el archivo que se sube más allá del tope de \
                    cordura del servidor: si la foto pesa varios megas, se reduce al guardarla. \
                    Pedirle al usuario que la comprima antes sería trasladarle un trabajo que \
                    la máquina hace mejor.

                    El formato se acepta si ImageIO puede abrirlo —JPG, PNG, WebP y los JPEG en \
                    CMYK de algunas cámaras—; las fotos de productor se guardan como PNG. Si el productor ya tenía \
                    foto, se reemplaza.""")
    public ImagenSubidaResponse subir(
            @PathVariable Long productorId,

            @Parameter(description = "Archivo de imagen, en cualquier tamaño.", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("archivo") MultipartFile archivo,

            @Parameter(description = "Borde izquierdo del recorte, en píxeles de la imagen "
                    + "subida. Los cuatro parámetros de recorte van juntos o no van.",
                    example = "820")
            @RequestParam(required = false) Integer recorteX,

            @Parameter(description = "Borde superior del recorte.", example = "410")
            @RequestParam(required = false) Integer recorteY,

            @Parameter(description = "Ancho del recorte.", example = "1800")
            @RequestParam(required = false) Integer recorteAncho,

            @Parameter(description = "Alto del recorte.", example = "2400")
            @RequestParam(required = false) Integer recorteAlto) {

        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoInvalidoException("No llegó ninguna imagen, o vino vacía.");
        }

        RecorteRequest recorte = armarRecorte(recorteX, recorteY, recorteAncho, recorteAlto);

        try {
            return imagenService.guardar(productorId, archivo.getBytes(),
                    archivo.getOriginalFilename(), recorte);
        } catch (IOException e) {
            throw new ArchivoInvalidoException("No se pudo leer el archivo subido.", e);
        }
    }

    /**
     * Arma el recorte a partir de los cuatro parámetros, o devuelve null si no
     * vino ninguno.
     * <p>
     * Se rechaza el caso a medias en vez de completar lo que falte con ceros:
     * un recorte con tres valores es un error del cliente, y adivinar el cuarto
     * produciría un encuadre que nadie pidió.
     */
    private RecorteRequest armarRecorte(Integer x, Integer y, Integer ancho, Integer alto) {
        boolean ninguno = x == null && y == null && ancho == null && alto == null;
        if (ninguno) {
            return null;
        }
        boolean todos = x != null && y != null && ancho != null && alto != null;
        if (!todos) {
            throw new ArchivoInvalidoException(
                    "Para recortar hacen falta los cuatro valores: recorteX, recorteY, "
                    + "recorteAncho y recorteAlto.");
        }
        return new RecorteRequest(x, y, ancho, alto);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borra la foto del productor",
            description = "Se van las dos variantes juntas: no se suben por separado, así que "
                    + "tampoco tiene sentido borrarlas por separado.")
    public void eliminar(@PathVariable Long productorId) {
        imagenService.eliminar(productorId);
    }
}
