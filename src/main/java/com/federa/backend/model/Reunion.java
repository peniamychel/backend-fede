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

    /**
     * Si en esta reunión se pueden decidir vetos.
     * <p>
     * No toda asamblea es para sancionar: la mayoría es informativa o de
     * organización, y ofrecer el veto en todas invita a usarlo donde no
     * corresponde. Se habilita a propósito, por reunión.
     */
    @Column(name = "vetos_habilitados", nullable = false)
    private boolean vetosHabilitados = false;

    /**
     * El número del acta, como está escrito en el libro.
     * <p>
     * No lo inventa el sistema: lo trae el papel. El libro de actas del
     * sindicato es el original, y el archivo que se sube acá es una foto de una
     * de sus hojas; sin el número, meses después nadie puede ir al libro a
     * cotejar lo que la pantalla dice que se decidió.
     * <p>
     * No es único: cada sindicato lleva su propio libro y numera desde uno, así
     * que «Acta N° 12» existe tantas veces como libros hay.
     * <p>
     * Null mientras no haya acta. Se pide al subir la primera hoja y se borra
     * al quitar la última: un número de acta sin acta no identifica nada.
     */
    @Column(name = "codigo_acta", length = 40)
    private String codigoActa;

    /**
     * Las hojas del acta, en orden.
     * <p>
     * En cascada: si la reunión se borra, sus hojas se van con ella. Los
     * archivos del almacén los limpia el servicio, que el disco no sabe de JPA.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "reunion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc")
    private List<HojaActa> hojasActa = new ArrayList<>();

    /** Las vueltas de lista, de la primera a la última. */
    @JsonIgnore
    @OneToMany(mappedBy = "reunion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero asc")
    private List<LlamadaLista> llamadas = new ArrayList<>();

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

    /** Nivel que la convoca. Se deriva del tipo, no se guarda aparte. */
    @Transient
    public Ambito getConvoca() {
        return tipo.getConvoca();
    }

    /**
     * Si el acta ya está cargada, aunque sea una sola hoja.
     * <p>
     * Es lo que habilita a que en esta reunión se decidan vetos: sin el
     * documento que lo respalde, un veto sería la palabra de quien lo cargó.
     */
    @Transient
    public boolean isTieneActa() {
        return hojasActa != null && !hojasActa.isEmpty();
    }

    /** La vuelta de lista abierta, si hay alguna. */
    @Transient
    public LlamadaLista getLlamadaAbierta() {
        if (llamadas == null) {
            return null;
        }
        for (LlamadaLista llamada : llamadas) {
            if (llamada.isAbierta()) {
                return llamada;
            }
        }
        return null;
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
