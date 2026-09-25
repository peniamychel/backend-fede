package com.federa.backend.repository;

import com.federa.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByNombreUsuarioIgnoreCase(String nombreUsuario);

    boolean existsByNombreUsuarioIgnoreCase(String nombreUsuario);

    Optional<Usuario> findByCodigoAccesoIdentificadorIgnoreCase(String identificador);

    Optional<Usuario> findByCodigoAccesoIdentificador(String identificador);

    long countByEstadoTrueAndRolesAccesoCodigoIgnoreCase(String codigo);
}
