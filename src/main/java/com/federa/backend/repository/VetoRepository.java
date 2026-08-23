package com.federa.backend.repository;

import com.federa.backend.model.Veto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VetoRepository extends JpaRepository<Veto, Long> {

    /** El veto abierto de un productor, si tiene. */
    Optional<Veto> findByProductorIdAndVigenteIsTrue(Long productorId);

    /** Todo lo que le pasó a esa persona: los vetos abiertos y los levantados. */
    List<Veto> findByProductorIdOrderByDesdeDesc(Long productorId);

    /** Los vetos que impuso una reunión. */
    List<Veto> findByReunionIdOrderByDesdeDesc(Long reunionId);

    /** Los que esa reunión levantó. */
    List<Veto> findByReunionLevantaId(Long reunionId);

    /**
     * Todo lo que esa reunión decidió sobre vetos: los que impuso y los que
     * levantó.
     * <p>
     * Van juntos porque en una asamblea se hacen las dos cosas, a veces en la
     * misma sesión, y quien después lee lo que pasó ahí quiere las dos listas.
     * Cuál es cuál se sabe mirando si la reunión figura como la que vetó o como
     * la que levantó.
     */
    @Query("""
            select v from Veto v
            where v.reunion.id = :reunionId or v.reunionLevanta.id = :reunionId
            order by v.desde desc
            """)
    List<Veto> decididosEn(@Param("reunionId") Long reunionId);

    /**
     * Búsqueda de vetados.
     * <p>
     * Un solo endpoint para las formas en que se pregunta en la práctica: con
     * la cédula en la mano, con cualquiera de los dos códigos —el de la
     * credencial, que es lo que dice el QR, y el del padrón ({@code 2-IVI-1}),
     * que es lo que está impreso—, por nombre y apellido cuando no hay papel, o
     * mirando el sindicato entero.
     * <p>
     * Es la misma forma de buscar que en el padrón, y a propósito: quien va a
     * levantar un veto en la asamblea busca a la persona igual que quien la
     * vetó, y tener dos búsquedas distintas para el mismo acto sería una
     * trampa.
     * <p>
     * {@code vigentes} en true deja solo los que están vetados hoy, que es lo
     * que se pregunta al controlar a alguien; en false trae también los
     * levantados, que es el historial.
     */
    @Query("""
            select v from Veto v
              join v.productor p
              join p.sindicato s
              join s.central c
              join c.federacion f
            where (:vigentes = false or v.vigente = true)
              and (:sindicatoId is null or s.id = :sindicatoId)
              and (:texto is null
                   or upper(p.nombres) like upper(concat('%', :texto, '%'))
                   or upper(p.apellidos) like upper(concat('%', :texto, '%'))
                   or p.ci like concat('%', :texto, '%')
                   or upper(p.codigo) = upper(:texto)
                   or upper(concat(f.numero, '-', c.abreviatura, '-', p.correlativo))
                        = upper(:texto))
            order by v.desde desc
            """)
    List<Veto> buscar(@Param("texto") String texto,
                      @Param("sindicatoId") Long sindicatoId,
                      @Param("vigentes") boolean vigentes);
}
