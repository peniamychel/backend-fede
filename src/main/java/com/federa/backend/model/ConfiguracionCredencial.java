package com.federa.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Única configuración compartida del diseño de las credenciales. */
@Entity
@Table(name = "configuracion_credencial")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionCredencial extends EntidadAuditable {

    @Id
    private Long id;

    @Lob
    @Column(name = "diseno_json", nullable = false, columnDefinition = "LONGTEXT")
    private String disenoJson;
}
