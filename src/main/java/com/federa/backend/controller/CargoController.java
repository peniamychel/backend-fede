package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.CargoResponse;
import com.federa.backend.dto.PieFirmaRequest;
import com.federa.backend.service.DirectorioService;
import com.federa.backend.service.CredencialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

/**
 * Lo que cuelga de un período del directorio.
 * <p>
 * La ruta es {@code /cargos/{id}} y no {@code /sindicatos/x/directorio/...}
 * porque el período existe por sí mismo: sobrevive al relevo, y su credencial
 * y sus imágenes se piden por su id sin saber de qué nivel salió.
 */
@RestController
@RequestMapping(ApiRutas.V1 + "/cargos")
@Tag(name = "Cargos", description =
        "Un período del directorio. Acá cuelgan su credencial y, cuando el cargo firma, "
        + "su imagen de firma y su pie de firma textual.")
public class CargoController {

    private final CredencialService credencialService;
    private final DirectorioService directorioService;

    public CargoController(CredencialService credencialService,
                           DirectorioService directorioService) {
        this.credencialService = credencialService;
        this.directorioService = directorioService;
    }

    @PatchMapping("/{id}/pie-firma")
    @Operation(summary = "Guarda el pie de firma como texto",
            description = "Reemplaza a la antigua imagen de pie de firma. "
                    + "Admite varias líneas y hasta 200 caracteres.")
    public CargoResponse actualizarPieFirma(@PathVariable Long id,
                                             @Valid @RequestBody PieFirmaRequest peticion) {
        return directorioService.actualizarPieFirma(id, peticion.pieFirma());
    }

    @GetMapping(value = "/{id}/credencial.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga la credencial del dirigente",
            description = """
                    Dos páginas del tamaño de una cédula (54 × 85,6 mm) pero **en vertical**, al \
                    revés que la del productor: así se distingue de un vistazo una credencial de \
                    dirigente de una de afiliado, sin tener que leerlas. Mide lo mismo, así que \
                    entra en el mismo portacredencial.

                    El anverso lleva la foto, el cargo destacado y a qué sindicato, central o \
                    federación corresponde. El reverso lleva la firma y el sello del propio \
                    titular: quien recibe un documento firmado por él puede contrastar la firma \
                    contra la credencial.

                    Sirve también para períodos ya cerrados, como constancia de que ocupó el \
                    cargo; el reverso dice entre qué fechas.""")
    public ResponseEntity<byte[]> credencial(@PathVariable Long id) {
        return ProductorController.comoAdjunto(credencialService.generarDeCargo(id));
    }
}
