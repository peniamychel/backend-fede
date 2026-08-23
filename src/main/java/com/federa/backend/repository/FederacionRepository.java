package com.federa.backend.repository;

import com.federa.backend.model.Federacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FederacionRepository extends JpaRepository<Federacion, Long> {

    Optional<Federacion> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    /** Para avisar del número repetido antes de que lo rechace la clave única. */
    Optional<Federacion> findByNumero(String numero);
}
