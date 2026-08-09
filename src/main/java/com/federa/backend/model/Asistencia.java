package com.federa.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Que alguien estuvo en una reunión.
 * <p>
 * Se guarda el momento exacto además de la fecha de la reunión: en una lista
 * larga sirve para saber quién llegó tarde, y para reconstruir el orden en que
 * se pasó lista si después hay una discusión sobre el quórum.
 */
@Entity
@Table(
        name = "asistencias",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asistencia",
                columnNames = {"reunion_id", "productor_id"}
        ),
        indexes = @Index(name = "idx_asistencia_productor", columnList = "productor_id")
)
@Getter
@Setter
@NoArgsConstructor
public class Asistencia extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reunion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_asistencia_reunion"))
    private Reunion reunion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_asistencia_productor"))
    private Productor productor;

    /** Cuándo se escaneó el carnet. */
    @Column(name = "registrada_en", nullable = false)
    private LocalDateTime registradaEn;

    public Asistencia(Reunion reunion, Productor productor) {
        this.reunion = reunion;
        this.productor = productor;
        this.registradaEn = LocalDateTime.now();
    }
}
