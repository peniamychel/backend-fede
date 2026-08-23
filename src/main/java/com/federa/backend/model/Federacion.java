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
 * Nivel más alto de la organización: agrupa {@link Central centrales}.
 * <p>
 * Una federación tiene muchas centrales y cada central pertenece a una sola.
 */
@Entity
@Table(
        name = "federaciones",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_federacion_nombre", columnNames = "nombre"),
                // Que sea una clave de la base y no solo una comprobación en
                // Java es lo que impide que dos altas simultáneas metan el
                // mismo número.
                @UniqueConstraint(name = "uk_federacion_numero", columnNames = "numero")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Federacion extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    /**
     * Número con el que se identifica a esta federación.
     * <p>
     * Opcional, y único entre todas. Va como texto y no como entero para
     * conservar los ceros a la izquierda y cualquier sufijo que usen, igual que
     * el número del sindicato.
     * <p>
     * La unicidad la garantiza {@code uk_federacion_numero}. En MariaDB una
     * clave única admite varios NULL, que es justo lo que hace falta acá:
     * muchas federaciones sin número, y a lo sumo una por cada número dado.
     */
    @Column(length = 20)
    private String numero;

    /** Clave de la imagen del sello institucional en el almacén de objetos. */
    @Column(name = "sello_clave", length = 200)
    private String selloClave;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "federacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Central> centrales = new ArrayList<>();

    public void agregarCentral(Central central) {
        centrales.add(central);
        central.setFederacion(this);
    }
}
