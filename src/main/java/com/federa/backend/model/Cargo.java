package com.federa.backend.model;

import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.TipoCargo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un productor ocupando un cargo del directorio de su sindicato, con las
 * fechas entre las que lo ocupó.
 * <p>
 * No se guarda "quién es el presidente" como un campo del sindicato: eso
 * respondería el presente y perdería el pasado. Guardando cada período como una
 * fila, cambiar de presidente es cerrar uno y abrir otro, y el historial sale
 * solo.
 */
@Entity
@Table(
        name = "cargos",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cargo_vigente",
                        columnNames = {"sindicato_id", "cargo", "vigente"}),
                @UniqueConstraint(name = "uk_cargo_central_vigente",
                        columnNames = {"central_id", "cargo", "vigente"}),
                @UniqueConstraint(name = "uk_cargo_federacion_vigente",
                        columnNames = {"federacion_id", "cargo", "vigente"}),
                // Nadie ocupa dos cargos a la vez, en ningún nivel. Es la
                // regla más fuerte del directorio y la garantiza el motor.
                @UniqueConstraint(name = "uk_cargo_productor_vigente",
                        columnNames = {"productor_id", "vigente"})
        },
        indexes = {
                @Index(name = "idx_cargo_sindicato", columnList = "sindicato_id"),
                @Index(name = "idx_cargo_central", columnList = "central_id"),
                @Index(name = "idx_cargo_federacion", columnList = "federacion_id"),
                @Index(name = "idx_cargo_productor", columnList = "productor_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cargo extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TipoCargo cargo;

    /**
     * Nivel al que pertenece este cargo.
     * <p>
     * Se guarda además de las tres claves foráneas, aunque se podría deducir de
     * cuál no es nula. Guardarlo hace directas las consultas y deja la
     * intención escrita en la fila.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Ambito ambito;

    /**
     * Los tres dueños posibles. Exactamente uno está cargado, según el ámbito;
     * lo verifica {@link #colgarDe}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sindicato_id",
            foreignKey = @ForeignKey(name = "fk_cargo_sindicato"))
    private Sindicato sindicato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "central_id",
            foreignKey = @ForeignKey(name = "fk_cargo_central"))
    private Central central;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "federacion_id",
            foreignKey = @ForeignKey(name = "fk_cargo_federacion"))
    private Federacion federacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cargo_productor"))
    private Productor productor;

    /** Desde cuándo ocupa el cargo. */
    @Column(nullable = false)
    private LocalDate desde;

    /** Hasta cuándo lo ocupó. Null mientras sigue en funciones. */
    private LocalDate hasta;

    /**
     * Marca de vigencia: {@code TRUE} mientras el cargo está activo,
     * {@code null} cuando terminó.
     * <p>
     * Parece redundante con {@code hasta == null}, y es a propósito. Forma
     * parte de la clave única junto con sindicato y cargo, y en MariaDB una
     * clave única <b>permite repetir los nulos</b>. El resultado es que la base
     * admite todos los períodos cerrados que hagan falta pero rechaza un
     * segundo presidente vigente en el mismo sindicato: la regla queda
     * garantizada por el motor y no depende de que el código se acuerde de
     * comprobarla, ni siquiera con dos peticiones simultáneas.
     */
    private Boolean vigente;

    /**
     * Texto automático de respaldo que se imprime debajo de la firma cuando
     * el período no tiene una imagen de pie de firma.
     */
    @Column(name = "pie_firma", length = 200)
    private String pieFirma;

    /**
     * Firma y pie de firma en imagen de este período.
     */
    @Builder.Default
    @OneToMany(mappedBy = "cargo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImagenCargo> imagenes = new ArrayList<>();

    public boolean estaVigente() {
        return hasta == null;
    }

    /**
     * Cuelga el cargo del sindicato, la central o la federación, según cuál
     * venga. Deja los otros dos en null y fija el ámbito.
     * <p>
     * Es el único camino para asignar el dueño: si cada llamador pusiera la
     * relación a mano, tarde o temprano alguien dejaría dos cargadas y la fila
     * quedaría contando dos historias distintas.
     */
    public void colgarDe(Sindicato unSindicato, Central unaCentral, Federacion unaFederacion) {
        int cuantos = (unSindicato != null ? 1 : 0) + (unaCentral != null ? 1 : 0)
                + (unaFederacion != null ? 1 : 0);
        if (cuantos != 1) {
            throw new IllegalArgumentException(
                    "Un cargo pertenece exactamente a un nivel; llegaron " + cuantos + ".");
        }
        this.sindicato = unSindicato;
        this.central = unaCentral;
        this.federacion = unaFederacion;
        this.ambito = unSindicato != null ? Ambito.SINDICATO
                : unaCentral != null ? Ambito.CENTRAL : Ambito.FEDERACION;
    }

    /** Id del dueño, sea del nivel que sea. */
    public Long getDuenoId() {
        return switch (ambito) {
            case SINDICATO -> sindicato.getId();
            case CENTRAL -> central.getId();
            case FEDERACION -> federacion.getId();
        };
    }

    /** Nombre del dueño, para los mensajes y las respuestas. */
    public String getDuenoNombre() {
        return switch (ambito) {
            case SINDICATO -> sindicato.getNombre();
            case CENTRAL -> central.getNombre();
            case FEDERACION -> federacion.getNombre();
        };
    }

    /**
     * Pie que acompaña a la firma. Se deriva siempre de los datos vigentes
     * para que no pueda quedar desactualizado ni dependa de una carga manual.
     */
    public String construirPieFirma() {
        return productor.getNombreCompleto() + "\n"
                + cargo.getEtiqueta().toUpperCase(Locale.ROOT) + "\n"
                + getDuenoNombre();
    }

    /** Cierra el período. Es lo que se hace al reemplazar a alguien. */
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
