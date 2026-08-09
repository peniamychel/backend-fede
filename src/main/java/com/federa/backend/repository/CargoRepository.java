package com.federa.backend.repository;

import com.federa.backend.model.Cargo;
import com.federa.backend.model.enums.TipoCargo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CargoRepository extends JpaRepository<Cargo, Long> {

    /** El que ocupa ese cargo ahora en ese sindicato, si hay alguno. */
    Optional<Cargo> findBySindicatoIdAndCargoAndVigenteIsTrue(
            Long sindicatoId, TipoCargo cargo);

    /** Los cargos vigentes de un sindicato: presidente y secretario. */
    List<Cargo> findBySindicatoIdAndVigenteIsTrue(Long sindicatoId);

    /**
     * Historial completo de un sindicato, del período más reciente al más
     * antiguo.
     */
    @Query("""
            select c from Cargo c
            where c.sindicato.id = :sindicatoId
            order by c.desde desc, c.id desc
            """)
    List<Cargo> findHistorialDeSindicato(@Param("sindicatoId") Long sindicatoId);

    /**
     * Cargos que ocupó un productor, en cualquier sindicato.
     * <p>
     * Se consulta por productor y no solo por sindicato porque el padrón mueve
     * gente entre sindicatos: el historial de una persona no se pierde cuando
     * cambia de base.
     */
    @Query("""
            select c from Cargo c
            where c.productor.id = :productorId
            order by c.desde desc, c.id desc
            """)
    List<Cargo> findHistorialDeProductor(@Param("productorId") Long productorId);

    /** Cargos vigentes de un conjunto de sindicatos, para listados. */
    @Query("""
            select c from Cargo c
            where c.sindicato.id in :ids and c.vigente = true
            """)
    List<Cargo> findVigentesDeSindicatos(@Param("ids") List<Long> ids);

    // ------------------------------------------------- central y federación

    Optional<Cargo> findByCentralIdAndCargoAndVigenteIsTrue(Long centralId, TipoCargo cargo);

    List<Cargo> findByCentralIdAndVigenteIsTrue(Long centralId);

    Optional<Cargo> findByFederacionIdAndCargoAndVigenteIsTrue(
            Long federacionId, TipoCargo cargo);

    List<Cargo> findByFederacionIdAndVigenteIsTrue(Long federacionId);

    @Query("""
            select c from Cargo c
            where c.central.id = :centralId
            order by c.desde desc, c.id desc
            """)
    List<Cargo> findHistorialDeCentral(@Param("centralId") Long centralId);

    @Query("""
            select c from Cargo c
            where c.federacion.id = :federacionId
            order by c.desde desc, c.id desc
            """)
    List<Cargo> findHistorialDeFederacion(@Param("federacionId") Long federacionId);

    // ------------------------------------------------------- elegibilidad

    /**
     * El cargo que ocupa hoy un productor, en el nivel que sea.
     * <p>
     * Es la consulta de la que depende la regla de que nadie ocupe dos cargos
     * a la vez. Sirve para negarlo con un mensaje que diga cuál ocupa, en vez
     * de dejar que reviente la clave única con un error de base de datos.
     */
    Optional<Cargo> findByProductorIdAndVigenteIsTrue(Long productorId);

    /**
     * Ids de los productores que hoy ocupan algún cargo, dentro de un conjunto.
     * <p>
     * Se usa para descartarlos de la lista de candidatos sin preguntar uno por
     * uno: en una federación entera son 4.051 consultas evitadas.
     */
    @Query("""
            select c.productor.id from Cargo c
            where c.vigente = true and c.productor.id in :ids
            """)
    List<Long> findProductoresConCargo(@Param("ids") List<Long> ids);

    // ------------------------------------------- convocados a las reuniones

    /**
     * Presidentes y secretarios en funciones de los sindicatos de una central.
     * <p>
     * Son los convocados a una reunión de dirigentes de la central. Se filtra
     * por cargo y no se traen todos los vigentes porque haciendas y vocal no
     * van a esa reunión.
     */
    @Query("""
            select c from Cargo c
            where c.vigente = true
              and c.ambito = com.federa.backend.model.enums.Ambito.SINDICATO
              and c.cargo in :cargos
              and c.sindicato.central.id = :centralId
            order by c.sindicato.nombre, c.cargo
            """)
    List<Cargo> findDirigentesDeSindicatosDeCentral(@Param("centralId") Long centralId,
                                                    @Param("cargos") List<TipoCargo> cargos);

    /**
     * Presidentes y secretarios en funciones de toda la federación: los de sus
     * centrales y los de todos sus sindicatos.
     * <p>
     * Son los convocados a una reunión de dirigentes de la federación, que es
     * la que junta a la dirigencia de arriba y la de abajo.
     */
    /*
     * Los join van explícitos y a la izquierda por un motivo que costó
     * encontrar: escribir la condición como `c.sindicato.central.federacion.id`
     * y `c.central.federacion.id` genera dos INNER JOIN, y como un cargo tiene
     * cargado el sindicato o la central pero nunca los dos, ninguna fila
     * sobrevive a los dos join a la vez. La consulta devolvía siempre vacío.
     */
    @Query("""
            select c from Cargo c
            left join c.sindicato s
            left join s.central sc
            left join sc.federacion sf
            left join c.central cc
            left join cc.federacion cf
            where c.vigente = true
              and c.cargo in :cargos
              and (sf.id = :federacionId or cf.id = :federacionId)
            order by c.ambito, c.cargo
            """)
    List<Cargo> findDirigentesDeFederacion(@Param("federacionId") Long federacionId,
                                           @Param("cargos") List<TipoCargo> cargos);
}
