package com.federa.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saludos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Saludo extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String mensaje;

    @Column(nullable = false, length = 80)
    private String autor;
}
