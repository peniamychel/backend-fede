package com.federa.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Un productor vetado por decisión de asamblea.
 * <p>
 * El veto no lo pone el sistema ni quien lo carga: lo decide una reunión, y el
 * acta de esa reunión es la prueba. Por eso {@link #reunion} es obligatoria y
 * el servicio exige que tenga su acta subida antes de dejar registrar nada.
 * <p>
 * Mientras está vigente, la persona queda observada: su credencial no se emite.
 * No se la borra ni se la deshabilita —sigue siendo afiliada, con su parcela y
 * su historial—, lo que pierde es el documento que la acredita.
 * <p>
 * Sacar a alguien de la lista también es una decisión de asamblea, y de
 * <b>otra</b> reunión: la que lo vetó no puede levantarlo. Por eso el
 * levantamiento guarda su propia reunión y su propio motivo, y el veto no se
 * borra: queda cerrado, con las dos decisiones a la vista.
 */
@Entity
@Table(
        name = "vetos",
        uniqueConstraints = {
                // Un solo veto abierto por productor. El truco es el mismo que
                // usan los cargos y las tenencias: `vigente` es TRUE o NULL,
                // nunca FALSE, y en MariaDB una clave única deja pasar todos los
                // NULL que haga falta. Así conviven los vetos ya levantados y a
                // lo sumo uno abierto.
                @UniqueConstraint(name = "uk_veto_productor_vigente",
                        columnNames = {"productor_id", "vigente"})
        },
        indexes = {
                @Index(name = "idx_veto_reunion", columnList = "reunion_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Veto extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_veto_productor"))
    private Productor productor;

    /** La reunión que lo decidió. Su acta es el respaldo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reunion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_veto_reunion"))
    private Reunion reunion;

    /**
     * Por qué se lo vetó, con el detalle que dé el acta.
     * <p>
     * Es obligatorio: un veto sin motivo es una sanción que nadie puede
     * revisar después, y quien la sufre tiene derecho a saber por qué.
     */
    @Column(nullable = false, length = 1000)
    private String motivo;

    @Column(nullable = false)
    private LocalDate desde;

    // ------------------------------------------------------ el levantamiento

    /** La reunión que lo sacó de la lista. Null mientras siga vetado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reunion_levanta_id",
            foreignKey = @ForeignKey(name = "fk_veto_reunion_levanta"))
    private Reunion reunionLevanta;

    @Column(name = "motivo_levantamiento", length = 1000)
    private String motivoLevantamiento;

    private LocalDate hasta;

    /**
     * TRUE mientras el veto esté abierto; <b>null</b> cuando se levanta.
     * <p>
     * Nunca FALSE: de eso depende que la clave única deje convivir varios vetos
     * cerrados del mismo productor con uno solo abierto.
     */
    @Column
    private Boolean vigente;

    public boolean estaVigente() {
        return Boolean.TRUE.equals(vigente);
    }

    /** Abre el veto. */
    public void iniciar(LocalDate desde) {
        this.desde = desde;
        this.vigente = Boolean.TRUE;
        this.hasta = null;
    }

    /** Lo cierra, dejando constancia de quién lo decidió y por qué. */
    public void levantar(Reunion reunion, String motivo, LocalDate hasta) {
        this.reunionLevanta = reunion;
        this.motivoLevantamiento = motivo;
        this.hasta = hasta;
        // A null y no a false: ver la nota de arriba.
        this.vigente = null;
    }
}
