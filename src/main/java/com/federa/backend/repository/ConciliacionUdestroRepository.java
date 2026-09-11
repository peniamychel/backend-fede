package com.federa.backend.repository;

import com.federa.backend.model.ConciliacionUdestro;
import com.federa.backend.model.enums.EstadoConciliacionUdestro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ConciliacionUdestroRepository extends JpaRepository<ConciliacionUdestro, Long> {

    Optional<ConciliacionUdestro> findFirstByFaseOrderByCreatedAtDesc(
            EstadoConciliacionUdestro fase);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select c from ConciliacionUdestro c where c.id = :id")
    Optional<ConciliacionUdestro> findByIdParaActualizar(
            @org.springframework.data.repository.query.Param("id") Long id);
}
