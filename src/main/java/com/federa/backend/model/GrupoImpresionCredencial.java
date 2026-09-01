package com.federa.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Una tanda de anversos aceptada por el controlador de impresión de Windows.
 *
 * Se conserva para poder corregir el último envío si la Zebra se detuvo a
 * mitad del trabajo. No representa reversos: esos no identifican productores
 * ni modifican el historial.
 */
@Entity
@Table(name = "grupos_impresion_credencial", indexes = {
        @Index(name = "idx_grupo_impresion_sindicato_fecha",
                columnList = "sindicato_id,enviado_en")
})
@Getter
@Setter
@NoArgsConstructor
public class GrupoImpresionCredencial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sindicato_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_grupo_impresion_sindicato"))
    private Sindicato sindicato;

    @Column(name = "enviado_en", nullable = false, columnDefinition = "datetime")
    private LocalDateTime enviadoEn;

    public GrupoImpresionCredencial(Sindicato sindicato, LocalDateTime enviadoEn) {
        this.sindicato = sindicato;
        this.enviadoEn = enviadoEn;
    }
}
