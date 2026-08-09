package com.federa.backend.model;

import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.model.enums.Mercado;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parcela asignada a un {@link Productor}. Mapea las columnas N° LOTE, EXT,
 * ESTADO DEL LOTE y MERCADO.
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
 *       Bloquearlo impediría cargar el padrón tal como está: la duplicación se
 *       anota como {@link Observacion} sobre el productor.</li>
 *   <li><b>estadoOriginal</b> conserva el texto tal cual venía, porque
 *       {@link EstadoLote} agrupa 14 escrituras distintas en 6 estados.</li>
 * </ul>
 */
@Entity
@Table(
        name = "lotes",
        indexes = {
                @Index(name = "idx_lote_productor", columnList = "productor_id"),
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lote_productor"))
    private Productor productor;

    /** Identificación legible del lote: "66-A" o "66" si no tiene extensión. */
    @Transient
    public String getCodigo() {
        if (numero == null) {
            return null;
        }
        return extension != null ? numero + "-" + extension.name() : numero;
    }
}
