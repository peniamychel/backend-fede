package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Columna "CENTRAL" del padrón: agrupa sindicatos y pertenece a una
 * {@link Federacion}.
 * <p>
 * La planilla trae 16 centrales, desde 1RO DE MAYO (832 productores) hasta
 * VALLE IVIRZA (2).
 * <p>
 * El nombre es único <b>dentro de la federación</b>, no a nivel global, por el
 * mismo criterio que {@link Sindicato} respecto de su central: dos federaciones
 * distintas pueden tener una central con el mismo nombre.
 */
@Entity
@Table(
        name = "centrales",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_central_federacion_nombre",
                        columnNames = {"federacion_id", "nombre"}
                ),
                // Único a secas, no por federación: el número lo asigna la
                // federación y no se repite entre centrales. Que sea una clave
                // de la base y no solo una comprobación en Java es lo que
                // impide que dos altas simultáneas metan el mismo número.
                @UniqueConstraint(name = "uk_central_numero", columnNames = "numero")
        }
        // Sin índice aparte sobre federacion_id: la primera clave única ya
        // empieza por esa columna y sirve para buscar por federación.
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Central extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    /**
     * Número con el que la federación identifica a esta central.
     * <p>
     * Opcional: casi ninguna lo tiene cargado todavía. Va como texto y no como
     * entero por el mismo motivo que la C.I. del productor —el padrón real
     * trae valores como "8005906-1V"—, y porque un número guardado como texto
     * conserva los ceros a la izquierda si mañana los usan.
     * <p>
     * La unicidad la garantiza {@code uk_central_numero}. En MariaDB una clave
     * única admite varios NULL, que es justo lo que hace falta acá: muchas
     * centrales sin número, y a lo sumo una por cada número dado.
     */
    @Column(length = 20)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "federacion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_central_federacion"))
    private Federacion federacion;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "central", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Sindicato> sindicatos = new ArrayList<>();

    public void agregarSindicato(Sindicato sindicato) {
        sindicatos.add(sindicato);
        sindicato.setCentral(this);
    }
}
