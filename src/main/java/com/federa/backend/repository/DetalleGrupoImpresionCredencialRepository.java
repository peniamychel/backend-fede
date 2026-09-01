package com.federa.backend.repository;

import com.federa.backend.model.DetalleGrupoImpresionCredencial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetalleGrupoImpresionCredencialRepository
        extends JpaRepository<DetalleGrupoImpresionCredencial, Long> {

    List<DetalleGrupoImpresionCredencial> findByGrupoIdOrderByIdAsc(Long grupoId);
}
