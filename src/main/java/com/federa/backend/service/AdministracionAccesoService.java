package com.federa.backend.service;

import com.federa.backend.dto.AccesoAdministracionDtos.*;
import com.federa.backend.model.*;
import com.federa.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.federa.backend.seguridad.CodigoAcceso;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class AdministracionAccesoService {
    private final UsuarioRepository usuarios;
    private final RolAccesoRepository roles;
    private final PermisoRepository permisos;
    private final SesionUsuarioRepository sesiones;
    private final PasswordEncoder encoder;
    private final CentralRepository centrales;
    private final SindicatoRepository sindicatos;

    public AdministracionAccesoService(UsuarioRepository usuarios, RolAccesoRepository roles,
            PermisoRepository permisos, SesionUsuarioRepository sesiones, PasswordEncoder encoder,
            CentralRepository centrales, SindicatoRepository sindicatos) {
        this.usuarios = usuarios; this.roles = roles; this.permisos = permisos;
        this.sesiones = sesiones; this.encoder = encoder;
        this.centrales = centrales;
        this.sindicatos = sindicatos;
    }

    @Transactional(readOnly = true)
    public List<PermisoDto> listarPermisos() {
        return permisos.findAll().stream().sorted(Comparator.comparing(Permiso::getGrupo)
                .thenComparing(Permiso::getNombre)).map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<RolDto> listarRoles() {
        return roles.findAll().stream().sorted(Comparator.comparing(RolAcceso::getNombre))
                .map(this::dto).toList();
    }

    public RolDto guardarRol(Long id, GuardarRolRequest request) {
        RolAcceso rol = id == null ? new RolAcceso() : buscarRol(id);
        if ("REGISTRO_CENTRAL".equals(rol.getCodigo())
                && !com.federa.backend.seguridad.AlcanceCentral.PERMISOS.containsAll(request.permisos())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este rol solo permite consulta, fotos, observaciones y número de lote");
        }
        if (id == null) rol.setCodigo("ROL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        if ("ADMIN".equalsIgnoreCase(rol.getCodigo()) && !request.activo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El rol Administrador no se puede deshabilitar");
        }
        rol.setNombre(request.nombre().trim());
        rol.setDescripcion(limpiar(request.descripcion()));
        rol.setEstado(request.activo());
        rol.setPermisos(new LinkedHashSet<>(request.permisos().stream()
                .map(c -> permisos.findByCodigo(c).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permiso inexistente: " + c)))
                .toList()));
        return dto(roles.save(rol));
    }

    @Transactional(readOnly = true)
    public List<UsuarioDto> listarUsuarios() {
        return usuarios.findAll().stream().sorted(Comparator.comparing(Usuario::getNombreCompleto,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioDto obtenerUsuario(Long id) { return dto(buscarUsuario(id)); }

    public CodigoGeneradoDto crearUsuario(CrearUsuarioRequest request) {
        Usuario usuario = new Usuario();
        usuario.setNombreCompleto(request.nombreCompleto().trim());
        usuario.setNombreUsuario("acceso-" + UUID.randomUUID().toString().substring(0, 12));
        usuario.setContrasenaHash(encoder.encode(UUID.randomUUID().toString()));
        usuario.setRol("OPERADOR");
        usuario.setEstado(true);
        usuario.setRolesAcceso(resolverRoles(request.roles()));
        asignarCentral(usuario, request.centralId());
        configurarDetalle(usuario, request.todosSindicatos(), request.sindicatoIds(),
                request.permisosPersonalizados(), request.permisos());
        String codigo = asignarNuevoCodigo(usuario);
        usuario = usuarios.save(usuario);
        return new CodigoGeneradoDto(usuario.getId(), codigo);
    }

    public UsuarioDto editarUsuario(Long id, EditarUsuarioRequest request) {
        Usuario usuario = buscarUsuario(id);
        LinkedHashSet<RolAcceso> nuevosRoles = resolverRoles(request.roles());
        asegurarAdministradorDisponible(usuario, request.activo(), nuevosRoles);
        usuario.setNombreCompleto(request.nombreCompleto().trim());
        usuario.setRolesAcceso(nuevosRoles);
        asignarCentral(usuario, request.centralId());
        configurarDetalle(usuario, request.todosSindicatos(), request.sindicatoIds(),
                request.permisosPersonalizados(), request.permisos());
        usuario.setEstado(request.activo());
        revocarSesiones(usuario);
        return dto(usuarios.save(usuario));
    }

    public void eliminarUsuario(Long id) {
        Usuario usuario = buscarUsuario(id);
        if ("ADMIN".equalsIgnoreCase(usuario.getRol()) || usuario.getRolesAcceso().stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getCodigo()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar a un administrador");
        }
        // Elimina también las sesiones expiradas para no dejar referencias al usuario.
        sesiones.eliminarDeUsuario(id);
        usuarios.delete(usuario);
    }

    public CodigoGeneradoDto regenerarCodigo(Long id) {
        Usuario usuario = buscarUsuario(id);
        String codigo = asignarNuevoCodigo(usuario);
        usuarios.save(usuario);
        revocarSesiones(usuario);
        return new CodigoGeneradoDto(id, codigo);
    }

    public CodigoGeneradoDto establecerCodigo(Long id, String codigoSolicitado) {
        Usuario usuario = buscarUsuario(id);
        String codigo = codigoSolicitado == null ? "" : codigoSolicitado.trim();
        if (!CodigoAcceso.esValido(codigo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El código debe tener exactamente 5 letras mayúsculas o minúsculas");
        }
        asignarCodigo(usuario, codigo);
        usuarios.save(usuario);
        revocarSesiones(usuario);
        return new CodigoGeneradoDto(id, codigo);
    }

    private String asignarNuevoCodigo(Usuario usuario) {
        String codigo;
        do { codigo = CodigoAcceso.generar(); }
        while (usuarios.findByCodigoAccesoIdentificador(CodigoAcceso.huella(codigo)).isPresent());
        asignarCodigo(usuario, codigo);
        return codigo;
    }

    private void asignarCodigo(Usuario usuario, String codigo) {
        String huella = CodigoAcceso.huella(codigo);
        Usuario existente = usuarios.findByCodigoAccesoIdentificador(huella).orElse(null);
        if (existente != null && !Objects.equals(existente.getId(), usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ese código ya pertenece a otro usuario");
        }
        usuario.setCodigoAccesoIdentificador(huella);
        usuario.setCodigoAccesoHash(encoder.encode(codigo));
    }

    private LinkedHashSet<RolAcceso> resolverRoles(List<Long> ids) {
        LinkedHashSet<RolAcceso> resultado = new LinkedHashSet<>();
        for (Long id : ids) {
            RolAcceso rol = buscarRol(id);
            if (!rol.isEstado()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El rol " + rol.getNombre() + " está deshabilitado");
            }
            resultado.add(rol);
        }
        return resultado;
    }

    private void revocarSesiones(Usuario usuario) {
        sesiones.revocarActivasDeUsuario(usuario.getId(), LocalDateTime.now());
    }

    private void asegurarAdministradorDisponible(Usuario usuario, boolean seguiraActivo,
                                                  Set<RolAcceso> nuevosRoles) {
        boolean eraAdministrador = usuario.isEstado() && usuario.getRolesAcceso().stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getCodigo()));
        boolean seguiraSiendoAdministrador = seguiraActivo && nuevosRoles.stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getCodigo()));
        if (eraAdministrador && !seguiraSiendoAdministrador
                && usuarios.countByEstadoTrueAndRolesAccesoCodigoIgnoreCase("ADMIN") <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Debe quedar al menos un administrador activo");
        }
    }

    private void asignarCentral(Usuario usuario, Long centralId) {
        if (centralId == null && usuario.getRolesAcceso().stream()
                .anyMatch(r -> "REGISTRO_CENTRAL".equals(r.getCodigo()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rol de fotos, observaciones y lote requiere una central asignada");
        }
        if (centralId != null && usuario.getRolesAcceso().stream().anyMatch(r ->
                "ADMIN".equals(r.getCodigo()) || r.getPermisos().stream().anyMatch(p ->
                !com.federa.backend.seguridad.AlcanceCentral.PERMISOS.contains(p.getCodigo())))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario de central solo puede tener permisos de consulta, fotos, observaciones y número de lote");
        }
        usuario.setCentralAcceso(centralId == null ? null : centrales.findById(centralId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Central inexistente")));
    }

    private void configurarDetalle(Usuario usuario, Boolean todos, List<Long> ids,
                                   Boolean personalizar, List<String> codigos) {
        boolean todosElegidos = todos == null ? usuario.isTodosSindicatos() : todos;
        if (usuario.getCentralAcceso() == null) {
            if (!todosElegidos || (ids != null && !ids.isEmpty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí una central antes de limitar sindicatos");
            }
            usuario.setTodosSindicatos(true);
            usuario.getSindicatosAcceso().clear();
        } else {
            usuario.setTodosSindicatos(todosElegidos);
            if (todosElegidos) usuario.getSindicatosAcceso().clear();
            else {
                var elegidos = ids == null ? usuario.getSindicatosAcceso().stream().map(Sindicato::getId).toList() : ids;
                if (elegidos.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná al menos un sindicato");
                var encontrados = sindicatos.findAllById(new LinkedHashSet<>(elegidos));
                if (encontrados.size() != new HashSet<>(elegidos).size()
                        || encontrados.stream().anyMatch(s -> !s.getCentral().getId().equals(usuario.getCentralAcceso().getId()))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todos los sindicatos deben pertenecer a la central seleccionada");
                }
                usuario.setSindicatosAcceso(new LinkedHashSet<>(encontrados));
            }
        }
        boolean personalizados = personalizar == null ? usuario.isPermisosPersonalizados() : personalizar;
        if (personalizados && usuario.getRolesAcceso().stream().anyMatch(r -> "ADMIN".equals(r.getCodigo()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol Administrador conserva sus permisos completos");
        }
        usuario.setPermisosPersonalizados(personalizados);
        if (!personalizados) usuario.getPermisosAcceso().clear();
        else {
            var seleccion = codigos == null ? usuario.getPermisosAcceso().stream().map(Permiso::getCodigo).toList() : codigos;
            if (usuario.getCentralAcceso() != null && !com.federa.backend.seguridad.AlcanceCentral.PERMISOS.containsAll(seleccion)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los permisos seleccionados no están disponibles para usuarios de central");
            }
            usuario.setPermisosAcceso(seleccion.stream().map(c -> permisos.findByCodigo(c)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permiso inexistente: " + c)))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        }
    }

    private Usuario buscarUsuario(Long id) { return usuarios.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario inexistente")); }
    private RolAcceso buscarRol(Long id) { return roles.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol inexistente")); }
    private String limpiar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private PermisoDto dto(Permiso p) { return new PermisoDto(p.getId(), p.getCodigo(), p.getNombre(), p.getGrupo(), p.getDescripcion()); }
    private RolDto dto(RolAcceso r) { return new RolDto(r.getId(), r.getCodigo(), r.getNombre(), r.getDescripcion(), r.isEstado(),
            r.getPermisos().stream().map(Permiso::getCodigo).sorted().toList()); }
    private UsuarioDto dto(Usuario u) { return new UsuarioDto(u.getId(), u.getNombreCompleto(), u.getCodigoAccesoHash() != null, u.isEstado(),
            u.getRolesAcceso().stream().map(RolAcceso::getCodigo).sorted().toList(),
            u.getCentralAcceso() == null ? null : u.getCentralAcceso().getId(),
            u.getCentralAcceso() == null ? null : u.getCentralAcceso().getNombre(),
            u.isTodosSindicatos(), u.getSindicatosAcceso().stream().map(Sindicato::getId).sorted().toList(),
            u.isPermisosPersonalizados(), u.getPermisosAcceso().stream().map(Permiso::getCodigo).sorted().toList(),
            com.federa.backend.seguridad.AutorizacionesUsuario.permisos(u).stream().sorted().toList()); }
}
