package com.federa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public final class AccesoAdministracionDtos {
    private AccesoAdministracionDtos() {}

    public record PermisoDto(Long id, String codigo, String nombre, String grupo,
                             String descripcion) {}
    public record RolDto(Long id, String codigo, String nombre, String descripcion,
                         boolean activo, List<String> permisos) {}
    public record UsuarioDto(Long id, String nombreCompleto, boolean codigoConfigurado,
                             boolean activo, List<String> roles, Long centralId, String centralNombre,
                             boolean todosSindicatos, List<Long> sindicatoIds,
                             boolean permisosPersonalizados, List<String> permisos,
                             List<String> permisosEfectivos) {}
    public record GuardarRolRequest(@NotBlank String nombre, String descripcion,
                                    @NotEmpty List<String> permisos, boolean activo) {}
    public record CrearUsuarioRequest(@NotBlank String nombreCompleto,
                                      @NotEmpty List<Long> roles, Long centralId,
                                      Boolean todosSindicatos, List<Long> sindicatoIds,
                                      Boolean permisosPersonalizados, List<String> permisos) {
        public CrearUsuarioRequest(String nombreCompleto, List<Long> roles, Long centralId) {
            this(nombreCompleto, roles, centralId, null, null, null, null);
        }
    }
    public record EditarUsuarioRequest(@NotBlank String nombreCompleto,
                                       @NotEmpty List<Long> roles, boolean activo, Long centralId,
                                       Boolean todosSindicatos, List<Long> sindicatoIds,
                                       Boolean permisosPersonalizados, List<String> permisos) {
        public EditarUsuarioRequest(String nombreCompleto, List<Long> roles, boolean activo, Long centralId) {
            this(nombreCompleto, roles, activo, centralId, null, null, null, null);
        }
    }
    public record CodigoGeneradoDto(Long usuarioId, String codigoAcceso) {}
    public record CodigoManualRequest(
            @Pattern(regexp = "[A-Za-z]{5}",
                    message = "El código debe tener exactamente 5 letras")
            String codigoAcceso) {}
}
