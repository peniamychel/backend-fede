package com.federa.backend.repository;

import com.federa.backend.model.Sistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SistemaRepository extends JpaRepository<Sistema, Long> {

    Optional<Sistema> findByCodigoIgnoreCase(String codigo);

    List<Sistema> findAllByOrderByCodigoAsc();

    /** Los que hoy no están en ningún lote: se pueden asignar. */
    @Query("""
            select s from Sistema s
            where not exists (select 1 from TenenciaSistema t
                               where t.sistema = s and t.vigente = true)
            order by s.codigo
            """)
    List<Sistema> findDisponibles();

    /** Los sistemas instalados en los lotes de un sindicato. */
    @Query("""
            select s from Sistema s join s.tenencias t
            where t.vigente = true and t.lote.sindicato.id = :sindicatoId
            order by s.codigo
            """)
    List<Sistema> findEnSindicato(@Param("sindicatoId") Long sindicatoId);
}
