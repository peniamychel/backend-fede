package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Un sistema: el agregado que un lote puede tener o no.
 * <p>
 * Es una entidad propia y no un campo del lote porque se puede vender y pasar a
 * otro lote. Un campo diría dónde está hoy; una entidad con historial dice
 * también de dónde vino, que es lo que hace falta cuando alguien reclama.
 */
@Entity
@Table(
        name = "sistemas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sistema_codigo", columnNames = "codigo")
)
@Getter
@Setter
@NoArgsConstructor
public class Sistema extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Cómo se lo identifica. Único: es lo que se nombra en un acta de venta,
     * así que dos sistemas con el mismo código harían imposible saber cuál se
     * vendió.
     */
    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(length = 200)
    private String descripcion;

    @JsonIgnore
    @OneToMany(mappedBy = "sistema", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TenenciaSistema> tenencias = new ArrayList<>();
}
