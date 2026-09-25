package com.federa.backend.repository;

import com.federa.backend.model.Productor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface ProductorRepository extends JpaRepository<Productor, Long> {

    @Override
    @Query("select p from Productor p where p.eliminadoEn is null")
    List<Productor> findAll();

    @Override
    @Query("select p from Productor p where p.eliminadoEn is null")
    List<Productor> findAll(Sort sort);

    @Override
    @Query("select p from Productor p where p.eliminadoEn is null")
    Page<Productor> findAll(Pageable pageable);

    @Override
    @Query("select count(p) from Productor p where p.eliminadoEn is null")
    long count();

    @Override
    @Query("select p from Productor p where p.id = :id and p.eliminadoEn is null")
    Optional<Productor> findById(@Param("id") Long id);

    @Override
    @Query("select p from Productor p where p.id in :ids and p.eliminadoEn is null")
    List<Productor> findAllById(@Param("ids") Iterable<Long> ids);

    @Query("select p from Productor p where p.id = :id and p.eliminadoEn is not null")
    Optional<Productor> findEnPapeleraPorId(@Param("id") Long id);

    @Query("""
            select p from Productor p
            where p.eliminadoEn is not null
              and (:centralId is null or p.sindicato.central.id = :centralId)
              and (:limitarSindicatos = false or p.sindicato.id in :sindicatoIds)
            order by p.eliminadoEn desc, p.id desc
            """)
    List<Productor> listarPapelera(@Param("centralId") Long centralId,
                                   @Param("limitarSindicatos") boolean limitarSindicatos,
                                   @Param("sindicatoIds") java.util.Collection<Long> sindicatoIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Productor p where p.id = :id and p.eliminadoEn is null")
    Optional<Productor> findByIdParaRevisionSie(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Productor p where p.id in :ids and p.eliminadoEn is null")
    List<Productor> findAllByIdParaImpresion(@Param("ids") List<Long> ids);

    List<Productor> findBySindicatoIdAndEliminadoEnIsNull(Long sindicatoId);

    default List<Productor> findBySindicatoId(Long sindicatoId) {
        return findBySindicatoIdAndEliminadoEnIsNull(sindicatoId);
    }

    Page<Productor> findBySindicatoCentralIdAndEliminadoEnIsNull(Long centralId, Pageable pageable);

    default Page<Productor> findBySindicatoCentralId(Long centralId, Pageable pageable) {
        return findBySindicatoCentralIdAndEliminadoEnIsNull(centralId, pageable);
    }

    /**
     * Todos los productores de una central, mirando a través de sus
     * sindicatos. Son los candidatos al directorio de esa central.
     */
    List<Productor> findBySindicatoCentralIdAndEliminadoEnIsNullOrderByApellidosAscNombresAsc(Long centralId);

    default List<Productor> findBySindicatoCentralIdOrderByApellidosAscNombresAsc(Long centralId) {
        return findBySindicatoCentralIdAndEliminadoEnIsNullOrderByApellidosAscNombresAsc(centralId);
    }

    /** Ídem para la federación: candidatos a su directorio. */
    List<Productor> findBySindicatoCentralFederacionIdAndEliminadoEnIsNullOrderByApellidosAscNombresAsc(
            Long federacionId);

    default List<Productor> findBySindicatoCentralFederacionIdOrderByApellidosAscNombresAsc(Long federacionId) {
        return findBySindicatoCentralFederacionIdAndEliminadoEnIsNullOrderByApellidosAscNombresAsc(federacionId);
    }

    List<Productor> findBySindicatoIdAndEliminadoEnIsNullOrderByApellidosAscNombresAsc(Long sindicatoId);

    default List<Productor> findBySindicatoIdOrderByApellidosAscNombresAsc(Long sindicatoId) {
        return findBySindicatoIdAndEliminadoEnIsNullOrderByApellidosAscNombresAsc(sindicatoId);
    }

    /** Padrón vigente visible del sindicato: los vetados se administran aparte. */
    @Query("""
            select p from Productor p
            where p.sindicato.id = :sindicatoId
              and p.eliminadoEn is null
              and not exists (
                  select v.id from Veto v
                  where v.productor = p and v.vigente = true
              )
            order by p.apellidos, p.nombres, p.id
            """)
    List<Productor> findNoVetadosBySindicatoIdOrderByApellidosAscNombresAsc(
            @Param("sindicatoId") Long sindicatoId);

    /** Padrón vigente visible de una central, sin productores vetados. */
    @Query("""
            select p from Productor p
            where p.sindicato.central.id = :centralId
              and p.eliminadoEn is null
              and not exists (
                  select v.id from Veto v
                  where v.productor = p and v.vigente = true
              )
            order by p.apellidos, p.nombres, p.id
            """)
    List<Productor> findNoVetadosByCentralIdOrderByApellidosAscNombresAsc(
            @Param("centralId") Long centralId);

    /** Retorna lista porque el padrón trae 27 cédulas repetidas. */
    /** El dueño de un código de credencial. Es lo que se busca al escanear. */
    Optional<Productor> findByCodigoAndEliminadoEnIsNull(String codigo);

    default Optional<Productor> findByCodigo(String codigo) {
        return findByCodigoAndEliminadoEnIsNull(codigo);
    }

    List<Productor> findByCiAndEliminadoEnIsNull(String ci);

    default List<Productor> findByCi(String ci) {
        return findByCiAndEliminadoEnIsNull(ci);
    }

    @Query("select count(p) > 0 from Productor p where p.eliminadoEn is null and upper(trim(p.ci)) = upper(:ci)")
    boolean existeCedulaNormalizada(@Param("ci") String ci);

    @Query("""
            select count(p) > 0 from Productor p
            where p.eliminadoEn is null and p.id <> :id
              and upper(trim(p.ci)) = upper(:ci)
            """)
    boolean existeOtraCedulaActiva(@Param("ci") String ci, @Param("id") Long id);

    @Query("""
            select count(p) > 0 from Productor p
            where p.eliminadoEn is null and p.id <> :id
              and p.sindicato.central.id = :centralId and p.correlativo = :correlativo
            """)
    boolean existeOtroCorrelativoActivo(@Param("centralId") Long centralId,
                                        @Param("correlativo") Integer correlativo,
                                        @Param("id") Long id);

    /** Incluye todo el padrón, también productores deshabilitados y otras centrales. */
    @Query("select p.ci, p.id from Productor p where p.ci is not null and p.eliminadoEn is null")
    List<Object[]> findCedulasParaImportacion();

    long countBySindicatoIdAndEliminadoEnIsNull(Long sindicatoId);

    default long countBySindicatoId(Long sindicatoId) {
        return countBySindicatoIdAndEliminadoEnIsNull(sindicatoId);
    }

    @Query("select count(p) from Productor p where p.sindicato.id = :sindicatoId")
    long countBySindicatoIdIncludingPapelera(@Param("sindicatoId") Long sindicatoId);

    /**
     * Listado principal del padrón. Los tres filtros son opcionales y
     * combinables: buscar un texto dentro de una central, dentro de un
     * sindicato, o en todo el padrón.
     * <p>
     * El texto se contrasta contra las cuatro formas en que se nombra a alguien
     * en la práctica: el nombre, el apellido, la cédula que trae en la mano, y
     * cualquiera de los dos códigos —el de la credencial, que es lo que dice el
     * QR, y el del padrón ({@code 2IVI1}), que es lo que está impreso y lo
     * que la gente lee en voz alta—.
     * <p>
     * El código del padrón no está guardado: se arma con el número de la
     * federación, la sigla de la central y el correlativo. Se arma también acá,
     * en la consulta, y no se guarda en una columna, por lo mismo que en
     * {@code CodigoPadron}: el día que a una central le pongan la sigla, sus
     * productores pasan a ser buscables por código sin tocar una fila.
     */
    @Query("""
            select p from Productor p
              join p.sindicato s
              join s.central c
              join c.federacion f
            where (:sindicatoId is null or s.id = :sindicatoId)
              and (:centralId is null or c.id = :centralId)
              and p.eliminadoEn is null
              and (:limitarSindicatos = false or s.id in :sindicatoIds)
              and not exists (
                  select v.id from Veto v
                  where v.productor = p and v.vigente = true
              )
              and (:texto is null
                   or upper(p.nombres) like :patron
                   or upper(p.apellidos) like :patron
                   or upper(p.nombresCorregidos) like :patron
                   or upper(p.apellidosCorregidos) like :patron
                   or upper(concat(concat(p.nombres, ' '), coalesce(p.apellidos, '')))
                        like :patronNombre
                   or upper(concat(concat(coalesce(p.apellidos, ''), ' '), p.nombres))
                        like :patronNombre
                   or upper(concat(concat(coalesce(p.nombresCorregidos, p.nombres), ' '),
                                           coalesce(p.apellidosCorregidos, p.apellidos, '')))
                        like :patronNombre
                   or upper(concat(concat(coalesce(p.apellidosCorregidos, p.apellidos, ''), ' '),
                                           coalesce(p.nombresCorregidos, p.nombres)))
                        like :patronNombre
                   or p.ci like :patron
                   or upper(p.codigo) like :patron
                   or upper(concat(f.numero, c.abreviatura, p.correlativo))
                        like :patron)
            """)
    Page<Productor> filtrarConAlcance(@Param("sindicatoId") Long sindicatoId,
                            @Param("centralId") Long centralId,
                            @Param("texto") String texto,
                            @Param("patron") String patron,
                            @Param("patronNombre") String patronNombre,
                            @Param("limitarSindicatos") boolean limitarSindicatos,
                            @Param("sindicatoIds") java.util.Collection<Long> sindicatoIds,
                            Pageable pageable);

    default Page<Productor> filtrar(Long sindicatoId, Long centralId, String texto, String patron,
                                    String patronNombre, Pageable pageable) {
        return filtrarConAlcance(sindicatoId, centralId, texto, patron, patronNombre, false, List.of(-1L), pageable);
    }

    /**
     * El correlativo más alto entregado en una central, para saber cuál sigue.
     * <p>
     * Devuelve null si la central no tiene ningún productor numerado todavía,
     * que es el caso de la primera alta: ahí el que sigue es el 1.
     * <p>
     * {@code sindicatoExcluido} deja fuera de la cuenta a un sindicato. Sirve
     * cuando el sindicato acaba de mudarse a esta central y hay que renumerarlo:
     * sus productores ya figuran acá con los números que traían de la central
     * anterior, y contarlos correría el siguiente hacia arriba dejando un hueco.
     */
    @Query("""
            select max(p.correlativo) from Productor p
            where p.sindicato.central.id = :centralId
              and (:sindicatoExcluido is null or p.sindicato.id <> :sindicatoExcluido)
            """)
    Integer maxCorrelativoDeCentral(@Param("centralId") Long centralId,
                                    @Param("sindicatoExcluido") Long sindicatoExcluido);

    /**
     * Los productores de un sindicato, para renumerarlos cuando el sindicato
     * entero se muda a otra central.
     */
    List<Productor> findBySindicatoIdAndEliminadoEnIsNullOrderByIdAsc(Long sindicatoId);

    default List<Productor> findBySindicatoIdOrderByIdAsc(Long sindicatoId) {
        return findBySindicatoIdAndEliminadoEnIsNullOrderByIdAsc(sindicatoId);
    }

    /** Productores sin fotografía cargada: 3.113 en el padrón original. */
    @Query("select p from Productor p where p.eliminadoEn is null and (p.fotoDescripcion is null or p.fotoDescripcion = '')")
    Page<Productor> findSinFoto(Pageable pageable);

    /** Cédulas que aparecen en más de un productor, para depuración. */
    @Query("""
            select p.ci from Productor p
            where p.ci is not null and p.eliminadoEn is null
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
              and p.eliminadoEn is null
            """)
    List<Object[]> findIdentidadesPorFederacion(@Param("federacionId") Long federacionId);

    /** Padrón completo con jerarquía y parcelas actuales para conciliar una nómina externa. */
    @Query("""
            select distinct p from Productor p
              join fetch p.sindicato s
              join fetch s.central c
              join fetch c.federacion f
              left join fetch p.tenencias t
              left join fetch t.lote l
            where p.eliminadoEn is null
            order by p.id
            """)
    List<Productor> findAllParaConciliacionUdestro();
}
