package com.federa.backend.repository;

import com.federa.backend.model.Sindicato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SindicatoRepository extends JpaRepository<Sindicato, Long> {

    List<Sindicato> findByCentralIdOrderByNombreAsc(Long centralId);

    /**
     * El nombre solo es único dentro de la central: hay sindicatos homónimos en
     * centrales distintas ("1RO DE MAYO" existe en tres).
     */
    Optional<Sindicato> findByCentralIdAndNombreIgnoreCase(Long centralId, String nombre);

    /** Para avisar del número repetido antes de que lo rechace la clave única. */
    Optional<Sindicato> findByNumero(String numero);

    long countByCentralId(Long centralId);

    /**
     * Sindicatos con la sede ya marcada, opcionalmente acotados a una central.
     * <p>
     * Se exigen las dos coordenadas: una sola no ubica nada y no debería
     * dibujarse en el mapa.
     */
    @Query("""
            select s from Sindicato s
            where s.latitud is not null and s.longitud is not null
              and (:centralId is null or s.central.id = :centralId)
            order by s.nombre
            """)
    List<Sindicato> findConUbicacion(@Param("centralId") Long centralId);
}
