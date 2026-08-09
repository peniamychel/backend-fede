package com.federa.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base de todas las entidades: cuándo se creó cada fila, cuándo se tocó por
 * última vez, y si sigue habilitada.
 * <p>
 * Las dos fechas las pone solo Spring Data, con el oyente de auditoría que
 * declara {@link EntityListeners}. No hay que acordarse de asignarlas al
 * guardar, y no se pueden falsear desde la API porque ningún request las
 * acepta: {@code createdAt} además es {@code updatable = false}, así que ni un
 * UPDATE puede moverla.
 * <p>
 * {@code updatedAt} solo cambia cuando la fila realmente cambió. Guardar una
 * entidad sin modificarle nada no la mueve, porque Hibernate ni siquiera emite
 * el UPDATE.
 * <p>
 * Es {@code @MappedSuperclass} y no una entidad: no hay tabla "auditable", cada
 * tabla lleva sus tres columnas propias.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class EntidadAuditable {

    /**
     * Alta de la fila. Se escribe una sola vez.
     * <p>
     * El tipo en la base es {@code datetime}, sin fracción de segundo: para
     * saber cuándo se cargó un productor sobra el segundo, y así la columna se
     * lee igual desde cualquier herramienta.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "datetime")
    private LocalDateTime createdAt;

    /** Última modificación. */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime")
    private LocalDateTime updatedAt;

    /**
     * Si el registro sigue vigente. Nace habilitado.
     * <p>
     * Deshabilitar no es borrar: la fila queda, con sus relaciones intactas y
     * su historial, y se puede volver a habilitar. Sirve para los casos en que
     * borrar no es una opción —un productor que se dio de baja pero figura en
     * actas anteriores— y para los que el borrado ni siquiera está permitido
     * porque tienen registros colgando.
     */
    @Column(nullable = false)
    private boolean estado = true;
}
