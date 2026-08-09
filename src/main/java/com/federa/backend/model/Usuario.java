package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Quien entra al sistema.
 * <p>
 * El padrón guarda nombres y cédulas de personas, así que en algún momento deja
 * de tener sentido que cualquiera con la dirección pueda consultarlo y
 * modificarlo. Esta entidad es la base de eso.
 */
@Entity
@Table(
        name = "usuarios",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_usuario_nombre", columnNames = "nombre_usuario")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_usuario", nullable = false, length = 60)
    private String nombreUsuario;

    /**
     * Hash BCrypt, nunca la contraseña.
     * <p>
     * {@code @JsonIgnore} es una segunda barrera: la entidad no debería salir
     * nunca en una respuesta, pero si alguien la devuelve por descuido, el hash
     * no viaja.
     */
    @JsonIgnore
    @Column(name = "contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    @Column(name = "nombre_completo", length = 120)
    private String nombreCompleto;

    /** Rol único por ahora: ADMIN u OPERADOR. */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String rol = "OPERADOR";

    // El usuario tenía su propio `activo` y su propio `creado_en`, con un
    // @PrePersist a mano. Los reemplaza EntidadAuditable: `estado` cumple lo
    // mismo que `activo` —un usuario deshabilitado no puede iniciar sesión— y
    // las fechas ya no hay que mantenerlas. Los valores viejos se migraron.
}
