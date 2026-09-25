package com.federa.backend.seguridad;

import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/** Alcance leído de la sesión vigente, nunca de parámetros enviados por el cliente. */
public final class AlcanceCentral {
    private AlcanceCentral() {}
    public static final Set<String> PERMISOS = Set.of("PRODUCTORES_VER",
            "FOTOS_PRODUCTORES_EDITAR", "PRODUCTORES_OBSERVAR", "NUMERO_LOTE_EDITAR",
            "INFORMES_DESCARGAR");
    public record Datos(Long centralId, Long federacionId, boolean todosSindicatos, Set<Long> sindicatoIds) {
        public Datos { sindicatoIds = Set.copyOf(sindicatoIds); }
        public Datos(Long centralId, Long federacionId) { this(centralId, federacionId, true, Set.of()); }
    }
    public static boolean permiteSindicato(Long id) {
        var alcance = actual();
        return alcance == null || alcance.todosSindicatos() || alcance.sindicatoIds().contains(id);
    }
    public static void verificarSindicato(Long id) { if (!permiteSindicato(id)) denegar(); }
    public static Datos actual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof Datos datos ? datos : null;
    }
    public static Long id() { return actual() == null ? null : actual().centralId(); }
    public static Long limitar(Long solicitado) {
        Long id = id();
        if (id != null && solicitado != null && !id.equals(solicitado)) denegar();
        return id == null ? solicitado : id;
    }
    public static void denegar() { throw new AccessDeniedException("Sin acceso a esta operación o central"); }
}
