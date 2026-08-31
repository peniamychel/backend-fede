package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.DisenoCredencial;
import com.federa.backend.service.DisenoCredencialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

@RestController
@RequestMapping(ApiRutas.V1 + "/configuracion/credencial")
@Tag(name = "Diseño de credenciales")
public class DisenoCredencialController {

    private final DisenoCredencialService service;

    public DisenoCredencialController(DisenoCredencialService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Obtiene el diseño y el catálogo de campos disponibles")
    public DisenoCredencial.Editor obtener() {
        return service.editor();
    }

    @PutMapping
    @Operation(summary = "Guarda el diseño que usarán la vista previa y los PDF")
    public DisenoCredencial.Editor guardar(@RequestBody DisenoCredencial diseno) {
        return service.guardar(diseno);
    }

    @PutMapping("/restablecer")
    @Operation(summary = "Restablece el diseño original")
    public DisenoCredencial.Editor restablecer() {
        return service.restablecer();
    }

    @PostMapping(value = "/plantilla/{cara}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Reemplaza la imagen de fondo de una cara de la credencial")
    public DisenoCredencial.Editor guardarPlantilla(
            @PathVariable DisenoCredencial.Cara cara,
            @RequestPart("archivo") MultipartFile archivo) throws IOException {
        return service.guardarPlantilla(cara, archivo.getBytes());
    }

    @PostMapping(value = "/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube una imagen para insertarla como objeto del diseño")
    public DisenoCredencialService.ImagenPersonalizada guardarImagen(
            @RequestPart("archivo") MultipartFile archivo) throws IOException {
        return service.guardarImagen(archivo.getBytes());
    }

    @GetMapping("/plantilla/{cara}")
    @Operation(summary = "Descarga la plantilla personalizada de una cara")
    public ResponseEntity<byte[]> plantilla(@PathVariable DisenoCredencial.Cara cara) {
        DisenoCredencialService.PlantillaArchivo plantilla = service.plantilla(cara);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(plantilla.tipoMime()))
                .body(plantilla.contenido());
    }

    @DeleteMapping("/plantilla/{cara}")
    @Operation(summary = "Vuelve a usar la plantilla incluida para una cara")
    public DisenoCredencial.Editor restablecerPlantilla(
            @PathVariable DisenoCredencial.Cara cara) {
        return service.restablecerPlantilla(cara);
    }
}
