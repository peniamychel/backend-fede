package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Productor afiliado: fila del padrón (4.051 registros).
 * <p>
 * Mapea las columnas Nombres, Apellidos, C.I, FOTO,
 * Nombre x, Apellido x y Columna1.
 * <p>
 * Sobre la calidad del dato de origen (por eso casi todo es nullable y sin
 * restricción de unicidad):
 * <ul>
 *   <li><b>C.I</b>: 3.927 de 4.051 filas la tienen, y hay 27 repetidas. Además
 *       26 valores no son numéricos ("8005906-1V", "2688288H5-P1"), por eso se
 *       guarda como texto y no como número.</li>
 *   <li><b>Apellidos</b>: faltan en 5 filas.</li>
 * </ul>
 * Ninguna de esas anomalías se bloquea con constraints: el padrón real entra
 * como está y se depura después.
 */
@Entity
@Table(
        name = "productores",
        indexes = {
                @Index(name = "idx_productor_sindicato", columnList = "sindicato_id"),
                @Index(name = "idx_productor_ci", columnList = "ci"),
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

    /**
     * Número del productor dentro de su central, empezando en 1.
     * <p>
     * Es la última parte del código del padrón —el "1" de {@code 2-IVI-1}— y lo
     * único que hace falta guardar: el número de la federación y la sigla de la
     * central se leen de ellas al armar el código. Guardar la cadena entera
     * dejaría códigos viejos el día que una central cambie de sigla, y hoy
     * ninguna la tiene puesta todavía.
     * <p>
     * No es el {@link #codigo} de la credencial. Ese es aleatorio y sirve para
     * escanear; este se lee, se dicta y dice dónde está la persona.
     * <p>
     * Va aparejado a la central, así que si el productor se muda a otra se le da
     * uno nuevo allá: el viejo pertenecía a la numeración de la central que
     * dejó. Puede ser null en filas que todavía no pasaron por la migración.
     */
    @Column
    private Integer correlativo;

    /**
     * Letra que distingue a quienes comparten un mismo número de lote.
     * <p>
     * Es null mientras el lote tenga un solo productor. Cuando el mismo número
     * está repartido entre varias filas vigentes, sus lotes se muestran como
     * "22 A", "22 B"... según la prioridad y el orden de asignación. La letra
     * no modifica el correlativo propio de cada productor.
     */
    @Column(name = "letra_codigo", length = 1)
    private String letraCodigo;

    /** Columna "C.I". Texto: admite formatos con complemento ("8005906-1V"). */
    @Column(length = 20)
    private String ci;

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

    /**
     * Los registros creados por importación masiva se revisan contra SIE al
     * abrir su ficha por primera vez. Las altas manuales ya consultan SIE en
     * el formulario, por eso nacen con este indicador apagado.
     */
    @Column(name = "revision_sie_pendiente", nullable = false)
    @Builder.Default
    private boolean revisionSiePendiente = false;

    /**
     * Cantidad de trabajos de impresión de anverso confirmados para este
     * productor. El reverso no modifica este valor porque es común a todo el
     * sindicato y no identifica una tarjeta concreta.
     */
    @Column(name = "credencial_impresiones", nullable = false,
            columnDefinition = "integer default 0")
    @Builder.Default
    private int credencialImpresiones = 0;

    /** Última vez que se confirmó el envío de su anverso a la impresora. */
    @Column(name = "credencial_ultima_impresion", columnDefinition = "datetime")
    private LocalDateTime credencialUltimaImpresion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sindicato_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_productor_sindicato"))
    private Sindicato sindicato;

    /**
     * Los períodos en que tuvo lotes, presentes y pasados.
     * <p>
     * Antes acá había una lista de lotes en cascada, y eso era un error de
     * fondo: borrar a un productor borraba su tierra. El lote pertenece al
     * sindicato y sobrevive a cualquier cambio de dueño; lo que se guarda del
     * lado del productor es cuándo lo tuvo.
     * <p>
     * En cascada solo las tenencias: si el productor desaparece del padrón, sus
     * períodos se van con él, pero los lotes se quedan donde están.
     */
    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "productor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TenenciaLote> tenencias = new ArrayList<>();

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

    /**
     * Los vetos que le puso la asamblea, vigentes y levantados.
     * <p>
     * En cascada como el resto de lo suyo. Si la persona se borra del padrón,
     * la sanción deja de tener a quién sancionar. Ojo: borrar es distinto de
     * dar de baja —lo habitual— donde la ficha y sus vetos se quedan.
     */
    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "productor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Veto> vetos = new ArrayList<>();

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

    /**
     * Le da un lote desde una fecha, abriendo su período de tenencia.
     * <p>
     * No comprueba que el lote esté libre: eso es trabajo del servicio, que
     * sabe cerrar el período anterior. Acá solo se arma la relación en los dos
     * sentidos.
     */
    public TenenciaLote tomarLote(Lote lote, LocalDate desde) {
        TenenciaLote tenencia = new TenenciaLote();
        tenencia.setLote(lote);
        tenencia.setProductor(this);
        tenencia.iniciar(desde);
        tenencias.add(tenencia);
        lote.getTenencias().add(tenencia);
        return tenencia;
    }

}
