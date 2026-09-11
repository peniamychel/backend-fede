package com.federa.backend.repository;

import com.federa.backend.model.FilaConciliacionUdestro;
import com.federa.backend.model.enums.AccionConciliacionUdestro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FilaConciliacionUdestroRepository
        extends JpaRepository<FilaConciliacionUdestro, Long> {

    List<FilaConciliacionUdestro> findByConciliacionIdOrderByIdAsc(Long conciliacionId);

    Page<FilaConciliacionUdestro> findByConciliacionId(Long conciliacionId, Pageable pageable);

    Page<FilaConciliacionUdestro> findByConciliacionIdAndAccion(
            Long conciliacionId, AccionConciliacionUdestro accion, Pageable pageable);

    long countByConciliacionIdAndAccion(Long conciliacionId, AccionConciliacionUdestro accion);

    Optional<FilaConciliacionUdestro> findByIdAndConciliacionId(Long id, Long conciliacionId);
}
