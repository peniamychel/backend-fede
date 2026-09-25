package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.AccesoAdministracionDtos.*;
import com.federa.backend.service.AdministracionAccesoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping(ApiRutas.V1 + "/administracion/accesos")
public class AdministracionAccesoController {
    private final AdministracionAccesoService servicio;
    public AdministracionAccesoController(AdministracionAccesoService servicio) { this.servicio = servicio; }

    @GetMapping("/permisos") public List<PermisoDto> permisos() { return servicio.listarPermisos(); }
    @GetMapping("/roles") public List<RolDto> roles() { return servicio.listarRoles(); }
    @PostMapping("/roles") public RolDto crearRol(@Valid @RequestBody GuardarRolRequest r) { return servicio.guardarRol(null, r); }
    @PutMapping("/roles/{id}") public RolDto editarRol(@PathVariable Long id, @Valid @RequestBody GuardarRolRequest r) { return servicio.guardarRol(id, r); }
    @GetMapping("/usuarios") public List<UsuarioDto> usuarios() { return servicio.listarUsuarios(); }
    @GetMapping("/usuarios/{id}") public UsuarioDto usuario(@PathVariable Long id) { return servicio.obtenerUsuario(id); }
    @DeleteMapping("/usuarios/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void eliminarUsuario(@PathVariable Long id) { servicio.eliminarUsuario(id); }
    @PostMapping("/usuarios") public CodigoGeneradoDto crearUsuario(@Valid @RequestBody CrearUsuarioRequest r) { return servicio.crearUsuario(r); }
    @PutMapping("/usuarios/{id}") public UsuarioDto editarUsuario(@PathVariable Long id, @Valid @RequestBody EditarUsuarioRequest r) { return servicio.editarUsuario(id, r); }
    @PostMapping("/usuarios/{id}/codigo") public CodigoGeneradoDto regenerar(@PathVariable Long id) { return servicio.regenerarCodigo(id); }
    @PostMapping("/usuarios/{id}/codigo-manual")
    public CodigoGeneradoDto codigoManual(@PathVariable Long id,
            @Valid @RequestBody CodigoManualRequest r) {
        return servicio.establecerCodigo(id, r.codigoAcceso());
    }
}
