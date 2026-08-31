package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una fotografía original de la lista física de un sindicato. */
@Entity
@Table(
        name = "paginas_lista_fisica_sindicato",
        indexes = @Index(name = "idx_lista_fisica_sindicato",
                columnList = "sindicato_id, orden")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginaListaFisicaSindicato extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sindicato_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lista_fisica_sindicato"))
    private Sindicato sindicato;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false, length = 220)
    private String clave;

    @Column(nullable = false, length = 180)
    private String nombre;

    @Column(name = "tipo_mime", nullable = false, length = 40)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private int tamanoBytes;
}
