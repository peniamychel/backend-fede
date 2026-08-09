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
        uniqueConstraints = @UniqueConstraint(name = "uk_federacion_nombre", columnNames = "nombre")
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

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "federacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Central> centrales = new ArrayList<>();

    public void agregarCentral(Central central) {
        centrales.add(central);
        central.setFederacion(this);
    }
}
