package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.exception.ArchivoInvalidoException;
import com.federa.backend.service.ListaFisicaSindicatoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Administra las fotografías originales y el PDF de la lista física. */
@RestController
@RequestMapping(ApiRutas.V1 + "/sindicatos/{sindicatoId}")
@Tag(name = "Sindicatos")
public class ListaFisicaSindicatoController {

    private final ListaFisicaSindicatoService servicio;

    public ListaFisicaSindicatoController(ListaFisicaSindicatoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/lista-fisica")
    @Operation(summary = "Obtiene las páginas de la lista física del sindicato")
    public ListaFisicaSindicatoService.ListaFisica obtener(
            @PathVariable Long sindicatoId) {
        return servicio.obtener(sindicatoId);
    }

    @PostMapping(value = "/lista-fisica/paginas",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Agrega varias fotografías a la lista física",
            description = "Conserva los archivos originales sin recomprimir y regenera el PDF.")
    public ListaFisicaSindicatoService.ListaFisica agregar(
            @PathVariable Long sindicatoId,
            @RequestPart("archivos") List<MultipartFile> archivos) {
        return servicio.agregar(sindicatoId, leer(archivos));
    }

    @PutMapping(value = "/lista-fisica/paginas/{paginaId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Reemplaza una fotografía de la lista física")
    public ListaFisicaSindicatoService.ListaFisica reemplazar(
            @PathVariable Long sindicatoId,
            @PathVariable Long paginaId,
            @RequestPart("archivo") MultipartFile archivo) {
        return servicio.reemplazar(sindicatoId, paginaId, leer(archivo));
    }

    @DeleteMapping("/lista-fisica/paginas/{paginaId}")
    @Operation(summary = "Quita una fotografía y vuelve a numerar las páginas")
    public ListaFisicaSindicatoService.ListaFisica quitar(
            @PathVariable Long sindicatoId,
            @PathVariable Long paginaId) {
        return servicio.quitar(sindicatoId, paginaId);
    }

    @GetMapping(value = "/lista-fisica.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Muestra o descarga el PDF consolidado")
    public ResponseEntity<byte[]> descargar(@PathVariable Long sindicatoId) {
        ListaFisicaSindicatoService.Descarga descarga = servicio.descargarPdf(sindicatoId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + codificarNombre(descarga.nombreArchivo()))
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(descarga.contenido().length)
                .body(descarga.contenido());
    }

    private List<ListaFisicaSindicatoService.ArchivoSubido> leer(
            List<MultipartFile> archivos) {
        if (archivos == null || archivos.isEmpty()) {
            throw new ArchivoInvalidoException("Elegí al menos una fotografía.");
        }
        return archivos.stream().map(this::leer).toList();
    }

    private ListaFisicaSindicatoService.ArchivoSubido leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoInvalidoException("Una de las fotografías llegó vacía.");
        }
        try {
            return new ListaFisicaSindicatoService.ArchivoSubido(
                    archivo.getBytes(), archivo.getOriginalFilename(), archivo.getContentType());
        } catch (IOException e) {
            throw new ArchivoInvalidoException("No se pudo leer una de las fotografías.", e);
        }
    }

    private String codificarNombre(String nombre) {
        StringBuilder resultado = new StringBuilder();
        for (byte valor : nombre.getBytes(StandardCharsets.UTF_8)) {
            int caracter = valor & 0xff;
            if ((caracter >= 'a' && caracter <= 'z')
                    || (caracter >= 'A' && caracter <= 'Z')
                    || (caracter >= '0' && caracter <= '9')
                    || caracter == '-' || caracter == '_' || caracter == '.') {
                resultado.append((char) caracter);
            } else {
                resultado.append('%').append(String.format("%02X", caracter));
            }
        }
        return resultado.toString();
    }
}
