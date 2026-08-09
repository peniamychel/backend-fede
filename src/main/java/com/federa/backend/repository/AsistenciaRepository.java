package com.federa.backend.repository;

import com.federa.backend.model.Asistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    Optional<Asistencia> findByReunionIdAndProductorId(Long reunionId, Long productorId);

    long countByReunionId(Long reunionId);

    List<Asistencia> findByReunionIdOrderByRegistradaEnAsc(Long reunionId);

    /**
     * Ids de los presentes, para marcar la lista de convocados sin traer las
     * filas enteras.
     */
    @Query("select a.productor.id from Asistencia a where a.reunion.id = :reunionId")
    List<Long> findProductoresPresentes(@Param("reunionId") Long reunionId);
}
