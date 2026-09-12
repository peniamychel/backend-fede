package com.federa.backend.repository;

import com.federa.backend.model.FaseImpresionCarnet;
import com.federa.backend.model.enums.EstadoFaseImpresionCarnet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FaseImpresionCarnetRepository extends JpaRepository<FaseImpresionCarnet, Long> {

    Optional<FaseImpresionCarnet> findFirstByCentralIdAndEstadoOrderByNumeroDesc(
            Long centralId, EstadoFaseImpresionCarnet estado);

    Optional<FaseImpresionCarnet> findFirstByCentralIdOrderByNumeroDesc(Long centralId);

    List<FaseImpresionCarnet> findByCentralIdOrderByNumeroDesc(Long centralId);

    List<FaseImpresionCarnet> findByCentralIdOrderByNumeroAsc(Long centralId);
}
