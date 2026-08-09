package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Columna "SINDICATO" del padrón: unidad de base, pertenece a una
 * {@link Central}.
 * <p>
 * Son 107 sindicatos. <b>El nombre no es único a nivel federación</b>: en la
 * planilla hay 3 nombres que se repiten en más de una central
 * ("1RO DE MAYO" en 13 DE JUNIO, PUERTO VILLARROEL y SANTA FE; "SANTA FE" en
 * SANTA FE y UNIFICADA; "GUALBERTO VILLARROEL" en SANTA FE y VALLE IVIRZA).
 * Por eso la clave única es el par (central, nombre) y no el nombre solo.
 */
@Entity
@Table(
        name = "sindicatos",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sindicato_central_nombre",
                        columnNames = {"central_id", "nombre"}
                ),
                // A diferencia del nombre, el número sí es único a nivel
                // general: lo asigna la federación y no se repite.
                @UniqueConstraint(name = "uk_sindicato_numero", columnNames = "numero")
        },
        indexes = @Index(name = "idx_sindicato_central", columnList = "central_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sindicato extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    /**
     * Número con el que la federación identifica a este sindicato.
     * <p>
     * Opcional, único, y texto por los mismos motivos que el de la central.
     * Todavía no se imprime en el informe: falta definir cuál de las dos
     * casillas del encabezado le corresponde.
     */
    @Column(length = 20)
    private String numero;

    /**
     * Latitud de la sede, en grados decimales. Null mientras no se haya
     * marcado en el mapa.
     * <p>
     * Se guarda como DECIMAL y no como double a propósito: un double redondea
     * y en coordenadas ese redondeo se traduce en metros de error. Con 7
     * decimales la precisión es de aproximadamente un centímetro, de sobra para
     * ubicar una sede sindical.
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitud;

    /** Longitud de la sede, en grados decimales. */
    @Column(precision = 10, scale = 7)
    private BigDecimal longitud;

    /** Cuándo se marcó la ubicación por última vez. Null si nunca se marcó. */
    @Column(name = "ubicacion_actualizada_en")
    private LocalDateTime ubicacionActualizadaEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "central_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sindicato_central"))
    private Central central;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "sindicato", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Productor> productores = new ArrayList<>();

    public void agregarProductor(Productor productor) {
        productores.add(productor);
        productor.setSindicato(this);
    }

    /**
     * Una ubicación solo cuenta si están las dos coordenadas: media coordenada
     * no ubica nada, y dejar que exista sería permitir un estado sin sentido.
     */
    @Transient
    public boolean tieneUbicacion() {
        return latitud != null && longitud != null;
    }

    public void marcarUbicacion(BigDecimal lat, BigDecimal lon) {
        this.latitud = lat;
        this.longitud = lon;
        this.ubicacionActualizadaEn = LocalDateTime.now();
    }

    public void borrarUbicacion() {
        this.latitud = null;
        this.longitud = null;
        this.ubicacionActualizadaEn = null;
    }
}
