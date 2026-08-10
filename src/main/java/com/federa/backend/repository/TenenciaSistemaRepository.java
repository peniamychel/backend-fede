package com.federa.backend.repository;

import com.federa.backend.model.TenenciaSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenenciaSistemaRepository extends JpaRepository<TenenciaSistema, Long> {

    /** Dónde está el sistema hoy. */
    Optional<TenenciaSistema> findBySistemaIdAndVigenteIsTrue(Long sistemaId);

    /** Qué sistema tiene el lote hoy, si tiene. */
    Optional<TenenciaSistema> findByLoteIdAndVigenteIsTrue(Long loteId);

    /** Por dónde pasó el sistema, del período más reciente al primero. */
    @Query("""
            select t from TenenciaSistema t
            where t.sistema.id = :sistemaId
            order by t.desde desc, t.id desc
            """)
    List<TenenciaSistema> findHistorialDeSistema(@Param("sistemaId") Long sistemaId);

    /** Qué sistemas pasaron por el lote. */
    @Query("""
            select t from TenenciaSistema t
            where t.lote.id = :loteId
            order by t.desde desc, t.id desc
            """)
    List<TenenciaSistema> findHistorialDeLote(@Param("loteId") Long loteId);

    /**
     * Los sistemas vigentes de un conjunto de lotes, para listados.
     * <p>
     * En bloque y no uno por lote: la lista de un sindicato puede tener cientos
     * de parcelas y preguntar por cada una sería una consulta por fila.
     */
    @Query("""
            select t from TenenciaSistema t
            where t.vigente = true and t.lote.id in :loteIds
            """)
    List<TenenciaSistema> findVigentesDeLotes(@Param("loteIds") List<Long> loteIds);
}
