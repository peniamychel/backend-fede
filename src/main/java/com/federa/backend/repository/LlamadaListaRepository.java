package com.federa.backend.repository;

import com.federa.backend.model.LlamadaLista;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LlamadaListaRepository extends JpaRepository<LlamadaLista, Long> {

    List<LlamadaLista> findByReunionIdOrderByNumeroAsc(Long reunionId);

    /** La vuelta que está admitiendo registros, si hay alguna. */
    Optional<LlamadaLista> findByReunionIdAndAbiertaIsTrue(Long reunionId);

    long countByReunionId(Long reunionId);
}
