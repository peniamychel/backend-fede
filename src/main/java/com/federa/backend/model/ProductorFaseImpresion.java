package com.federa.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Productor incluido en una fase, con el resultado propio de esa fase. */
@Entity
@Table(name = "productores_fase_impresion",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_productor_fase_impresion",
                columnNames = {"fase_id", "productor_id"}),
        indexes = {
                @Index(name = "idx_productor_fase_fase", columnList = "fase_id"),
                @Index(name = "idx_productor_fase_productor", columnList = "productor_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class ProductorFaseImpresion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fase_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_productor_fase_fase"))
    private FaseImpresionCarnet fase;

    @ManyToOne(optional = false)
    @JoinColumn(name = "productor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_productor_fase_productor"))
    private Productor productor;

    @Column(name = "agregado_en", nullable = false, columnDefinition = "datetime")
    private LocalDateTime agregadoEn;

    @Column(nullable = false)
    private boolean pendiente;

    @Column(nullable = false)
    private boolean reimpresion;

    @Column(name = "impresiones_en_fase", nullable = false,
            columnDefinition = "integer default 0")
    private int impresionesEnFase;

    public ProductorFaseImpresion(FaseImpresionCarnet fase, Productor productor,
                                   LocalDateTime agregadoEn, boolean pendiente,
                                   boolean reimpresion, int impresionesEnFase) {
        this.fase = fase;
        this.productor = productor;
        this.agregadoEn = agregadoEn;
        this.pendiente = pendiente;
        this.reimpresion = reimpresion;
        this.impresionesEnFase = impresionesEnFase;
    }
}
