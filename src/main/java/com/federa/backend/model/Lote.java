package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.model.enums.Mercado;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una parcela del padrón. Mapea las columnas N° LOTE, EXT, ESTADO DEL LOTE y
 * MERCADO.
 * <p>
 * <b>El lote pertenece al sindicato, no al productor.</b> Es la diferencia que
 * ordena todo lo demás: la tierra no se mueve. Un productor puede vender su
 * lote e irse a otro sindicato, y el lote se queda donde siempre estuvo. Colgar
 * el lote del productor hacía que la parcela lo siguiera, que es imposible.
 * <p>
 * Quién lo tiene se guarda como una sucesión de {@link TenenciaLote}: el
 * tenedor de hoy es el período abierto, y los cerrados son el historial.
 * <p>
 * Decisiones tomadas a partir del dato real:
 * <ul>
 *   <li><b>numero es texto, no entero.</b> De 3.871 valores, 46 no son números:
 *       rangos ("30-31", "38-39"), lote con extensión pegada ("21-A", "20 B"),
 *       códigos ("B.N47", "6_007") e incluso una celda que Excel interpretó
 *       como fecha.</li>
 *   <li><b>No lleva restricción de unicidad.</b> El par
 *       (sindicato, número, extensión) se repite 425 veces en el padrón; es
 *       precisamente lo que la revisión anotó como "numero lote (repetido)".
 *       Bloquearlo impediría cargar el padrón tal como está.</li>
 *   <li><b>estadoOriginal</b> conserva el texto tal cual venía, porque
 *       {@link EstadoLote} agrupa 14 escrituras distintas en 6 estados.</li>
 * </ul>
 */
@Entity
@Table(
        name = "lotes",
        indexes = {
                @Index(name = "idx_lote_sindicato", columnList = "sindicato_id"),
                @Index(name = "idx_lote_numero", columnList = "numero")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lote extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Columna "N° LOTE". Ausente en 180 filas. */
    @Column(length = 20)
    private String numero;

    /** Columna "EXT": subdivisión del lote (A–E). */
    @Enumerated(EnumType.STRING)
    @Column(length = 1)
    private ExtensionLote extension;

    /**
     * Columna "ESTADO DEL LOTE" de la planilla, normalizada.
     * <p>
     * Se llama {@code estadoLote} y no {@code estado} porque ese nombre lo
     * ocupa ahora el booleano de {@link EntidadAuditable}, que dice si el
     * registro está habilitado. Son dos cosas distintas: esto describe la
     * parcela, aquello si la fila sigue vigente en el sistema.
     * <p>
     * En la API sigue viajando como {@code estado}: el cambio es interno y no
     * rompe a quien ya consume el endpoint.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_lote", length = 20)
    private EstadoLote estadoLote;

    /** Texto original de "ESTADO DEL LOTE", antes de normalizar. */
    @Column(name = "estado_original", length = 30)
    private String estadoOriginal;

    /** Columna "MERCADO". Solo 55 filas la tienen, todas DETALLISTA. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Mercado mercado;

    /**
     * Superficie en hectáreas, que es como se mide la tierra acá.
     * <p>
     * Cuatro decimales llegan al metro cuadrado. Null mientras no se haya
     * medido: en el padrón original la superficie no está, y forzar un cero
     * haría que una parcela sin medir se confunda con una de tamaño nulo.
     */
    @Column(precision = 10, scale = 4)
    private BigDecimal superficie;

    /**
     * Dónde está la parcela, en grados decimales. Null mientras no se marque.
     * <p>
     * DECIMAL y no double, igual que en el sindicato: un double redondea, y
     * sobre el terreno ese redondeo son metros. Con 7 decimales la precisión
     * ronda el centímetro.
     * <p>
     * Es un punto y no un polígono. Alcanza para encontrar la parcela en el
     * mapa, que es lo que hace falta hoy; dibujar el perímetro sería otra cosa
     * y no cambiaría este campo, lo agregaría al lado.
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitud;

    /** Cuándo se marcó por última vez. Null si nunca se marcó. */
    @Column(name = "ubicacion_actualizada_en")
    private LocalDateTime ubicacionActualizadaEn;

    /** Dónde está la tierra. No cambia. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sindicato_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lote_sindicato"))
    private Sindicato sindicato;

    /**
     * Quién lo tuvo y quién lo tiene, del más reciente al más antiguo.
     * <p>
     * En cascada porque el historial de tenencia no le sirve a nadie sin el
     * lote: si la parcela se borra, sus períodos se van con ella.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TenenciaLote> tenencias = new ArrayList<>();

    /** El sistema que tiene hoy, si tiene. */
    @JsonIgnore
    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TenenciaSistema> sistemas = new ArrayList<>();

    @Transient
    public boolean tieneUbicacion() {
        return latitud != null && longitud != null;
    }

    /** Marca o mueve el punto de la parcela. */
    public void marcarUbicacion(BigDecimal lat, BigDecimal lon) {
        this.latitud = lat;
        this.longitud = lon;
        this.ubicacionActualizadaEn = LocalDateTime.now();
    }

    public void borrarUbicacion() {
        this.latitud = null;
        this.longitud = null;
        this.ubicacionActualizadaEn = null;
    }

    /** Quién lo tiene hoy, o null si quedó sin tenedor. */
    @Transient
    public TenenciaLote getTenenciaVigente() {
        return tenencias.stream()
                .filter(TenenciaLote::estaVigente)
                .findFirst()
                .orElse(null);
    }

    /** Identificación legible del lote: "66-A" o "66" si no tiene extensión. */
    @Transient
    public String getCodigo() {
        if (numero == null) {
            return null;
        }
        return extension != null ? numero + "-" + extension.name() : numero;
    }
}
