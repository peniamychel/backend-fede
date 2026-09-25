package com.federa.backend.seguridad;

import com.federa.backend.model.*;
import com.federa.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccesoCentralTest {
    final ProductorRepository productores = mock(ProductorRepository.class);
    final SindicatoRepository sindicatos = mock(SindicatoRepository.class);
    final LoteRepository lotes = mock(LoteRepository.class);
    final AccesoCentralInterceptor interceptor = new AccesoCentralInterceptor(productores, sindicatos, lotes);

    @BeforeEach void autenticar() {
        var auth = new UsernamePasswordAuthenticationToken("central", null,
                List.of(new SimpleGrantedAuthority("PRODUCTORES_VER")));
        auth.setDetails(new AlcanceCentral.Datos(3L, 1L));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void permiteLaFichaFotosObservacionYNumeroSoloEnSuCentral() {
        when(productores.findById(5L)).thenReturn(Optional.of(productor(3L)));
        for (String[] ruta : List.of(new String[]{"GET", "/productores/5"},
                new String[]{"GET", "/productores/5/credencial/previa"},
                new String[]{"POST", "/productores/5/imagenes"},
                new String[]{"DELETE", "/productores/5/imagenes"},
                new String[]{"GET", "/productores/5/imagenes/descarga"},
                new String[]{"PATCH", "/productores/5/observacion"},
                new String[]{"PUT", "/productores/5/numero-lote"})) {
            assertTrue(ejecutar(ruta[0], ruta[1]));
        }
        when(productores.findById(5L)).thenReturn(Optional.of(productor(8L)));
        assertThrows(AccessDeniedException.class, () -> ejecutar("GET", "/productores/5"));
        assertThrows(AccessDeniedException.class,
                () -> ejecutar("GET", "/productores/5/credencial/previa"));
        assertThrows(AccessDeniedException.class, () -> ejecutar("POST", "/productores/5/imagenes"));
        assertThrows(AccessDeniedException.class, () -> ejecutar("PATCH", "/productores/5/observacion"));
        assertThrows(AccessDeniedException.class, () -> ejecutar("PUT", "/productores/5/numero-lote"));
    }

    @Test void noPuedeConsultarSieClasificarCrearEditarEliminarNiImprimir() {
        for (String[] ruta : List.of(new String[]{"POST", "/productores/5/revision-sie"},
                new String[]{"POST", "/productores/5/verificacion-sie"},
                new String[]{"DELETE", "/productores/5/observacion"},
                new String[]{"PUT", "/lotes/5"}, new String[]{"POST", "/lotes"},
                new String[]{"POST", "/productores"}, new String[]{"PUT", "/productores/5"},
                new String[]{"DELETE", "/productores/5"}, new String[]{"PATCH", "/productores/5/estado"},
                new String[]{"GET", "/productores/5/credencial.pdf"},
                new String[]{"GET", "/productores/sin-foto"},
                new String[]{"GET", "/productores/por-cedula/123"},
                new String[]{"GET", "/administracion/accesos/usuarios"},
                new String[]{"GET", "/reuniones"},
                new String[]{"GET", "/centrales/3/credenciales/impresion/informe.pdf"})) {
            assertThrows(AccessDeniedException.class, () -> ejecutar(ruta[0], ruta[1]), ruta[1]);
        }
    }

    @Test void puedeLeerElDisenoNecesarioParaLaPreviaPeroNoImprimir() {
        assertTrue(ejecutar("GET", "/configuracion/credencial"));
        assertTrue(ejecutar("GET", "/configuracion/credencial/plantilla/CARA"));
        assertTrue(ejecutar("GET", "/configuracion/credencial/plantilla/REVERSO"));
        assertThrows(AccessDeniedException.class,
                () -> ejecutar("GET", "/productores/5/credencial.pdf"));
    }

    @Test void listasAcotanLaCentralYRechazanFiltrosAjenos() {
        assertEquals(3L, AlcanceCentral.limitar(null));
        assertEquals(3L, AlcanceCentral.limitar(3L));
        assertThrows(AccessDeniedException.class, () -> AlcanceCentral.limitar(4L));
        assertTrue(ejecutar("GET", "/federaciones/1/centrales"));
        assertTrue(ejecutar("GET", "/centrales/3/sindicatos"));
        assertThrows(AccessDeniedException.class, () -> ejecutar("GET", "/federaciones/2/centrales"));
        assertThrows(AccessDeniedException.class, () -> ejecutar("GET", "/centrales/4/sindicatos"));
        assertThrows(AccessDeniedException.class, () -> ejecutar("GET", "/lotes"));
        var request = new MockHttpServletRequest("GET", "/api/v1/productores");
        request.setParameter("centralId", "4");
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        request.removeParameter("centralId");
        request.setParameter("sindicatoId", "7");
        when(sindicatos.findById(7L)).thenReturn(Optional.of(productor(8L).getSindicato()));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test void usuarioGeneralConservaSuAcceso() {
        SecurityContextHolder.clearContext();
        assertNull(AlcanceCentral.limitar(null));
        assertTrue(ejecutar("GET", "/productores/sin-foto"));
    }
    @Test void restringeTambienEntreSindicatosDeLaMismaCentral() {
        var auth = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        auth.setDetails(new AlcanceCentral.Datos(3L, 1L, false, java.util.Set.of(7L)));
        var permitido = productor(3L);
        when(productores.findById(5L)).thenReturn(Optional.of(permitido));
        assertTrue(ejecutar("GET", "/productores/5"));
        var ajeno = productor(3L); ajeno.getSindicato().setId(8L);
        when(productores.findById(6L)).thenReturn(Optional.of(ajeno));
        for (String[] ruta : List.of(new String[]{"GET", "/productores/6"},
                new String[]{"GET", "/productores/6/credencial/previa"},
                new String[]{"POST", "/productores/6/imagenes"}, new String[]{"DELETE", "/productores/6/imagenes"},
                new String[]{"PATCH", "/productores/6/observacion"}, new String[]{"PUT", "/productores/6/numero-lote"})) {
            assertThrows(AccessDeniedException.class, () -> ejecutar(ruta[0], ruta[1]));
        }
        when(sindicatos.findById(8L)).thenReturn(Optional.of(ajeno.getSindicato()));
        assertThrows(AccessDeniedException.class, () -> ejecutar("GET", "/sindicatos/8"));
        var lote = new Lote(); lote.setSindicato(ajeno.getSindicato());
        when(lotes.findById(9L)).thenReturn(Optional.of(lote));
        assertThrows(AccessDeniedException.class, () -> ejecutar("GET", "/lotes/9"));
        var request = new MockHttpServletRequest("GET", "/api/v1/productores");
        request.setParameter("sindicatoId", "8");
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        auth.setDetails(new AlcanceCentral.Datos(3L, 1L, false, java.util.Set.of()));
        assertThrows(AccessDeniedException.class, () -> ejecutar("GET", "/productores/5"));
    }

    @Test void informesDelSindicatoRespetanLaSeleccionDeSindicatos() {
        var auth = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        auth.setDetails(new AlcanceCentral.Datos(3L, 1L, false, java.util.Set.of(7L)));
        when(sindicatos.findById(7L)).thenReturn(Optional.of(productor(3L).getSindicato()));
        assertTrue(ejecutar("GET", "/sindicatos/7/informes/avance"));
        assertTrue(ejecutar("GET", "/sindicatos/7/informes/revision-padron.pdf"));
        assertTrue(ejecutar("GET", "/sindicatos/7/informes/pre-impresion.pdf"));
        assertTrue(ejecutar("GET", "/sindicatos/7/informes/nomina.pdf"));
        assertTrue(ejecutar("GET", "/sindicatos/7/informes/fases"));
        assertTrue(ejecutar("GET", "/sindicatos/7/informes/fases/11/informe.pdf"));
        var ajeno = productor(3L).getSindicato();
        ajeno.setId(8L);
        when(sindicatos.findById(8L)).thenReturn(Optional.of(ajeno));
        assertThrows(AccessDeniedException.class,
                () -> ejecutar("GET", "/sindicatos/8/informes/avance"));
        assertThrows(AccessDeniedException.class,
                () -> ejecutar("GET", "/sindicatos/8/informes/revision-padron.pdf"));
        assertThrows(AccessDeniedException.class,
                () -> ejecutar("GET", "/sindicatos/8/informes/pre-impresion.pdf"));
        assertThrows(AccessDeniedException.class,
                () -> ejecutar("GET", "/sindicatos/8/informes/nomina.pdf"));
        assertThrows(AccessDeniedException.class,
                () -> ejecutar("GET", "/sindicatos/8/informes/fases"));
        assertThrows(AccessDeniedException.class,
                () -> ejecutar("GET", "/sindicatos/8/informes/fases/11/informe.pdf"));
    }

    boolean ejecutar(String method, String path) {
        return interceptor.preHandle(new MockHttpServletRequest(method, "/api/v1" + path),
                new MockHttpServletResponse(), new Object());
    }
    Productor productor(long centralId) {
        Central c = new Central(); c.setId(centralId);
        Sindicato s = new Sindicato(); s.setId(7L); s.setCentral(c);
        Productor p = new Productor(); p.setId(5L); p.setSindicato(s);
        return p;
    }
}
