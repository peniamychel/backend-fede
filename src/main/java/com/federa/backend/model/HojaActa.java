package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una hoja del acta de una reunión.
 * <p>
 * El acta casi nunca es un solo archivo. Lo habitual es el cuaderno de actas
 * fotografiado hoja por hoja con el teléfono, ahí mismo en la asamblea. Antes
 * esto era un archivo único y obligaba a juntar todo en un PDF antes de subir,
 * que es un paso que nadie hace en el campo.
 * <p>
 * El {@link #orden} conserva la secuencia: sin él, seis fotos de un cuaderno
 * son seis fotos sueltas y hay que adivinar cuál va primero.
 */
@Entity
@Table(
        name = "hojas_acta",
        indexes = @Index(name = "idx_hoja_reunion", columnList = "reunion_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HojaActa extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reunion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hoja_reunion"))
    private Reunion reunion;

    /** Posición dentro del acta, empezando en 1. */
    @Column(nullable = false)
    private int orden;

    /** Clave en el almacén. */
    @Column(nullable = false, length = 200)
    private String clave;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(name = "tipo_mime", nullable = false, length = 60)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private int tamanoBytes;
}
