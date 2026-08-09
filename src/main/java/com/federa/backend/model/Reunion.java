package com.federa.backend.model;

import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.TipoReunion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Una reunión convocada por un sindicato, una central o la federación.
 * <p>
 * Cuelga del nivel que la convoca, igual que un cargo del directorio: tres
 * claves foráneas de las que exactamente una está cargada. Cuál corresponde lo
 * dice el propio {@link TipoReunion}, así que no puede haber una reunión de
 * ampliado colgada de un sindicato.
 */
@Entity
@Table(
        name = "reuniones",
        indexes = {
                @Index(name = "idx_reunion_fecha", columnList = "fecha"),
                @Index(name = "idx_reunion_sindicato", columnList = "sindicato_id"),
                @Index(name = "idx_reunion_central", columnList = "central_id"),
                @Index(name = "idx_reunion_federacion", columnList = "federacion_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Reunion extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TipoReunion tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 120)
    private String lugar;

    @Column(length = 500)
    private String observaciones;

    /**
     * Una reunión cerrada ya no admite asistencias.
     * <p>
     * Existe porque pasar lista es un acto con un momento: si la lista siguiera
     * abierta para siempre, alguien podría agregarse una semana después y el
     * acta diría que estuvo.
     */
    @Column(nullable = false)
    private boolean cerrada = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sindicato_id",
            foreignKey = @ForeignKey(name = "fk_reunion_sindicato"))
    private Sindicato sindicato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "central_id",
            foreignKey = @ForeignKey(name = "fk_reunion_central"))
    private Central central;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "federacion_id",
            foreignKey = @ForeignKey(name = "fk_reunion_federacion"))
    private Federacion federacion;

    @JsonIgnore
    @OneToMany(mappedBy = "reunion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Asistencia> asistencias = new ArrayList<>();

    /** Nivel que la convoca. Se deriva del tipo, no se guarda aparte. */
    @Transient
    public Ambito getConvoca() {
        return tipo.getConvoca();
    }

    /**
     * Cuelga la reunión del nivel que la convoca, dejando los otros dos en
     * null. Es el único camino, por lo mismo que en los cargos: si cada
     * llamador armara la relación a mano, tarde o temprano una fila quedaría
     * contando dos historias.
     */
    public void colgarDe(Sindicato unSindicato, Central unaCentral, Federacion unaFederacion) {
        int cuantos = (unSindicato != null ? 1 : 0) + (unaCentral != null ? 1 : 0)
                + (unaFederacion != null ? 1 : 0);
        if (cuantos != 1) {
            throw new IllegalArgumentException(
                    "Una reunión pertenece exactamente a un nivel; llegaron " + cuantos + ".");
        }
        this.sindicato = unSindicato;
        this.central = unaCentral;
        this.federacion = unaFederacion;
    }

    public Long getConvocanteId() {
        return switch (getConvoca()) {
            case SINDICATO -> sindicato.getId();
            case CENTRAL -> central.getId();
            case FEDERACION -> federacion.getId();
        };
    }

    public String getConvocanteNombre() {
        return switch (getConvoca()) {
            case SINDICATO -> sindicato.getNombre();
            case CENTRAL -> central.getNombre();
            case FEDERACION -> federacion.getNombre();
        };
    }
}
