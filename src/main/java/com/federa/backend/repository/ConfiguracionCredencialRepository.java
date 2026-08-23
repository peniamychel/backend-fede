package com.federa.backend.repository;

import com.federa.backend.model.ConfiguracionCredencial;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionCredencialRepository
        extends JpaRepository<ConfiguracionCredencial, Long> {
}
