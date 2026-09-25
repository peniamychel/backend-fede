package com.federa.backend.seguridad;

import com.federa.backend.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;

/** Lista explícita: nuevas rutas no heredan acceso al padrón completo. */
@Component
public class AccesoCentralInterceptor implements HandlerInterceptor {
    private final ProductorRepository productores;
    private final SindicatoRepository sindicatos;
    private final LoteRepository lotes;

    public AccesoCentralInterceptor(ProductorRepository productores, SindicatoRepository sindicatos,
                                   LoteRepository lotes) {
        this.productores = productores; this.sindicatos = sindicatos; this.lotes = lotes;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var alcance = AlcanceCentral.actual();
        if (alcance == null || "OPTIONS".equals(request.getMethod())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String metodo = request.getMethod();
        if (path.equals("/api/v1/auth/yo") || path.equals("/api/v1/auth/logout")) return true;
        // Los archivos usan claves aleatorias compartidas por los clientes existentes.
        if (metodo.equals("GET") && path.startsWith("/api/v1/archivos/")) return true;
        Long central = alcance.centralId();
        if (metodo.equals("GET")) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean informeSindicato = path.matches(
                    "/api/v1/sindicatos/\\d+/informes/"
                            + "(avance|revision-padron\\.pdf|pre-impresion\\.pdf|nomina\\.pdf|fases"
                            + "|fases/\\d+/informe\\.pdf)");
            boolean puedeVerPadron = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("PRODUCTORES_VER"));
            boolean puedeVerInforme = informeSindicato && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("INFORMES_DESCARGAR"));
            if (!puedeVerPadron && !puedeVerInforme) {
                AlcanceCentral.denegar();
            }
            if (path.equals("/api/v1/federaciones") || path.equals("/api/v1/centrales")) return true;
            if (path.matches("/api/v1/federaciones/\\d+(/centrales)?")) {
                comprobar(alcance.federacionId(), id(path)); return true;
            }
            if (path.equals("/api/v1/sindicatos") || path.equals("/api/v1/productores")) {
                parametroCentral(request);
                parametroSindicato(request, central);
                return true;
            }
            if (path.matches("/api/v1/centrales/\\d+(/sindicatos|/credenciales/impresion)?")) {
                comprobar(central, id(path)); return true;
            }
            if (path.matches("/api/v1/sindicatos/\\d+")) {
                sindicato(id(path), central); return true;
            }
            if (informeSindicato) {
                sindicato(id(path), central); return true;
            }
            if (path.equals("/api/v1/lotes")) {
                String productor = request.getParameter("productorId");
                if (productor != null) productor(Long.valueOf(productor), central);
                parametroSindicato(request, central);
                if (productor == null && request.getParameter("sindicatoId") == null) AlcanceCentral.denegar();
                return true;
            }
            if (path.matches("/api/v1/lotes/\\d+")) {
                var lote = lotes.findById(id(path)).orElseThrow(this::denegado);
                AlcanceCentral.verificarSindicato(lote.getSindicato().getId());
                comprobar(central, lote.getSindicato().getCentral().getId()); return true;
            }
            if (path.matches("/api/v1/productores/\\d+(/imagenes(/descarga)?)?")) {
                productor(id(path), central); return true;
            }
            // La vista previa es una consulta de la ficha, no una emisión. Un
            // productor vetado debe poder verse con su bloqueo, aunque el PDF
            // y el registro de impresión sigan prohibidos por CredencialService.
            if (path.matches("/api/v1/productores/\\d+/credencial/previa")) {
                productor(id(path), central); return true;
            }
            // El diseño solo describe cómo se dibuja la tarjeta. Hace falta
            // para representar la vista previa, pero no concede la posibilidad
            // de generar ni contabilizar una impresión.
            if (path.equals("/api/v1/configuracion/credencial")
                    || path.matches("/api/v1/configuracion/credencial/plantilla/(CARA|REVERSO)")) {
                return true;
            }
        }
        if ((metodo.equals("POST") || metodo.equals("DELETE"))
                && path.matches("/api/v1/productores/\\d+/imagenes")) {
            productor(id(path), central); return true;
        }
        if (metodo.equals("PATCH") && path.matches("/api/v1/productores/\\d+/observacion")) {
            productor(id(path), central); return true;
        }
        if (metodo.equals("PUT") && path.matches("/api/v1/productores/\\d+/numero-lote")) {
            productor(id(path), central); return true;
        }
        AlcanceCentral.denegar();
        return false;
    }

    private long id(String path) { return Long.parseLong(path.split("/")[4]); }
    private void parametroCentral(HttpServletRequest request) {
        String valor = request.getParameter("centralId");
        if (valor != null) AlcanceCentral.limitar(Long.valueOf(valor));
    }
    private void parametroSindicato(HttpServletRequest request, Long central) {
        String valor = request.getParameter("sindicatoId");
        if (valor != null) sindicato(Long.valueOf(valor), central);
    }
    private void productor(Long id, Long central) {
        var p = productores.findById(id).orElseThrow(this::denegado);
        AlcanceCentral.verificarSindicato(p.getSindicato().getId());
        comprobar(central, p.getSindicato().getCentral().getId());
    }
    private void sindicato(Long id, Long central) {
        var s = sindicatos.findById(id).orElseThrow(this::denegado);
        AlcanceCentral.verificarSindicato(s.getId());
        comprobar(central, s.getCentral().getId());
    }
    private void comprobar(Long permitido, Long solicitado) {
        if (!permitido.equals(solicitado)) AlcanceCentral.denegar();
    }
    private org.springframework.security.access.AccessDeniedException denegado() {
        return new org.springframework.security.access.AccessDeniedException("Sin acceso a este registro");
    }
}
