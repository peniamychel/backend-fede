package com.federa.backend.repository;

import com.federa.backend.model.ProductorFaseImpresion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductorFaseImpresionRepository
        extends JpaRepository<ProductorFaseImpresion, Long> {

    Optional<ProductorFaseImpresion> findByFaseIdAndProductorId(Long faseId, Long productorId);

    @Query("""
            select pf from ProductorFaseImpresion pf
              join fetch pf.productor p
              join fetch p.sindicato s
            where pf.fase.id = :faseId
            order by s.nombre, p.apellidos, p.nombres, p.id
            """)
    List<ProductorFaseImpresion> findTodosDeFase(@Param("faseId") Long faseId);

    @Query("""
            select pf from ProductorFaseImpresion pf
              join fetch pf.fase f
              join fetch pf.productor p
              join fetch p.sindicato s
            where f.central.id = :centralId and f.numero <= :numeroFase
            order by f.numero, s.nombre, p.apellidos, p.nombres, p.id
            """)
    List<ProductorFaseImpresion> findImpresionesHastaFase(
            @Param("centralId") Long centralId,
            @Param("numeroFase") int numeroFase);

    @Query("""
            select pf from ProductorFaseImpresion pf
              join fetch pf.fase f
              join fetch pf.productor p
              join fetch p.sindicato s
            where f.central.id = :centralId and f.numero <= :numeroFase
              and s.id = :sindicatoId
            order by f.numero, p.apellidos, p.nombres, p.id
            """)
    List<ProductorFaseImpresion> findImpresionesHastaFaseSindicato(
            @Param("centralId") Long centralId,
            @Param("numeroFase") int numeroFase,
            @Param("sindicatoId") Long sindicatoId);

    List<ProductorFaseImpresion> findByFaseIdAndPendienteTrue(Long faseId);
}
