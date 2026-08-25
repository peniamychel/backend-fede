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

    long countBySindicatoId(Long sindicatoId);

    /**
     * Listado principal del padrón. Los tres filtros son opcionales y
     * combinables: buscar un texto dentro de una central, dentro de un
     * sindicato, o en todo el padrón.
     * <p>
     * El texto se contrasta contra las cuatro formas en que se nombra a alguien
     * en la práctica: el nombre, el apellido, la cédula que trae en la mano, y
     * cualquiera de los dos códigos —el de la credencial, que es lo que dice el
     * QR, y el del padrón ({@code 2-IVI-1}), que es lo que está impreso y lo
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
                   or upper(p.codigo) = upper(:texto)
                   or upper(concat(f.numero, '-', c.abreviatura, '-', p.correlativo))
                        = upper(:texto))
            """)
    Page<Productor> filtrar(@Param("sindicatoId") Long sindicatoId,
                            @Param("centralId") Long centralId,
                            @Param("texto") String texto,
                            @Param("patron") String patron,
                            @Param("patronNombre") String patronNombre,
                            Pageable pageable);

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
    List<Productor> findBySindicatoIdOrderByIdAsc(Long sindicatoId);

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
}
