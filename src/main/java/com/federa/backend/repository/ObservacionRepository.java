package com.federa.backend.repository;

import com.federa.backend.model.Observacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ObservacionRepository extends JpaRepository<Observacion, Long> {

    List<Observacion> findByProductorIdOrderByIdAsc(Long productorId);

    long countByProductorIdAndResueltaFalse(Long productorId);

    long countByResueltaFalse();

    /**
     * Observaciones sin resolver de un sindicato como {productorId, mensaje},
     * para la columna OBSERVACIONES del informe.
     * <p>
     * Solo las pendientes: el informe se entrega para que se corrijan, y una
     * observación ya resuelta no da trabajo a nadie.
     */
    @Query("""
            select o.productor.id, o.mensaje from Observacion o
            where o.productor.sindicato.id = :sindicatoId
              and o.resuelta = false
            order by o.id
            """)
    List<Object[]> findPendientesPorSindicato(@Param("sindicatoId") Long sindicatoId);

    /**
     * Listado principal. Los filtros son opcionales y combinables: las
     * pendientes de un sindicato, las que mencionan "foto", las de un
     * productor puntual.
     */
    @Query("""
            select o from Observacion o
            where (:productorId is null or o.productor.id = :productorId)
              and (:sindicatoId is null or o.productor.sindicato.id = :sindicatoId)
              and (:centralId is null or o.productor.sindicato.central.id = :centralId)
              and (:soloPendientes = false or o.resuelta = false)
              and (:texto is null or upper(o.mensaje) like upper(concat('%', :texto, '%')))
            """)
    Page<Observacion> filtrar(@Param("productorId") Long productorId,
                              @Param("sindicatoId") Long sindicatoId,
                              @Param("centralId") Long centralId,
                              @Param("soloPendientes") boolean soloPendientes,
                              @Param("texto") String texto,
                              Pageable pageable);
}
