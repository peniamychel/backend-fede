package com.federa.backend.repository;

import com.federa.backend.model.DetalleGrupoImpresionCredencial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DetalleGrupoImpresionCredencialRepository
        extends JpaRepository<DetalleGrupoImpresionCredencial, Long> {

    @Modifying
    @Query("delete from DetalleGrupoImpresionCredencial d where d.productor.id = :productorId")
    void eliminarPorProductor(@Param("productorId") Long productorId);

    List<DetalleGrupoImpresionCredencial> findByGrupoIdOrderByIdAsc(Long grupoId);
}
