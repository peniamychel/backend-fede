package com.federa.backend.repository;

import com.federa.backend.model.Central;
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

    /** Para avisar del número repetido antes de que lo rechace la clave única. */
    Optional<Central> findByNumero(String numero);

    long countByFederacionId(Long federacionId);
}
