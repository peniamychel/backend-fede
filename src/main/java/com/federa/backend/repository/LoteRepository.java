package com.federa.backend.repository;

import com.federa.backend.model.Lote;
import com.federa.backend.model.enums.EstadoLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    /**
     * Los lotes de un sindicato.
     * <p>
     * Antes esto navegaba {@code l.productor.sindicato.id}, y era una respuesta
     * indirecta a la pregunta equivocada: preguntaba dónde está el productor
     * para deducir dónde está la tierra. Ahora el lote sabe su sindicato.
     */
    List<Lote> findBySindicatoIdOrderByNumeroAscExtensionAsc(Long sindicatoId);

    /** Los que tiene hoy un productor, por su período de tenencia abierto. */
    @Query("""
            select t.lote from TenenciaLote t
            where t.productor.id = :productorId and t.vigente = true
            order by t.lote.numero, t.lote.extension
            """)
    List<Lote> findVigentesDeProductor(@Param("productorId") Long productorId);

    long countByEstadoLote(EstadoLote estadoLote);

    /**
     * Números que se repiten dentro del sindicato: el hallazgo más frecuente de
     * la revisión del padrón, 425 casos.
     */
    @Query("""
            select l.numero from Lote l
            where l.sindicato.id = :sindicatoId
              and l.numero is not null
            group by l.numero, l.extension
            having count(l) > 1
            """)
    List<String> findNumerosDuplicadosEnSindicato(@Param("sindicatoId") Long sindicatoId);

    /**
     * Para el informe: qué lotes tiene hoy cada productor del sindicato, en una
     * sola consulta.
     * <p>
     * Devuelve (productorId, numero, extension). Va por la tenencia vigente
     * porque es la que dice de quién es el lote hoy, que es lo que se imprime.
     */
    @Query("""
            select t.productor.id, l.numero, l.extension
              from TenenciaLote t join t.lote l
             where l.sindicato.id = :sindicatoId and t.vigente = true
             order by l.numero, l.extension
            """)
    List<Object[]> findIdentificacionesPorSindicato(@Param("sindicatoId") Long sindicatoId);

    @Query("select l from Lote l where l.estadoLote = com.federa.backend.model.enums.EstadoLote.DESCONOCIDO")
    List<Lote> findConEstadoDesconocido();

    /** Los que ya tienen punto marcado, para dibujarlos todos en un mapa. */
    @Query("""
            select l from Lote l
            where l.sindicato.id = :sindicatoId
              and l.latitud is not null and l.longitud is not null
            order by l.numero, l.extension
            """)
    List<Lote> findConUbicacion(@Param("sindicatoId") Long sindicatoId);

    /** Lotes del sindicato que hoy no tiene nadie. */
    @Query("""
            select l from Lote l
            where l.sindicato.id = :sindicatoId
              and not exists (select 1 from TenenciaLote t
                               where t.lote = l and t.vigente = true)
            order by l.numero, l.extension
            """)
    List<Lote> findSinTenedor(@Param("sindicatoId") Long sindicatoId);
}
