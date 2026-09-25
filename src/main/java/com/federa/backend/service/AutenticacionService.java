package com.federa.backend.service;

import com.federa.backend.dto.LoginResponse;
import com.federa.backend.dto.AccesoRequest;
import com.federa.backend.model.SesionUsuario;
import com.federa.backend.model.Usuario;
import com.federa.backend.repository.SesionUsuarioRepository;
import com.federa.backend.repository.UsuarioRepository;
import com.federa.backend.seguridad.JwtService;
import com.federa.backend.seguridad.CodigoAcceso;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AutenticacionService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder codificador;
    private final JwtService jwtService;
    private final SesionUsuarioRepository sesionRepository;

    public AutenticacionService(UsuarioRepository usuarioRepository,
                                PasswordEncoder codificador,
                                JwtService jwtService,
                                SesionUsuarioRepository sesionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.codificador = codificador;
        this.jwtService = jwtService;
        this.sesionRepository = sesionRepository;
    }

    @Transactional
    public LoginResponse acceder(AccesoRequest peticion) {
        String recibido = peticion.codigo().trim();
        boolean formatoNuevo = CodigoAcceso.esValido(recibido);
        String codigo = formatoNuevo ? recibido : recibido.toUpperCase();
        Usuario usuario;
        if (formatoNuevo) {
            usuario = usuarioRepository
                    .findByCodigoAccesoIdentificador(CodigoAcceso.huella(codigo))
                    .orElse(null);
        } else {
            // Compatibilidad temporal con códigos largos emitidos antes de la
            // migración a cinco letras. Al regenerarlos pasan al formato nuevo.
            int separador = codigo.indexOf('-');
            String identificador = separador > 0 ? codigo.substring(0, separador) : codigo;
            usuario = usuarioRepository
                    .findByCodigoAccesoIdentificadorIgnoreCase(identificador)
                    .orElse(null);
        }
        String hash = usuario != null && usuario.getCodigoAccesoHash() != null
                ? usuario.getCodigoAccesoHash()
                : "$2a$10$invalidoinvalidoinvalidoinvalidoinvalidoinvalidoinvalidoinv";
        boolean coincide = codificador.matches(codigo, hash);
        if (usuario == null || !coincide || !usuario.isEstado()) {
            throw new BadCredentialsException("Código de acceso incorrecto");
        }
        return crearSesion(usuario);
    }

    @Transactional
    public void cerrarSesion(String sesionId) {
        if (sesionId == null) return;
        sesionRepository.findById(sesionId).ifPresent(sesion -> {
            sesion.setRevocada(true);
            sesion.setRevocadaEn(LocalDateTime.now());
            sesionRepository.save(sesion);
        });
    }

    private LoginResponse crearSesion(Usuario usuario) {
        SesionUsuario sesion = new SesionUsuario();
        sesion.setUsuario(usuario);
        sesion.setCreadaEn(LocalDateTime.now());
        sesion.setExpiraEn(LocalDateTime.now().plusSeconds(jwtService.getDuracionSegundos()));
        sesion = sesionRepository.save(sesion);

        List<String> roles = usuario.getRolesAcceso().stream()
                .filter(r -> r.isEstado())
                .map(r -> r.getCodigo())
                .sorted().toList();
        var permisosUnicos = com.federa.backend.seguridad.AutorizacionesUsuario.permisos(usuario);
        String rolLegado = roles.contains("ADMIN") ? "ADMIN"
                : usuario.getRolesAcceso().isEmpty() && !usuario.isPermisosPersonalizados()
                ? usuario.getRol() : "OPERADOR";
        var central = usuario.getCentralAcceso();
        if (central != null) {
            permisosUnicos.retainAll(com.federa.backend.seguridad.AlcanceCentral.PERMISOS);
            roles = List.of("REGISTRO_CENTRAL");
            rolLegado = "OPERADOR";
        }
        return new LoginResponse(
                jwtService.generar(usuario, sesion.getId()),
                jwtService.getDuracionSegundos(),
                usuario.getNombreUsuario(),
                usuario.getNombreCompleto(),
                rolLegado,
                roles,
                List.copyOf(permisosUnicos), central == null ? null : central.getId(),
                central == null ? null : central.getNombre());
    }
}
