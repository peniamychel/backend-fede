package com.federa.backend.seguridad;

import com.federa.backend.model.Usuario;
import com.federa.backend.model.Permiso;
import com.federa.backend.model.RolAcceso;
import com.federa.backend.repository.PermisoRepository;
import com.federa.backend.repository.RolAccesoRepository;
import com.federa.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            PermisoRepository permisoRepository,
            RolAccesoRepository rolRepository,
            PasswordEncoder codificador,
            @Value("${federa.seguridad.usuario-inicial:admin}") String nombre,
            @Value("${federa.seguridad.codigo-acceso-inicial:}") String codigoInicial) {

        return args -> {
            Map<String, Permiso> permisos = CatalogoPermisos.TODOS.stream()
                    .map(def -> permisoRepository.findByCodigo(def.codigo()).orElseGet(() -> {
                        Permiso permiso = new Permiso();
                        permiso.setCodigo(def.codigo()); permiso.setNombre(def.nombre());
                        permiso.setGrupo(def.grupo());
                        return permisoRepository.save(permiso);
                    })).collect(Collectors.toMap(Permiso::getCodigo, p -> p));

            RolAcceso adminRol = rolRepository.findByCodigo("ADMIN").orElseGet(RolAcceso::new);
            adminRol.setCodigo("ADMIN"); adminRol.setNombre("Administrador");
            adminRol.setDescripcion("Acceso completo y administración de usuarios");
            adminRol.setEstado(true); adminRol.setPermisos(new LinkedHashSet<>(permisos.values()));
            adminRol = rolRepository.save(adminRol);

            crearRolSiFalta(rolRepository, "SUPERVISOR", "Supervisor", permisos,
                    Set.of("PRODUCTORES_VER", "PRODUCTORES_EDITAR", "SIE_REVISAR", "IMPORTAR_PADRON",
                            "JERARQUIA_EDITAR", "LOTES_EDITAR", "DIRECTORIOS_EDITAR", "IMAGENES_EDITAR",
                            "INFORMES_DESCARGAR", "CARNETS_IMPRIMIR", "FASES_GESTIONAR", "REUNIONES_GESTIONAR"));
            crearRolSiFalta(rolRepository, "REGISTRO", "Registro de productores", permisos,
                    Set.of("PRODUCTORES_VER", "PRODUCTORES_EDITAR", "SIE_REVISAR", "IMPORTAR_PADRON",
                            "LOTES_EDITAR", "IMAGENES_EDITAR"));
            crearRolSiFalta(rolRepository, "IMPRESION", "Impresión", permisos,
                    Set.of("PRODUCTORES_VER", "INFORMES_DESCARGAR", "CARNETS_IMPRIMIR", "FASES_GESTIONAR"));
            crearRolSiFalta(rolRepository, "REGISTRO_CENTRAL", "Fotos, observaciones y número de lote", permisos,
                    AlcanceCentral.PERMISOS);
            crearRolSiFalta(rolRepository, "CONSULTA", "Solo consulta", permisos,
                    Set.of("PRODUCTORES_VER", "INFORMES_DESCARGAR"));

            // Solo cuando no hay ningún usuario. `contrasena_hash` se conserva
            // por compatibilidad con instalaciones anteriores, pero ya no hay
            // un acceso público por usuario/contraseña: se guarda un valor
            // aleatorio imposible de conocer y se entra con el código.
            Usuario admin = usuarioRepository.findByNombreUsuarioIgnoreCase(nombre).orElse(null);
            if (admin == null && usuarioRepository.count() == 0) {
                admin = new Usuario();
                admin.setNombreUsuario(nombre);
                admin.setContrasenaHash(codificador.encode(
                        java.util.UUID.randomUUID().toString()));
                admin.setNombreCompleto("Administrador");
                admin.setRol("ADMIN");
                admin.setEstado(true);
            }
            if (admin == null) {
                admin = usuarioRepository.findAll().stream()
                        .filter(u -> "ADMIN".equalsIgnoreCase(u.getRol())).findFirst().orElse(null);
            }
            if (admin == null) return;
            admin.setRolesAcceso(new LinkedHashSet<>(Set.of(adminRol)));
            if (admin.getCodigoAccesoHash() == null) {
                boolean generado = codigoInicial == null || codigoInicial.isBlank();
                String codigo = generado ? CodigoAcceso.generar() : codigoInicial.trim();
                if (!CodigoAcceso.esValido(codigo)) {
                    throw new IllegalStateException(
                            "El código inicial debe tener exactamente 5 letras");
                }
                admin.setCodigoAccesoIdentificador(CodigoAcceso.huella(codigo));
                admin.setCodigoAccesoHash(codificador.encode(codigo));
                if (generado) {
                    // En una instalación sin configuración hay que entregar el
                    // primer secreto de alguna manera. Solo se registra una
                    // vez, al crear el acceso; después no puede recuperarse.
                    log.warn("Código de acceso inicial del administrador: {}. "
                            + "Copialo y cambialo desde Administración.", codigo);
                } else {
                    // No repetir en los logs un secreto entregado mediante la
                    // variable ADMIN_CODIGO_ACCESO.
                    log.info("Código de acceso inicial configurado para el administrador.");
                }
            }
            usuarioRepository.save(admin);

        };
    }

    private void crearRolSiFalta(RolAccesoRepository repositorio, String codigo, String nombre,
                                 Map<String, Permiso> catalogo, Set<String> codigos) {
        if (repositorio.findByCodigo(codigo).isPresent()) return;
        RolAcceso rol = new RolAcceso();
        rol.setCodigo(codigo); rol.setNombre(nombre); rol.setEstado(true);
        rol.setPermisos(codigos.stream().map(catalogo::get).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        repositorio.save(rol);
    }
}
