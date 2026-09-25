package com.federa.backend.repository;

import com.federa.backend.model.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {
    Optional<Permiso> findByCodigo(String codigo);
}
