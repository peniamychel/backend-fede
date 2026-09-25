package com.federa.backend.repository;

import com.federa.backend.model.RolAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RolAccesoRepository extends JpaRepository<RolAcceso, Long> {
    Optional<RolAcceso> findByCodigo(String codigo);
}
