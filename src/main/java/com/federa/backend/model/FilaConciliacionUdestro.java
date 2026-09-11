package com.federa.backend.model;

import com.federa.backend.model.enums.AccionConciliacionUdestro;
import com.federa.backend.model.enums.DecisionConflictoUdestro;
import com.federa.backend.model.enums.EstadoLote;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Una acción propuesta; puede venir del Excel o representar una ausencia del padrón. */
@Entity
@Table(name = "filas_conciliacion_udestro", indexes = {
        @Index(name = "idx_fila_udestro_conciliacion", columnList = "conciliacion_id"),
        @Index(name = "idx_fila_udestro_accion", columnList = "conciliacion_id,accion")
})
@Getter
@Setter
@NoArgsConstructor
public class FilaConciliacionUdestro extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conciliacion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fila_udestro_conciliacion"))
    private ConciliacionUdestro conciliacion;

    @Column(name = "numero_fila")
    private Integer numeroFila;

    @Column(name = "central_nombre", length = 60)
    private String centralNombre;

    @Column(name = "sindicato_nombre", length = 60)
    private String sindicatoNombre;

    @Column(name = "nombres_udestro", length = 60)
    private String nombresUdestro;

    @Column(name = "apellidos_udestro", length = 60)
    private String apellidosUdestro;

    @Column(name = "ci", length = 20)
    private String ci;

    @Column(name = "central_destino_id")
    private Long centralDestinoId;

    @Column(name = "sindicato_destino_id")
    private Long sindicatoDestinoId;

    @Column(name = "sindicato_nuevo", nullable = false)
    private boolean sindicatoNuevo;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 32)
    private AccionConciliacionUdestro accion;

    @Column(name = "motivo", length = 600)
    private String motivo;

    /** ID separado de la FK a propósito: el borrador conserva el diagnóstico. */
    @Column(name = "productor_id")
    private Long productorId;

    @Column(name = "productor_actualizado_en", columnDefinition = "datetime")
    private LocalDateTime productorActualizadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "clasificacion_anterior", length = 30)
    private EstadoLote clasificacionAnterior;

    /** Lista CSV de IDs; solo contiene dígitos y comas generados por el servidor. */
    @Column(name = "candidatos_ids", length = 1000)
    private String candidatosIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_conflicto", length = 24)
    private DecisionConflictoUdestro decisionConflicto;

    @Column(name = "productor_seleccionado_id")
    private Long productorSeleccionadoId;

    /** Porcentaje de similitud entre el nombre actual y el informado por UDESTRO. */
    @Column(name = "similitud_nombre")
    private Integer similitudNombre;
}
