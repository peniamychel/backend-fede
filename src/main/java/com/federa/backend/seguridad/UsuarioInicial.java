package com.federa.backend.seguridad;

import com.federa.backend.model.Usuario;
import com.federa.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Crea el primer usuario si la tabla está vacía.
 * <p>
 * Sin esto no habría forma de entrar la primera vez: el único endpoint abierto
 * sería el de login y no existiría ninguna credencial válida.
 */
@Configuration
public class UsuarioInicial {

    private static final Logger log = LoggerFactory.getLogger(UsuarioInicial.class);

    @Bean
    ApplicationRunner crearUsuarioInicial(
            UsuarioRepository usuarioRepository,
            PasswordEncoder codificador,
            @Value("${federa.seguridad.usuario-inicial:admin}") String nombre,
            @Value("${federa.seguridad.contrasena-inicial:admin}") String contrasena) {

        return args -> {
            // Solo cuando no hay ningún usuario: si ya existen, tocar algo acá
            // podría pisar una contraseña que alguien cambió a propósito.
            if (usuarioRepository.count() > 0) {
                return;
            }

            Usuario admin = new Usuario();
            admin.setNombreUsuario(nombre);
            admin.setContrasenaHash(codificador.encode(contrasena));
            admin.setNombreCompleto("Administrador");
            admin.setRol("ADMIN");
            admin.setEstado(true);
            usuarioRepository.save(admin);

            log.warn("""
                    Se creó el usuario inicial '{}' con la contraseña por defecto.
                    Cambiala antes de exponer esto fuera de tu máquina: una contraseña
                    conocida en un sistema con datos personales no es una contraseña.""",
                    nombre);
        };
    }
}
