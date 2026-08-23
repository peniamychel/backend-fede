package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.DirectorioResponse;
import com.federa.backend.exception.ArchivoInvalidoException;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.service.SelloDirectorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Carga el sello institucional del nivel, no el de una persona o un cargo. */
@RestController
@RequestMapping(ApiRutas.V1)
@Tag(name = "Sellos del directorio",
        description = "Un sello por sindicato, central y federación.")
public class SelloDirectorioController {

    private final SelloDirectorioService servicio;

    public SelloDirectorioController(SelloDirectorioService servicio) {
        this.servicio = servicio;
    }

    @PostMapping(value = "/sindicatos/{id}/directorio/sello",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube o reemplaza el sello del sindicato")
    public DirectorioResponse subirSindicato(@PathVariable Long id,
                                              @RequestPart("archivo") MultipartFile archivo) {
        return subir(Ambito.SINDICATO, id, archivo);
    }

    @DeleteMapping("/sindicatos/{id}/directorio/sello")
    public DirectorioResponse eliminarSindicato(@PathVariable Long id) {
        return servicio.eliminar(Ambito.SINDICATO, id);
    }

    @PostMapping(value = "/centrales/{id}/directorio/sello",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube o reemplaza el sello de la central")
    public DirectorioResponse subirCentral(@PathVariable Long id,
                                            @RequestPart("archivo") MultipartFile archivo) {
        return subir(Ambito.CENTRAL, id, archivo);
    }

    @DeleteMapping("/centrales/{id}/directorio/sello")
    public DirectorioResponse eliminarCentral(@PathVariable Long id) {
        return servicio.eliminar(Ambito.CENTRAL, id);
    }

    @PostMapping(value = "/federaciones/{id}/directorio/sello",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube o reemplaza el sello de la federación")
    public DirectorioResponse subirFederacion(@PathVariable Long id,
                                               @RequestPart("archivo") MultipartFile archivo) {
        return subir(Ambito.FEDERACION, id, archivo);
    }

    @DeleteMapping("/federaciones/{id}/directorio/sello")
    public DirectorioResponse eliminarFederacion(@PathVariable Long id) {
        return servicio.eliminar(Ambito.FEDERACION, id);
    }

    private DirectorioResponse subir(Ambito ambito, Long id, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoInvalidoException("No llegó ninguna imagen de sello.");
        }
        try {
            return servicio.guardar(ambito, id, archivo.getBytes());
        } catch (IOException e) {
            throw new ArchivoInvalidoException("No se pudo leer la imagen del sello.", e);
        }
    }
}
