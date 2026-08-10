package com.federa.backend.repository;

import com.federa.backend.model.TenenciaLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenenciaLoteRepository extends JpaRepository<TenenciaLote, Long> {

    /** Quién tiene el lote hoy. */
    Optional<TenenciaLote> findByLoteIdAndVigenteIsTrue(Long loteId);

    /** Historial del lote: quién lo tuvo, del período más reciente al primero. */
    @Query("""
            select t from TenenciaLote t
            where t.lote.id = :loteId
            order by t.desde desc, t.id desc
            """)
    List<TenenciaLote> findHistorialDeLote(@Param("loteId") Long loteId);

    /**
     * Historial de un productor: qué lotes tuvo y cuáles tiene.
     * <p>
     * Sirve para la ficha: alguien que vendió su parcela sigue teniendo un
     * pasado en el padrón, y esconderlo haría imposible resolver un reclamo.
     */
    @Query("""
            select t from TenenciaLote t
            where t.productor.id = :productorId
            order by t.desde desc, t.id desc
            """)
    List<TenenciaLote> findHistorialDeProductor(@Param("productorId") Long productorId);

    long countByProductorIdAndVigenteIsTrue(Long productorId);
}
