package com.federa.backend.repository;

import com.federa.backend.model.Asistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    Optional<Asistencia> findByLlamadaIdAndProductorId(Long llamadaId, Long productorId);

    long countByLlamadaId(Long llamadaId);

    /** Cuántos se registraron en toda la reunión, sumando sus vueltas. */
    long countByLlamadaReunionId(Long reunionId);

    List<Asistencia> findByLlamadaIdOrderByRegistradaEnAsc(Long llamadaId);

    /**
     * Ids de los presentes en una vuelta, para marcar la lista de convocados sin
     * traer las filas enteras.
     */
    @Query("select a.productor.id from Asistencia a where a.llamada.id = :llamadaId")
    List<Long> findProductoresPresentes(@Param("llamadaId") Long llamadaId);

    /**
     * Ids de los que estuvieron en alguna vuelta de la reunión.
     * <p>
     * Sirve para el resumen: quien vino a la primera y se fue igual estuvo.
     */
    @Query("select distinct a.productor.id from Asistencia a "
            + "where a.llamada.reunion.id = :reunionId")
    List<Long> findProductoresPresentesEnLaReunion(@Param("reunionId") Long reunionId);
}
