package com.federa.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

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

    /** Null para acceso general; de otro modo solo esta central. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "central_acceso_id")
    private Central centralAcceso;

    @Column(name = "todos_sindicatos", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean todosSindicatos = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuarios_sindicatos_acceso",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "sindicato_id"))
    @Builder.Default
    private Set<Sindicato> sindicatosAcceso = new LinkedHashSet<>();

    @Column(name = "permisos_personalizados", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean permisosPersonalizados = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuarios_permisos_acceso",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    @Builder.Default
    private Set<Permiso> permisosAcceso = new LinkedHashSet<>();

    /** Rol único por ahora: ADMIN u OPERADOR. */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String rol = "OPERADOR";

    @Column(name = "codigo_acceso_identificador", unique = true, length = 24)
    private String codigoAccesoIdentificador;

    @JsonIgnore
    @Column(name = "codigo_acceso_hash", length = 100)
    private String codigoAccesoHash;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuarios_roles_acceso",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    @Builder.Default
    private Set<RolAcceso> rolesAcceso = new LinkedHashSet<>();

    // El usuario tenía su propio `activo` y su propio `creado_en`, con un
    // @PrePersist a mano. Los reemplaza EntidadAuditable: `estado` cumple lo
    // mismo que `activo` —un usuario deshabilitado no puede iniciar sesión— y
    // las fechas ya no hay que mantenerlas. Los valores viejos se migraron.
}
