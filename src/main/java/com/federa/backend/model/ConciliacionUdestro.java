package com.federa.backend.model;

import com.federa.backend.model.enums.EstadoConciliacionUdestro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Borrador auditable de una comparación entre el padrón y UDESTRO. */
@Entity
@Table(name = "conciliaciones_udestro", indexes = {
        @Index(name = "idx_conciliacion_udestro_federacion", columnList = "federacion_id"),
        @Index(name = "idx_conciliacion_udestro_estado", columnList = "fase")
})
@Getter
@Setter
@NoArgsConstructor
public class ConciliacionUdestro extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "federacion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_conciliacion_udestro_federacion"))
    private Federacion federacion;

    @Column(name = "nombre_archivo", nullable = false, length = 180)
    private String nombreArchivo;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "filas_excel", nullable = false)
    private int filasExcel;

    @Enumerated(EnumType.STRING)
    @Column(name = "fase", nullable = false, length = 20)
    private EstadoConciliacionUdestro fase = EstadoConciliacionUdestro.BORRADOR;

    @Column(name = "aplicada_en", columnDefinition = "datetime")
    private LocalDateTime aplicadaEn;

    @Version
    private long version;
}
