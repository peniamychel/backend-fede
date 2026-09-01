package com.federa.backend.repository;

import com.federa.backend.model.GrupoImpresionCredencial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GrupoImpresionCredencialRepository
        extends JpaRepository<GrupoImpresionCredencial, Long> {

    Optional<GrupoImpresionCredencial> findFirstBySindicatoIdOrderByEnviadoEnDescIdDesc(
            Long sindicatoId);
}
