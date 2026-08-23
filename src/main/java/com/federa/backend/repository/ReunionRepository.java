package com.federa.backend.repository;

import com.federa.backend.model.Reunion;
import com.federa.backend.model.enums.TipoReunion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReunionRepository extends JpaRepository<Reunion, Long> {

    /**
     * Reuniones de un nivel, de la más reciente a la más antigua.
     * <p>
     * Los tres primeros parámetros son excluyentes: se pasa el del nivel que
     * interesa y los otros dos en null. Es una sola consulta y no tres métodos
     * porque la pantalla filtra por lo que el usuario tenga elegido.
     * <p>
     * El {@code texto} entra por dos caminos distintos, y a propósito, porque
     * son las dos formas en que alguien busca una asamblea meses después:
     * <ul>
     *   <li><b>Por lo que fue</b>: el título, el lugar, las notas, o el número
     *       del acta en el libro.</li>
     *   <li><b>Por a quién se vetó ahí</b>: su nombre, su cédula, cualquiera de
     *       sus dos códigos, o el motivo que quedó escrito. «¿En qué reunión
     *       vetaron a Fulano?» es una pregunta que se hace sola, y sin esto
     *       habría que abrir asamblea por asamblea hasta encontrarla.</li>
     * </ul>
     * Cuenta tanto la reunión que impuso el veto como la que lo levantó: las
     * dos decidieron sobre esa persona.
     */
    @Query("""
            select distinct r from Reunion r
            where (:sindicatoId is null or r.sindicato.id = :sindicatoId)
              and (:centralId is null or r.central.id = :centralId)
              and (:federacionId is null or r.federacion.id = :federacionId)
              and (:tipo is null or r.tipo = :tipo)
              and (:texto is null
                   or upper(r.titulo) like upper(concat('%', :texto, '%'))
                   or upper(r.lugar) like upper(concat('%', :texto, '%'))
                   or upper(r.observaciones) like upper(concat('%', :texto, '%'))
                   or upper(r.codigoActa) like upper(concat('%', :texto, '%'))
                   or exists (
                       select v from Veto v
                         join v.productor p
                         join p.sindicato s
                         join s.central c
                         join c.federacion f
                       where (v.reunion = r or v.reunionLevanta = r)
                         and (upper(p.nombres) like upper(concat('%', :texto, '%'))
                              or upper(p.apellidos) like upper(concat('%', :texto, '%'))
                              or p.ci like concat('%', :texto, '%')
                              or upper(p.codigo) = upper(:texto)
                              or upper(concat(f.numero, '-', c.abreviatura, '-', p.correlativo))
                                   = upper(:texto)
                              or upper(v.motivo) like upper(concat('%', :texto, '%')))))
            order by r.fecha desc, r.id desc
            """)
    List<Reunion> filtrar(@Param("sindicatoId") Long sindicatoId,
                          @Param("centralId") Long centralId,
                          @Param("federacionId") Long federacionId,
                          @Param("tipo") TipoReunion tipo,
                          @Param("texto") String texto);

    /**
     * Solo el tipo de cada reunión que pasa el filtro, sin el tipo aplicado.
     * <p>
     * Es para contar cuántas hay en cada solapa. Va aparte y trayendo únicamente
     * la columna del tipo porque armar la respuesta de una reunión cuesta caro
     * —hay que calcular a cuántos convoca, que es otra consulta por cada una— y
     * para contar no hace falta nada de eso.
     * <p>
     * <b>El filtro es el mismo que el de {@link #filtrar}, escrito de nuevo.</b>
     * JPQL no deja compartirlo, así que la prueba
     * {@code el conteo por tipo coincide con lo que trae cada solapa} los ata:
     * si uno cambia y el otro no, falla.
     */
    @Query("""
            select r.tipo from Reunion r
            where (:sindicatoId is null or r.sindicato.id = :sindicatoId)
              and (:centralId is null or r.central.id = :centralId)
              and (:federacionId is null or r.federacion.id = :federacionId)
              and (:texto is null
                   or upper(r.titulo) like upper(concat('%', :texto, '%'))
                   or upper(r.lugar) like upper(concat('%', :texto, '%'))
                   or upper(r.observaciones) like upper(concat('%', :texto, '%'))
                   or upper(r.codigoActa) like upper(concat('%', :texto, '%'))
                   or exists (
                       select v from Veto v
                         join v.productor p
                         join p.sindicato s
                         join s.central c
                         join c.federacion f
                       where (v.reunion = r or v.reunionLevanta = r)
                         and (upper(p.nombres) like upper(concat('%', :texto, '%'))
                              or upper(p.apellidos) like upper(concat('%', :texto, '%'))
                              or p.ci like concat('%', :texto, '%')
                              or upper(p.codigo) = upper(:texto)
                              or upper(concat(f.numero, '-', c.abreviatura, '-', p.correlativo))
                                   = upper(:texto)
                              or upper(v.motivo) like upper(concat('%', :texto, '%')))))
            """)
    List<TipoReunion> tiposQueCoinciden(@Param("sindicatoId") Long sindicatoId,
                                        @Param("centralId") Long centralId,
                                        @Param("federacionId") Long federacionId,
                                        @Param("texto") String texto);
}
