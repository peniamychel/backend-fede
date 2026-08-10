package com.federa.backend.model;

import com.federa.backend.model.enums.MotivoTraspaso;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Un período en el que un productor tuvo un lote.
 * <p>
 * Existe porque "de quién es el lote" no es un dato sino una historia: los
 * productores venden, heredan y se van del sindicato, y el padrón tiene que
 * poder decir quién lo tenía en tal fecha. Guardarlo como un campo del lote
 * respondería el presente y perdería todo lo anterior.
 */
@Entity
@Table(
        name = "tenencias_lote",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tenencia_lote_vigente",
                columnNames = {"lote_id", "vigente"}
        ),
        indexes = @Index(name = "idx_tenencia_lote_productor", columnList = "productor_id")
)
@Getter
@Setter
@NoArgsConstructor
public class TenenciaLote extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tenencia_lote_lote"))
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tenencia_lote_productor"))
    private Productor productor;

    @Column(nullable = false)
    private LocalDate desde;

    /** Hasta cuándo lo tuvo. Null mientras siga siendo suyo. */
    private LocalDate hasta;

    /**
     * Marca de vigencia: {@code TRUE} mientras lo tiene, {@code null} cuando lo
     * dejó.
     * <p>
     * Parece redundante con {@code hasta == null} y es a propósito: forma parte
     * de la clave única junto con el lote, y en MariaDB una clave única admite
     * repetir los nulos. El resultado es que la base acepta todos los períodos
     * cerrados que hagan falta pero rechaza dos tenedores vigentes del mismo
     * lote, sin depender de que el código se acuerde de comprobarlo.
     */
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
