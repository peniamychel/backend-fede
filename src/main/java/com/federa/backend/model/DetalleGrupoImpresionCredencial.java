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

/** Estado anterior y confirmación de una tarjeta dentro de una tanda masiva. */
@Entity
@Table(name = "detalles_grupo_impresion_credencial",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_detalle_grupo_productor",
                columnNames = {"grupo_id", "productor_id"}),
        indexes = {
                @Index(name = "idx_detalle_impresion_grupo", columnList = "grupo_id"),
                @Index(name = "idx_detalle_impresion_productor", columnList = "productor_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class DetalleGrupoImpresionCredencial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "grupo_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_detalle_impresion_grupo"))
    private GrupoImpresionCredencial grupo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "productor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_detalle_impresion_productor"))
    private Productor productor;

    @Column(name = "conteo_anterior", nullable = false)
    private int conteoAnterior;

    @Column(name = "ultima_impresion_anterior", columnDefinition = "datetime")
    private LocalDateTime ultimaImpresionAnterior;

    @Column(nullable = false)
    private boolean contabilizado;

    public DetalleGrupoImpresionCredencial(GrupoImpresionCredencial grupo,
                                            Productor productor,
                                            int conteoAnterior,
                                            LocalDateTime ultimaImpresionAnterior) {
        this.grupo = grupo;
        this.productor = productor;
        this.conteoAnterior = conteoAnterior;
        this.ultimaImpresionAnterior = ultimaImpresionAnterior;
        this.contabilizado = true;
    }
}
