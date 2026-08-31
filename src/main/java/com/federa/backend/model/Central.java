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
                // Única a secas, no por federación: la sigla sirve para
                // distinguir centrales entre sí y no se repite. Que sea una
                // clave de la base y no solo una comprobación en Java es lo que
                // impide que dos altas simultáneas metan la misma.
                @UniqueConstraint(name = "uk_central_abreviatura", columnNames = "abreviatura")
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
     * Sigla de tres caracteres con la que se abrevia a esta central.
     * <p>
     * Letras o números: varias centrales empiezan con un dígito, y la sigla de
     * 1RO DE MAYO es 1MO.
     * <p>
     * Reemplaza al número que llevaba antes. Se escribe a mano y es opcional:
     * todavía no están todas definidas.
     * <p>
     * Se guarda siempre en mayúsculas, y eso no es cosmética: la clave única
     * compara como esté guardado, así que si una central quedara con "ivi" y
     * otra con "IVI", la base las vería distintas y dejaría pasar las dos.
     * <p>
     * La unicidad la garantiza {@code uk_central_abreviatura}. En MariaDB una
     * clave única admite varios NULL, que es justo lo que hace falta acá:
     * muchas centrales sin sigla, y a lo sumo una por cada sigla dada.
     */
    @Column(length = 3)
    private String abreviatura;

    /** Clave de la imagen del sello institucional en el almacén de objetos. */
    @Column(name = "sello_clave", length = 200)
    private String selloClave;

    @Column(name = "sello_original_clave", length = 200)
    private String selloOriginalClave;

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
