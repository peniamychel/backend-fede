package com.federa.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Observación sobre un {@link Productor}: columna "OBJ" del padrón.
 * <p>
 * Es un mensaje libre escrito por quien revisa el padrón ("falta foto",
 * "el nombre no coincide con el CI", "duplicado en sindicato Media Luna").
 * En la planilla una misma celda puede juntar varios motivos separados por
 * coma; acá cada motivo se guarda como una fila propia para poder resolverlos
 * de a uno.
 */
@Entity
@Table(
        name = "observaciones",
        indexes = {
                @Index(name = "idx_observacion_productor", columnList = "productor_id"),
                @Index(name = "idx_observacion_resuelta", columnList = "resuelta")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Observacion extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Qué se observó, en texto libre. */
    @Column(nullable = false, length = 500)
    private String mensaje;

    /** Permite dar por depurada la observación desde el sistema. */
    @Column(nullable = false)
    @Builder.Default
    private boolean resuelta = false;

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_observacion_productor"))
    private Productor productor;

    public void resolver() {
        this.resuelta = true;
        this.resueltaEn = LocalDateTime.now();
    }

    public void reabrir() {
        this.resuelta = false;
        this.resueltaEn = null;
    }
}
