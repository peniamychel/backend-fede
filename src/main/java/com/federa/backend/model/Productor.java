package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Productor afiliado: fila del padrón (4.051 registros).
 * <p>
 * Mapea las columnas Nombres, Apellidos, C.I, Carnet Productor, FOTO,
 * Nombre x, Apellido x y Columna1.
 * <p>
 * Sobre la calidad del dato de origen (por eso casi todo es nullable y sin
 * restricción de unicidad):
 * <ul>
 *   <li><b>C.I</b>: 3.927 de 4.051 filas la tienen, y hay 27 repetidas. Además
 *       26 valores no son numéricos ("8005906-1V", "2688288H5-P1"), por eso se
 *       guarda como texto y no como número.</li>
 *   <li><b>Carnet Productor</b>: solo 2.214 filas lo tienen, con 208 repetidos,
 *       y 10 valores son texto ("NUEVO").</li>
 *   <li><b>Apellidos</b>: faltan en 5 filas.</li>
 * </ul>
 * Ninguna de esas anomalías se bloquea con constraints: se registran como
 * {@link Observacion} para que la federación las depure desde el sistema.
 */
@Entity
@Table(
        name = "productores",
        indexes = {
                @Index(name = "idx_productor_sindicato", columnList = "sindicato_id"),
                @Index(name = "idx_productor_ci", columnList = "ci"),
                @Index(name = "idx_productor_carnet", columnList = "carnet_productor"),
                @Index(name = "idx_productor_apellidos", columnList = "apellidos")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Productor extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Columna "Nombres". */
    @Column(nullable = false, length = 60)
    private String nombres;

    /** Columna "Apellidos". Ausente en 5 filas del padrón. */
    @Column(length = 60)
    private String apellidos;

    /**
     * Código propio del productor, el que va en el QR de su credencial.
     * <p>
     * Es aleatorio y no derivado del id: un QR que dijera "812" se podría
     * fabricar a mano probando números, y acá el código es lo único que hace
     * falta para que a alguien le tomen asistencia.
     * <p>
     * Diez caracteres hexadecimales en mayúscula. Alcanzan de sobra para 4.051
     * personas —la probabilidad de que dos coincidan es de una en cien mil— y
     * son pocos como para escribirlos a mano cuando la cámara no coopera, que
     * es el respaldo que siempre hay que tener en el campo.
     */
    @Column(length = 16, unique = true)
    private String codigo;

    /** Columna "C.I". Texto: admite formatos con complemento ("8005906-1V"). */
    @Column(length = 20)
    private String ci;

    /** Columna "Carnet Productor". Texto: admite el valor "NUEVO". */
    @Column(name = "carnet_productor", length = 20)
    private String carnetProductor;

    /**
     * Columna "Nombre x": nombre corregido propuesto durante la revisión del
     * padrón, cuando el de la columna "Nombres" no coincide con el documento.
     * Presente en 292 filas.
     */
    @Column(name = "nombres_corregidos", length = 60)
    private String nombresCorregidos;

    /** Columna "Apellido x": apellido corregido. Presente en las mismas 292 filas. */
    @Column(name = "apellidos_corregidos", length = 60)
    private String apellidosCorregidos;

    /**
     * Columna "FOTO": rótulo con el que se archivó la fotografía del productor
     * ("Juliana Flores Pérez", "Constantina Hinojosa, 1ro de Mayo").
     * Presente en 938 filas; su ausencia es justamente lo que se observa como
     * {@code FALTA_FOTO}.
     */
    @Column(name = "foto_descripcion", length = 120)
    private String fotoDescripcion;

    /**
     * Columna "Columna1": marca manual ("a") puesta sobre 137 filas durante la
     * revisión. No hay un criterio único detrás, se conserva como bandera de
     * seguimiento.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean marcado = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sindicato_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_productor_sindicato"))
    private Sindicato sindicato;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "productor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Lote> lotes = new ArrayList<>();

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "productor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Observacion> observaciones = new ArrayList<>();

    /**
     * Miniatura y original, como máximo una de cada tipo. Va en cascada como
     * los lotes: borrar el productor tiene que llevarse sus imágenes, o quedan
     * filas huérfanas ocupando espacio para siempre.
     */
    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "productor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImagenProductor> imagenes = new ArrayList<>();

    /**
     * Cargos del directorio que ocupó, presentes y pasados.
     * <p>
     * En cascada como el resto de sus datos. Sin esto, borrar a alguien que
     * alguna vez fue presidente falla contra la clave foránea y el usuario
     * recibe un "viola una restricción de la base" que no le dice nada.
     */
    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "productor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Cargo> cargos = new ArrayList<>();

    // Las fechas de alta y modificación ya no viven acá: las hereda de
    // EntidadAuditable, que las pone para todas las tablas por igual. Antes
    // eran dos columnas propias, creado_en y actualizado_en, mantenidas por un
    // @PrePersist a mano; sus valores se migraron a created_at y updated_at.

    /** Nombre corregido si existe; si no, el original. */
    @Transient
    public String getNombreCompleto() {
        String n = nombresCorregidos != null ? nombresCorregidos : nombres;
        String a = apellidosCorregidos != null ? apellidosCorregidos : apellidos;
        return a != null ? n + " " + a : n;
    }

    @Transient
    /**
     * Le da un código si todavía no tiene.
     * <p>
     * Se llama al persistir y no en el constructor para que las filas que ya
     * existían —las que trajo la migración— conserven el suyo. Diez
     * hexadecimales de un UUID aleatorio; en Java el UUID de versión 4 sí sale
     * de un generador criptográfico, a diferencia del de MariaDB.
     */
    @PrePersist
    void asignarCodigo() {
        if (codigo == null || codigo.isBlank()) {
            codigo = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 10).toUpperCase();
        }
    }

    @Transient
    public boolean isTieneFoto() {
        return fotoDescripcion != null && !fotoDescripcion.isBlank();
    }

    public void agregarLote(Lote lote) {
        lotes.add(lote);
        lote.setProductor(this);
    }

    public void agregarObservacion(Observacion observacion) {
        observaciones.add(observacion);
        observacion.setProductor(this);
    }
}
