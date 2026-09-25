package com.federa.backend.seguridad;

import com.federa.backend.model.Usuario;
import com.federa.backend.model.Permiso;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AutorizacionesUsuario {
    private AutorizacionesUsuario() {}
    public static Set<String> permisos(Usuario usuario) {
        Set<String> resultado = new LinkedHashSet<>();
        var activos = usuario.getRolesAcceso().stream().filter(r -> r.isEstado()).toList();
        if (usuario.isPermisosPersonalizados()) {
            if (!activos.isEmpty()) usuario.getPermisosAcceso().stream().map(Permiso::getCodigo).forEach(resultado::add);
        } else {
            activos.forEach(r -> r.getPermisos().stream().map(Permiso::getCodigo).forEach(resultado::add));
        }
        if (usuario.getCentralAcceso() != null) resultado.retainAll(AlcanceCentral.PERMISOS);
        return resultado;
    }
}
