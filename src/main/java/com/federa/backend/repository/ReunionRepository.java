package com.federa.backend.repository;

import com.federa.backend.model.Reunion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReunionRepository extends JpaRepository<Reunion, Long> {

    /**
     * Reuniones de un nivel, de la más reciente a la más antigua.
     * <p>
     * Los tres parámetros son excluyentes: se pasa el del nivel que interesa y
     * los otros dos en null. Es una sola consulta y no tres métodos porque la
     * pantalla filtra por lo que el usuario tenga elegido.
     */
    @Query("""
            select r from Reunion r
            where (:sindicatoId is null or r.sindicato.id = :sindicatoId)
              and (:centralId is null or r.central.id = :centralId)
              and (:federacionId is null or r.federacion.id = :federacionId)
            order by r.fecha desc, r.id desc
            """)
    List<Reunion> filtrar(@Param("sindicatoId") Long sindicatoId,
                          @Param("centralId") Long centralId,
                          @Param("federacionId") Long federacionId);
}
