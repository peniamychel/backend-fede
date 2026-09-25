package com.federa.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sesiones_usuario", indexes = {
        @Index(name = "idx_sesion_usuario_activa", columnList = "usuario_id,revocada,expira_en")
})
@Getter
@Setter
public class SesionUsuario {
    @Id
    @Column(length = 36)
    private String id;

    // El filtro de seguridad consulta la sesión fuera de una transacción larga;
    // usuario y sus roles deben estar disponibles en ese mismo acceso.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "creada_en", nullable = false, columnDefinition = "datetime")
    private LocalDateTime creadaEn;

    @Column(name = "expira_en", nullable = false, columnDefinition = "datetime")
    private LocalDateTime expiraEn;

    @Column(nullable = false)
    private boolean revocada;

    @Column(name = "revocada_en", columnDefinition = "datetime")
    private LocalDateTime revocadaEn;

    @PrePersist
    void preparar() {
        if (id == null) id = UUID.randomUUID().toString();
        if (creadaEn == null) creadaEn = LocalDateTime.now();
    }
}
