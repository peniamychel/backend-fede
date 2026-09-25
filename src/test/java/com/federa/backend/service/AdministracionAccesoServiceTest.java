package com.federa.backend.service;

import com.federa.backend.dto.AccesoAdministracionDtos.EditarUsuarioRequest;
import com.federa.backend.dto.AccesoAdministracionDtos.GuardarRolRequest;
import com.federa.backend.seguridad.CodigoAcceso;
import com.federa.backend.model.RolAcceso;
import com.federa.backend.model.Usuario;
import com.federa.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdministracionAccesoServiceTest {
    @Mock UsuarioRepository usuarios;
    @Mock RolAccesoRepository roles;
    @Mock PermisoRepository permisos;
    @Mock SesionUsuarioRepository sesiones;
    @Mock PasswordEncoder encoder;
    @Mock CentralRepository centrales;
    @Mock SindicatoRepository sindicatos;

    @Test
    void noPermiteDeshabilitarAlUltimoAdministrador() {
        RolAcceso admin = rol(1L, "ADMIN", true);
        RolAcceso consulta = rol(2L, "CONSULTA", true);
        Usuario usuario = usuario(9L, true, admin);
        when(usuarios.findById(9L)).thenReturn(Optional.of(usuario));
        when(roles.findById(2L)).thenReturn(Optional.of(consulta));
        when(usuarios.countByEstadoTrueAndRolesAccesoCodigoIgnoreCase("ADMIN"))
                .thenReturn(1L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> servicio().editarUsuario(9L,
                        new EditarUsuarioRequest("Administrador", List.of(2L), true, null)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(usuarios, never()).save(any());
    }

    @Test
    void deshabilitarUsuarioRevocaTodasSusSesiones() {
        RolAcceso consulta = rol(2L, "CONSULTA", true);
        Usuario usuario = usuario(9L, true, consulta);
        when(usuarios.findById(9L)).thenReturn(Optional.of(usuario));
        when(roles.findById(2L)).thenReturn(Optional.of(consulta));
        when(usuarios.save(usuario)).thenReturn(usuario);

        servicio().editarUsuario(9L,
                new EditarUsuarioRequest("Consulta", List.of(2L), false, null));

        assertFalse(usuario.isEstado());
        verify(sesiones).revocarActivasDeUsuario(eq(9L), any());
    }

    @Test
    void rolAdministradorNoPuedeDeshabilitarse() {
        RolAcceso admin = rol(1L, "ADMIN", true);
        when(roles.findById(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> servicio().guardarRol(1L,
                        new GuardarRolRequest("Administrador", null,
                                List.of("PRODUCTORES_VER"), false)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(roles, never()).save(any());
    }

    @Test
    void permiteAsignarCodigoManualDeCincoLetrasYRevocaSesiones() {
        Usuario usuario = usuario(9L, true, rol(2L, "CONSULTA", true));
        when(usuarios.findById(9L)).thenReturn(Optional.of(usuario));
        when(usuarios.findByCodigoAccesoIdentificador(CodigoAcceso.huella("aBcDe")))
                .thenReturn(Optional.empty());
        when(encoder.encode("aBcDe")).thenReturn("hash");
        when(usuarios.save(usuario)).thenReturn(usuario);

        var respuesta = servicio().establecerCodigo(9L, "aBcDe");

        assertEquals("aBcDe", respuesta.codigoAcceso());
        assertEquals(CodigoAcceso.huella("aBcDe"),
                usuario.getCodigoAccesoIdentificador());
        assertEquals("hash", usuario.getCodigoAccesoHash());
        verify(sesiones).revocarActivasDeUsuario(eq(9L), any());
    }

    private AdministracionAccesoService servicio() {
        return new AdministracionAccesoService(
                usuarios, roles, permisos, sesiones, encoder, centrales, sindicatos);
    }

    @Test void eliminarUsuarioEliminaSusSesionesPrimero() {
        var u = usuario(9L, true, rol(2L, "CONSULTA", true));
        when(usuarios.findById(9L)).thenReturn(Optional.of(u));
        servicio().eliminarUsuario(9L);
        var orden = inOrder(sesiones, usuarios);
        orden.verify(sesiones).eliminarDeUsuario(9L);
        orden.verify(usuarios).delete(u);
    }

    @Test void noEliminaAdministradoresAunqueEstenDeshabilitados() {
        var u = usuario(9L, false, rol(1L, "ADMIN", false));
        when(usuarios.findById(9L)).thenReturn(Optional.of(u));
        assertThrows(ResponseStatusException.class, () -> servicio().eliminarUsuario(9L));
        u.setRolesAcceso(Set.of());
        u.setRol("ADMIN");
        assertThrows(ResponseStatusException.class, () -> servicio().eliminarUsuario(9L));
        verify(sesiones, never()).eliminarDeUsuario(any());
        verify(usuarios, never()).delete(any());
    }

    @Test void eliminarInexistenteDevuelveNoEncontrado() {
        when(usuarios.findById(99L)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> servicio().eliminarUsuario(99L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(sesiones, never()).eliminarDeUsuario(any());
    }

    @Test
    void usuarioDeCentralDebeElegirUnaCentralYNoPuedeRecibirAdmin() {
        var registro = rol(4L, "REGISTRO_CENTRAL", true);
        when(roles.findById(4L)).thenReturn(Optional.of(registro));
        assertThrows(ResponseStatusException.class, () -> servicio().crearUsuario(
                new com.federa.backend.dto.AccesoAdministracionDtos.CrearUsuarioRequest(
                        "Fotógrafo", List.of(4L), null)));
        var admin = rol(1L, "ADMIN", true);
        when(roles.findById(1L)).thenReturn(Optional.of(admin));
        assertThrows(ResponseStatusException.class, () -> servicio().crearUsuario(
                new com.federa.backend.dto.AccesoAdministracionDtos.CrearUsuarioRequest(
                        "Fotógrafo", List.of(1L), 3L)));
        verify(usuarios, never()).save(any());
    }

    @Test
    void cambiarCentralRevocaLasSesionesAnteriores() {
        var registro = rol(4L, "REGISTRO_CENTRAL", true);
        Usuario usuario = usuario(9L, true, registro);
        var central = new com.federa.backend.model.Central();
        central.setId(3L); central.setNombre("13 DE JUNIO");
        when(usuarios.findById(9L)).thenReturn(Optional.of(usuario));
        when(roles.findById(4L)).thenReturn(Optional.of(registro));
        when(centrales.findById(3L)).thenReturn(Optional.of(central));
        when(usuarios.save(usuario)).thenReturn(usuario);
        var resultado = servicio().editarUsuario(9L,
                new EditarUsuarioRequest("Fotógrafo", List.of(4L), true, 3L));
        assertEquals(3L, resultado.centralId());
        assertEquals("13 DE JUNIO", resultado.centralNombre());
        verify(sesiones).revocarActivasDeUsuario(eq(9L), any());
    }

    @Test
    void guardaVariosSindicatosPermisosPropiosYRevocaSesiones() {
        var registro = rol(4L, "REGISTRO_CENTRAL", true);
        var ver = new com.federa.backend.model.Permiso(); ver.setCodigo("PRODUCTORES_VER");
        var foto = new com.federa.backend.model.Permiso(); foto.setCodigo("FOTOS_PRODUCTORES_EDITAR");
        registro.setPermisos(Set.of(ver, foto));
        var u = usuario(9L, true, registro);
        var c = new com.federa.backend.model.Central(); c.setId(3L);
        var s1 = new com.federa.backend.model.Sindicato(); s1.setId(7L); s1.setCentral(c);
        var s2 = new com.federa.backend.model.Sindicato(); s2.setId(8L); s2.setCentral(c);
        when(usuarios.findById(9L)).thenReturn(Optional.of(u));
        when(roles.findById(4L)).thenReturn(Optional.of(registro));
        when(centrales.findById(3L)).thenReturn(Optional.of(c));
        when(sindicatos.findAllById(any())).thenReturn(List.of(s1, s2));
        when(permisos.findByCodigo("PRODUCTORES_VER")).thenReturn(Optional.of(ver));
        when(usuarios.save(u)).thenReturn(u);
        var dto = servicio().editarUsuario(9L, new EditarUsuarioRequest("Fotos", List.of(4L), true, 3L,
                false, List.of(7L, 8L), true, List.of("PRODUCTORES_VER")));
        assertFalse(dto.todosSindicatos());
        assertEquals(List.of(7L, 8L), dto.sindicatoIds());
        assertEquals(List.of("PRODUCTORES_VER"), dto.permisosEfectivos());
        assertEquals(2, registro.getPermisos().size(), "No modifica el rol compartido");
        verify(sesiones).revocarActivasDeUsuario(eq(9L), any());

        var todos = servicio().editarUsuario(9L, new EditarUsuarioRequest("Fotos", List.of(4L), true, 3L,
                true, List.of(), false, List.of()));
        assertTrue(todos.todosSindicatos());
        assertTrue(todos.sindicatoIds().isEmpty());
        assertEquals(2, todos.permisosEfectivos().size());
    }

    @Test
    void rechazaSindicatosDeOtraCentralYSeleccionVacia() {
        var registro = rol(4L, "REGISTRO_CENTRAL", true);
        var u = usuario(9L, true, registro);
        var c = new com.federa.backend.model.Central(); c.setId(3L);
        var otra = new com.federa.backend.model.Central(); otra.setId(4L);
        var s = new com.federa.backend.model.Sindicato(); s.setId(7L); s.setCentral(otra);
        when(usuarios.findById(9L)).thenReturn(Optional.of(u));
        when(roles.findById(4L)).thenReturn(Optional.of(registro));
        when(centrales.findById(3L)).thenReturn(Optional.of(c));
        assertThrows(ResponseStatusException.class, () -> servicio().editarUsuario(9L,
                new EditarUsuarioRequest("Fotos", List.of(4L), true, 3L, false, List.of(), false, List.of())));
        when(sindicatos.findAllById(any())).thenReturn(List.of(s));
        assertThrows(ResponseStatusException.class, () -> servicio().editarUsuario(9L,
                new EditarUsuarioRequest("Fotos", List.of(4L), true, 3L, false, List.of(7L), false, List.of())));
        verify(usuarios, never()).save(any());
    }

    @Test
    void personalizarNoPermiteSieParaUnaCentral() {
        var registro = rol(4L, "REGISTRO_CENTRAL", true);
        var u = usuario(9L, true, registro);
        var c = new com.federa.backend.model.Central(); c.setId(3L);
        when(usuarios.findById(9L)).thenReturn(Optional.of(u));
        when(roles.findById(4L)).thenReturn(Optional.of(registro));
        when(centrales.findById(3L)).thenReturn(Optional.of(c));
        assertThrows(ResponseStatusException.class, () -> servicio().editarUsuario(9L,
                new EditarUsuarioRequest("Fotos", List.of(4L), true, 3L, true, List.of(), true, List.of("SIE_REVISAR"))));
        verify(usuarios, never()).save(any());
    }

    private RolAcceso rol(Long id, String codigo, boolean activo) {
        RolAcceso rol = new RolAcceso();
        rol.setId(id);
        rol.setCodigo(codigo);
        rol.setNombre(codigo);
        rol.setEstado(activo);
        return rol;
    }

    private Usuario usuario(Long id, boolean activo, RolAcceso... rolesAcceso) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombreCompleto("Usuario");
        usuario.setEstado(activo);
        usuario.setRolesAcceso(Set.of(rolesAcceso));
        return usuario;
    }
}
