package com.federa.backend.model;

import com.federa.backend.model.enums.MotivoTraspaso;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Un período en el que un sistema estuvo en un lote.
 * <p>
 * Las dos claves únicas dicen las dos reglas del dominio, y las garantiza el
 * motor: un sistema está en un solo lote a la vez, y un lote tiene a lo sumo un
 * sistema. Las dos funcionan por lo mismo —en MariaDB una clave única admite
 * repetir los nulos— así que los períodos cerrados no compiten entre sí.
 */
@Entity
@Table(
        name = "tenencias_sistema",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenencia_sistema_vigente",
                        columnNames = {"sistema_id", "vigente"}),
                @UniqueConstraint(name = "uk_tenencia_lote_sistema_vigente",
                        columnNames = {"lote_id", "vigente"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TenenciaSistema extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sistema_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tenencia_sistema_sistema"))
    private Sistema sistema;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tenencia_sistema_lote"))
    private Lote lote;

    @Column(nullable = false)
    private LocalDate desde;

    private LocalDate hasta;

    private Boolean vigente;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private MotivoTraspaso motivo;

    @Column(length = 300)
    private String observaciones;

    public boolean estaVigente() {
        return hasta == null;
    }

    public void terminar(LocalDate fecha) {
        this.hasta = fecha;
        this.vigente = null;
    }

    public void iniciar(LocalDate fecha) {
        this.desde = fecha;
        this.hasta = null;
        this.vigente = Boolean.TRUE;
    }
}
