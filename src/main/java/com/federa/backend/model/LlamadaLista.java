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

/**
 * Una vuelta de lista dentro de una reunión.
 * <p>
 * En una asamblea se llama lista más de una vez: la primera al empezar, otra
 * más tarde para los que llegaron tarde, y a veces una al final para ver quién
 * se quedó hasta el cierre. Cada vuelta tiene su propia lista de presentes, y
 * mezclarlas perdería justamente lo que se quiere saber: quién estuvo en qué
 * momento.
 * <p>
 * Antes la asistencia colgaba directamente de la reunión, lo que solo permitía
 * una vuelta. Ahora cuelga de acá.
 */
@Entity
@Table(
        name = "llamadas_lista",
        uniqueConstraints = {
                // El número identifica la vuelta dentro de su reunión: "segunda
                // llamada" no significa nada sin decir de qué reunión.
                @UniqueConstraint(name = "uk_llamada_reunion_numero",
                        columnNames = {"reunion_id", "numero"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlamadaLista extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reunion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_llamada_reunion"))
    private Reunion reunion;

    /** Primera, segunda, tercera. Empieza en 1 dentro de cada reunión. */
    @Column(nullable = false)
    private int numero;

    /**
     * Una llamada abierta admite registros; una cerrada no.
     * <p>
     * Se cierra cuando se termina esa vuelta, y a partir de ahí lo que se
     * registre va a la siguiente. Sin esto, alguien que llega a la tercera
     * quedaría anotado en la primera y el acta diría que estuvo desde el
     * principio.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean abierta = true;

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    /** Nota opcional: "segunda llamada, después del cuarto intermedio". */
    @Column(length = 200)
    private String nota;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "llamada", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Asistencia> asistencias = new ArrayList<>();

    public void cerrar(LocalDateTime momento) {
        this.abierta = false;
        this.cerradaEn = momento;
    }
}
