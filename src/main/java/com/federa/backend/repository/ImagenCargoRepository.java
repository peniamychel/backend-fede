package com.federa.backend.repository;

import com.federa.backend.model.ImagenCargo;
import com.federa.backend.model.enums.TipoImagenCargo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImagenCargoRepository extends JpaRepository<ImagenCargo, Long> {

    Optional<ImagenCargo> findByCargoIdAndTipo(Long cargoId, TipoImagenCargo tipo);

    List<ImagenCargo> findByCargoId(Long cargoId);

    /**
     * Imágenes de un conjunto de cargos, en una sola consulta.
     * <p>
     * El historial de un sindicato puede tener decenas de períodos: recorrer la
     * relación de cada uno haría una consulta por fila.
     */
    @Query("""
            select i.cargo.id, i.tipo, i.clave from ImagenCargo i
            where i.cargo.id in :ids
            """)
    List<Object[]> findClavesPorCargos(@Param("ids") List<Long> ids);

    /** Claves de los cargos de un productor, para borrar sus archivos. */
    @Query("""
            select i.clave from ImagenCargo i
            where i.cargo.productor.id = :productorId
            """)
    List<String> findClavesPorProductor(@Param("productorId") Long productorId);
}
