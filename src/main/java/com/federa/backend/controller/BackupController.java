package com.federa.backend.controller;

import com.federa.backend.backup.BackupInfo;
import com.federa.backend.backup.BackupService;
import com.federa.backend.config.ApiRutas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping(ApiRutas.V1 + "/administracion/backups")
@Tag(name = "Copias de seguridad", description =
        "Respaldos comprimidos de MariaDB. Todos los endpoints exigen rol ADMIN.")
public class BackupController {

    private static final MediaType GZIP = MediaType.parseMediaType("application/gzip");

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping
    @Operation(summary = "Inicia un respaldo manual")
    public ResponseEntity<BackupInfo> crear(Authentication autenticacion) {
        BackupInfo iniciado = backupService.iniciarManual(autenticacion.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(iniciado);
    }

    @GetMapping
    @Operation(summary = "Lista el historial de respaldos")
    public List<BackupInfo> listar() {
        return backupService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta el estado de un respaldo")
    public BackupInfo obtener(@PathVariable String id) {
        return backupService.obtener(id);
    }

    @GetMapping(value = "/{id}/descarga", produces = "application/gzip")
    @Operation(summary = "Descarga un respaldo completado")
    public ResponseEntity<Resource> descargar(@PathVariable String id) throws IOException {
        Path archivo = backupService.archivoParaDescarga(id);
        Resource recurso = new FileSystemResource(archivo);
        return ResponseEntity.ok()
                .contentType(GZIP)
                .contentLength(FilesSize.of(archivo))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archivo.getFileName() + "\"")
                .body(recurso);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina un respaldo que no esté en ejecución")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        backupService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private static final class FilesSize {
        private FilesSize() {
        }

        static long of(Path path) throws IOException {
            return java.nio.file.Files.size(path);
        }
    }
}
