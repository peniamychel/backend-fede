package com.federa.backend.model;

import com.federa.backend.model.enums.TipoImagenCargo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Firma y pie de firma de quien ocupa un cargo del directorio.
 * <p>
 * Como el resto de las imágenes del sistema, el archivo vive en el almacén de
 * objetos y acá queda solo la clave para encontrarlo.
 */
@Entity
@Table(
        name = "imagenes_cargo",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_imagen_cargo_tipo",
                columnNames = {"cargo_id", "tipo"}
        ),
        indexes = @Index(name = "idx_imagen_cargo", columnList = "cargo_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImagenCargo extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoImagenCargo tipo;

    /** Clave en el almacén, del estilo {@code firmas/a1b2c3-juan-morales.jpg}. */
    @Column(nullable = false, length = 200)
    private String clave;

    @Column(name = "tipo_mime", nullable = false, length = 40)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private int tamanoBytes;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    @Column(name = "nombre_original", length = 160)
    private String nombreOriginal;

    /** Copia editable, comprimida como JPEG a un máximo de 300 KB. */
    @Column(name = "original_clave", length = 200)
    private String originalClave;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cargo_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_imagen_cargo"))
    private Cargo cargo;

    @Column(name = "actualizada_en", nullable = false)
    private LocalDateTime actualizadaEn;

    @PrePersist
    @PreUpdate
    void alGuardar() {
        actualizadaEn = LocalDateTime.now();
    }
}
