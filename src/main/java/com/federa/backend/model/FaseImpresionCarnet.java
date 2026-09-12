package com.federa.backend.model;

import com.federa.backend.model.enums.EstadoFaseImpresionCarnet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Período habilitado por una central para controlar la impresión de carnets. */
@Entity
@Table(name = "fases_impresion_carnet",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fase_impresion_central_numero",
                columnNames = {"central_id", "numero"}),
        indexes = @Index(name = "idx_fase_impresion_central_estado",
                columnList = "central_id,estado"))
@Getter
@Setter
@NoArgsConstructor
public class FaseImpresionCarnet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "central_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fase_impresion_central"))
    private Central central;

    @Column(nullable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoFaseImpresionCarnet estado;

    @Column(name = "abierta_en", nullable = false, columnDefinition = "datetime")
    private LocalDateTime abiertaEn;

    @Column(name = "cerrada_en", columnDefinition = "datetime")
    private LocalDateTime cerradaEn;

    public FaseImpresionCarnet(Central central, int numero, LocalDateTime abiertaEn) {
        this.central = central;
        this.numero = numero;
        this.abiertaEn = abiertaEn;
        this.estado = EstadoFaseImpresionCarnet.ABIERTA;
    }

    public boolean estaAbierta() {
        return estado == EstadoFaseImpresionCarnet.ABIERTA && cerradaEn == null;
    }
}
