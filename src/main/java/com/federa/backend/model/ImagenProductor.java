package com.federa.backend.model;

import com.federa.backend.model.enums.TipoImagen;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Referencia a la imagen de un productor. <b>El archivo no está acá</b>.
 * <p>
 * Los binarios viven en el almacén de objetos del servidor y la base guarda
 * solo la clave con la que recuperarlos, más la metadata que hace falta para
 * mostrarlos sin abrir el archivo.
 * <p>
 * La clave lleva un identificador aleatorio ({@code ORIGINAL-a1b2c3.jpg}) y no
 * un nombre fijo. Eso hace que al reemplazar una foto cambie la URL, y con ella
 * se invalide sola cualquier caché del navegador: sin eso habría que inventar
 * parámetros de versión para que no se siguiera viendo la imagen vieja.
 */
@Entity
@Table(
        name = "imagenes_productor",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_imagen_productor_tipo",
                columnNames = {"productor_id", "tipo"}
        ),
        indexes = @Index(name = "idx_imagen_productor", columnList = "productor_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImagenProductor extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoImagen tipo;

    /**
     * Clave en el almacén de objetos, del estilo
     * {@code productores/15/ORIGINAL-a1b2c3.jpg}. Es una ruta lógica, no del
     * sistema de archivos: dónde aterriza lo decide el almacén.
     */
    @Column(nullable = false, length = 200)
    private String clave;

    /** Tipo MIME real, verificado al procesar la imagen. */
    @Column(name = "tipo_mime", nullable = false, length = 40)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private int tamanoBytes;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    /** Nombre con el que se subió, solo para mostrarlo. */
    @Column(name = "nombre_original", length = 160)
    private String nombreOriginal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_imagen_productor"))
    private Productor productor;

    @Column(name = "actualizada_en", nullable = false)
    private LocalDateTime actualizadaEn;

    @PrePersist
    @PreUpdate
    void alGuardar() {
        actualizadaEn = LocalDateTime.now();
    }
}
