package com.federa.backend.repository;

import com.federa.backend.model.Productor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductorRepository extends JpaRepository<Productor, Long> {

    List<Productor> findBySindicatoId(Long sindicatoId);

    Page<Productor> findBySindicatoCentralId(Long centralId, Pageable pageable);

    /**
     * Todos los productores de una central, mirando a través de sus
     * sindicatos. Son los candidatos al directorio de esa central.
     */
    List<Productor> findBySindicatoCentralIdOrderByApellidosAscNombresAsc(Long centralId);

    /** Ídem para la federación: candidatos a su directorio. */
    List<Productor> findBySindicatoCentralFederacionIdOrderByApellidosAscNombresAsc(
            Long federacionId);

    List<Productor> findBySindicatoIdOrderByApellidosAscNombresAsc(Long sindicatoId);

    /** Retorna lista porque el padrón trae 27 cédulas repetidas. */
    /** El dueño de un código de credencial. Es lo que se busca al escanear. */
    Optional<Productor> findByCodigo(String codigo);

    List<Productor> findByCi(String ci);

    /** Retorna lista porque el padrón trae 208 carnés repetidos. */
    List<Productor> findByCarnetProductor(String carnetProductor);

    long countBySindicatoId(Long sindicatoId);

    /**
     * Listado principal del padrón. Los tres filtros son opcionales y
     * combinables: buscar un texto dentro de una central, dentro de un
     * sindicato, o en todo el padrón.
     */
    @Query("""
            select p from Productor p
            where (:sindicatoId is null or p.sindicato.id = :sindicatoId)
              and (:centralId is null or p.sindicato.central.id = :centralId)
              and (:texto is null
                   or upper(p.nombres) like upper(concat('%', :texto, '%'))
                   or upper(p.apellidos) like upper(concat('%', :texto, '%'))
                   or p.ci like concat('%', :texto, '%')
                   or p.carnetProductor like concat('%', :texto, '%'))
            """)
    Page<Productor> filtrar(@Param("sindicatoId") Long sindicatoId,
                            @Param("centralId") Long centralId,
                            @Param("texto") String texto,
                            Pageable pageable);

    /** Productores sin fotografía cargada: 3.113 en el padrón original. */
    @Query("select p from Productor p where p.fotoDescripcion is null or p.fotoDescripcion = ''")
    Page<Productor> findSinFoto(Pageable pageable);

    /** Cédulas que aparecen en más de un productor, para depuración. */
    @Query("""
            select p.ci from Productor p
            where p.ci is not null
            group by p.ci
            having count(p) > 1
            """)
    List<String> findCedulasDuplicadas();

    /**
     * Identidad (sindicato, nombres, apellidos) de los productores ya cargados
     * en una federación.
     * <p>
     * La usa la importación para avisar que una planilla se está subiendo por
     * segunda vez. Devuelve solo esas tres columnas y no las entidades enteras
     * porque son 4.051 filas y lo único que hace falta es comparar.
     */
    @Query("""
            select p.sindicato.id, p.nombres, p.apellidos from Productor p
            where p.sindicato.central.federacion.id = :federacionId
            """)
    List<Object[]> findIdentidadesPorFederacion(@Param("federacionId") Long federacionId);

    /** Carnés de productor asignados a más de una persona. */
    @Query("""
            select p.carnetProductor from Productor p
            where p.carnetProductor is not null
            group by p.carnetProductor
            having count(p) > 1
            """)
    List<String> findCarnetsDuplicados();
}
