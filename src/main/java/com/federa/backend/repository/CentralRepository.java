package com.federa.backend.repository;

import com.federa.backend.model.Central;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CentralRepository extends JpaRepository<Central, Long> {

    List<Central> findByFederacionIdOrderByNombreAsc(Long federacionId);

    /**
     * El nombre solo es único dentro de la federación, igual que el del
     * sindicato lo es dentro de su central.
     */
    Optional<Central> findByFederacionIdAndNombreIgnoreCase(Long federacionId, String nombre);

    /** Para avisar de la sigla repetida antes de que la rechace la clave única. */
    Optional<Central> findByAbreviatura(String abreviatura);

    long countByFederacionId(Long federacionId);

    /** Serializa la entrega de correlativos dentro de una misma central. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Central c where c.id = :id")
    Optional<Central> findByIdParaNumerar(@Param("id") Long id);
}
