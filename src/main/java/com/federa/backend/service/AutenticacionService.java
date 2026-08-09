package com.federa.backend.service;

import com.federa.backend.dto.LoginRequest;
import com.federa.backend.dto.LoginResponse;
import com.federa.backend.model.Usuario;
import com.federa.backend.repository.UsuarioRepository;
import com.federa.backend.seguridad.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AutenticacionService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder codificador;
    private final JwtService jwtService;

    public AutenticacionService(UsuarioRepository usuarioRepository,
                                PasswordEncoder codificador,
                                JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.codificador = codificador;
        this.jwtService = jwtService;
    }

    public LoginResponse iniciarSesion(LoginRequest peticion) {
        Usuario usuario = usuarioRepository
                .findByNombreUsuarioIgnoreCase(peticion.usuario())
                .orElse(null);

        // Se compara el hash aunque el usuario no exista, contra un valor
        // descartable. Sin esto, un usuario inexistente respondería mucho más
        // rápido que uno con la contraseña mal, y esa diferencia de tiempo
        // permite averiguar qué usuarios existen.
        String hash = usuario != null
                ? usuario.getContrasenaHash()
                : "$2a$10$invalidoinvalidoinvalidoinvalidoinvalidoinvalidoinvalidoinv";
        boolean coincide = codificador.matches(peticion.contrasena(), hash);

        if (usuario == null || !coincide || !usuario.isEstado()) {
            // Un solo mensaje para los tres casos: decir cuál falló le
            // confirmaría a quien prueba credenciales qué usuarios existen.
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        return new LoginResponse(
                jwtService.generar(usuario),
                jwtService.getDuracionSegundos(),
                usuario.getNombreUsuario(),
                usuario.getNombreCompleto(),
                usuario.getRol());
    }
}
