package com.federa.backend.seguridad;

import com.federa.backend.model.*;
import com.federa.backend.repository.SesionUsuarioRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JwtAlcanceCentralTest {
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void usaLaCentralDeLaSesionYLimitaLosPermisosAunqueUnRolSeHayaAmpliado() throws Exception {
        var jwt = mock(JwtService.class);
        var sesiones = mock(SesionUsuarioRepository.class);
        var claims = mock(Claims.class);
        when(jwt.validar("token")).thenReturn(claims);
        when(claims.get("sid", String.class)).thenReturn("sesion");
        var federacion = new Federacion(); federacion.setId(1L);
        var central = new Central(); central.setId(3L); central.setFederacion(federacion);
        var foto = new Permiso(); foto.setCodigo("FOTOS_PRODUCTORES_EDITAR");
        var sie = new Permiso(); sie.setCodigo("SIE_REVISAR");
        var rol = new RolAcceso(); rol.setCodigo("ADMIN"); rol.setEstado(true);
        rol.setPermisos(Set.of(foto, sie));
        var usuario = new Usuario(); usuario.setNombreUsuario("fotografo"); usuario.setEstado(true);
        usuario.setCentralAcceso(central); usuario.setRolesAcceso(Set.of(rol));
        var sesion = new SesionUsuario(); sesion.setUsuario(usuario);
        when(sesiones.findByIdAndRevocadaFalseAndExpiraEnAfter(eq("sesion"), any()))
                .thenReturn(Optional.of(sesion));
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/yo");
        request.addHeader("Authorization", "Bearer token");
        new JwtFiltro(jwt, sesiones).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertEquals(3L, AlcanceCentral.id());
            assertEquals(1L, AlcanceCentral.actual().federacionId());
            var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                    .stream().map(Object::toString).toList();
            assertEquals(java.util.List.of("FOTOS_PRODUCTORES_EDITAR"), authorities);
        });
    }
    @Test void permisosIndividualesSinPrivilegiosDeRespaldoYConSindicatosSeleccionados() throws Exception {
        var jwt = mock(JwtService.class);
        var sesiones = mock(SesionUsuarioRepository.class);
        var claims = mock(Claims.class);
        when(jwt.validar("token")).thenReturn(claims);
        when(claims.get("sid", String.class)).thenReturn("sesion");
        var f = new Federacion(); f.setId(1L);
        var c = new Central(); c.setId(3L); c.setFederacion(f);
        var s = new Sindicato(); s.setId(7L); s.setCentral(c);
        var foto = new Permiso(); foto.setCodigo("FOTOS_PRODUCTORES_EDITAR");
        var ver = new Permiso(); ver.setCodigo("PRODUCTORES_VER");
        var rol = new RolAcceso(); rol.setCodigo("REGISTRO_CENTRAL"); rol.setEstado(true);
        rol.setPermisos(Set.of(foto, ver));
        var u = new Usuario(); u.setNombreUsuario("fotografo"); u.setEstado(true);
        u.setRol("ADMIN"); u.setRolesAcceso(Set.of(rol));
        u.setCentralAcceso(c); u.setTodosSindicatos(false); u.setSindicatosAcceso(Set.of(s));
        u.setPermisosPersonalizados(true); u.setPermisosAcceso(Set.of(ver));
        var sesion = new SesionUsuario(); sesion.setUsuario(u);
        when(sesiones.findByIdAndRevocadaFalseAndExpiraEnAfter(eq("sesion"), any()))
                .thenReturn(Optional.of(sesion));
        for (boolean vacio : java.util.List.of(false, true)) {
            SecurityContextHolder.clearContext();
            if (vacio) { u.setPermisosAcceso(Set.of()); u.setCentralAcceso(null); }
            var request = new MockHttpServletRequest("GET", "/api/v1/auth/yo");
            request.addHeader("Authorization", "Bearer token");
            new JwtFiltro(jwt, sesiones).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                        .stream().map(Object::toString).toList();
                assertEquals(vacio ? java.util.List.of() : java.util.List.of("PRODUCTORES_VER"), authorities);
                if (!vacio) {
                    assertFalse(AlcanceCentral.actual().todosSindicatos());
                    assertEquals(Set.of(7L), AlcanceCentral.actual().sindicatoIds());
                }
            });
        }
    }
}
