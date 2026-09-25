package com.federa.backend.service;

import com.federa.backend.dto.AccesoRequest;
import com.federa.backend.dto.LoginResponse;
import com.federa.backend.model.Permiso;
import com.federa.backend.model.RolAcceso;
import com.federa.backend.model.SesionUsuario;
import com.federa.backend.model.Usuario;
import com.federa.backend.repository.SesionUsuarioRepository;
import com.federa.backend.repository.UsuarioRepository;
import com.federa.backend.seguridad.JwtService;
import com.federa.backend.seguridad.CodigoAcceso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutenticacionServiceTest {
    @Mock UsuarioRepository usuarios;
    @Mock PasswordEncoder codificador;
    @Mock JwtService jwt;
    @Mock SesionUsuarioRepository sesiones;

    @Test
    void codigoValidoCreaSesionConRolesYPermisos() {
        Permiso permiso = new Permiso();
        permiso.setCodigo("PRODUCTORES_VER");
        RolAcceso rol = new RolAcceso();
        rol.setCodigo("CONSULTA");
        rol.setEstado(true);
        rol.setPermisos(Set.of(permiso));
        Usuario usuario = Usuario.builder()
                .nombreUsuario("operador")
                .nombreCompleto("Operador de prueba")
                .codigoAccesoHash("hash")
                .rolesAcceso(Set.of(rol))
                .build();
        usuario.setEstado(true);

        when(usuarios.findByCodigoAccesoIdentificadorIgnoreCase("ABC123"))
                .thenReturn(Optional.of(usuario));
        when(codificador.matches("ABC123-SECRETO", "hash")).thenReturn(true);
        when(jwt.getDuracionSegundos()).thenReturn(3600L);
        when(sesiones.save(any())).thenAnswer(invocacion -> {
            SesionUsuario sesion = invocacion.getArgument(0);
            sesion.setId("sesion-1");
            return sesion;
        });
        when(jwt.generar(usuario, "sesion-1")).thenReturn("token-firmado");

        LoginResponse respuesta = servicio().acceder(new AccesoRequest(" abc123-secreto "));

        assertEquals("token-firmado", respuesta.token());
        assertEquals("operador", respuesta.usuario());
        assertEquals(Set.of("CONSULTA"), Set.copyOf(respuesta.roles()));
        assertEquals(Set.of("PRODUCTORES_VER"), Set.copyOf(respuesta.permisos()));
        verify(sesiones).save(any(SesionUsuario.class));
    }

    @Test
    void codigoIncorrectoNoCreaSesion() {
        when(usuarios.findByCodigoAccesoIdentificadorIgnoreCase("ABC123"))
                .thenReturn(Optional.empty());
        when(codificador.matches(eq("ABC123-MALO"), anyString())).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> servicio().acceder(new AccesoRequest("ABC123-MALO")));
        verifyNoInteractions(sesiones, jwt);
    }

    @Test
    void codigoCortoRespetaMayusculasYMinusculas() {
        Usuario usuario = Usuario.builder()
                .nombreUsuario("operador")
                .codigoAccesoHash("hash-corto")
                .build();
        usuario.setEstado(true);
        String huella = CodigoAcceso.huella("aBcDe");
        when(usuarios.findByCodigoAccesoIdentificador(huella))
                .thenReturn(Optional.of(usuario));
        when(codificador.matches("aBcDe", "hash-corto")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> servicio().acceder(new AccesoRequest("aBcDe")));
        verify(usuarios, never())
                .findByCodigoAccesoIdentificadorIgnoreCase(anyString());
    }

    @Test
    void cerrarSesionLaRevoca() {
        SesionUsuario sesion = new SesionUsuario();
        when(sesiones.findById("sesion-1")).thenReturn(Optional.of(sesion));

        servicio().cerrarSesion("sesion-1");

        assertTrue(sesion.isRevocada());
        assertNotNull(sesion.getRevocadaEn());
        verify(sesiones).save(sesion);
    }

    private AutenticacionService servicio() {
        return new AutenticacionService(usuarios, codificador, jwt, sesiones);
    }
}
