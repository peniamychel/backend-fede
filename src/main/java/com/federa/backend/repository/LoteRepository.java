package com.federa.backend.repository;

import com.federa.backend.model.Lote;
import com.federa.backend.model.enums.EstadoLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    List<Lote> findByProductorId(Long productorId);

    List<Lote> findByProductorSindicatoId(Long sindicatoId);

    long countByEstadoLote(EstadoLote estadoLote);

    /**
     * Números de lote repetidos dentro de un mismo sindicato: el hallazgo más
     * frecuente de la revisión del padrón (425 casos).
     */
    @Query("""
            select l.numero from Lote l
            where l.productor.sindicato.id = :sindicatoId
              and l.numero is not null
            group by l.numero, l.extension
            having count(l) > 1
            """)
    List<String> findNumerosDuplicadosEnSindicato(@Param("sindicatoId") Long sindicatoId);

    /**
     * Lotes de un sindicato como {productorId, número, extensión}, para armar
     * la columna N° LOTE del informe.
     * <p>
     * Es una proyección y no una lista de entidades porque el informe recorre
     * cientos de productores: traer los lotes uno por uno sería una consulta
     * por fila.
     */
    @Query("""
            select l.productor.id, l.numero, l.extension from Lote l
            where l.productor.sindicato.id = :sindicatoId
            order by l.numero, l.extension
            """)
    List<Object[]> findIdentificacionesPorSindicato(@Param("sindicatoId") Long sindicatoId);

    /** Lotes cuyo estado del padrón no encajó en ninguna categoría conocida. */
    @Query("select l from Lote l where l.estadoLote = com.federa.backend.model.enums.EstadoLote.DESCONOCIDO")
    List<Lote> findConEstadoDesconocido();
}
