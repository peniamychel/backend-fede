package com.federa.backend.repository;

import com.federa.backend.model.Saludo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaludoRepository extends JpaRepository<Saludo, Long> {
}
