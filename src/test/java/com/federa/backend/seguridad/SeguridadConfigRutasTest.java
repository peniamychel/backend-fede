package com.federa.backend.seguridad;

import com.federa.backend.repository.SesionUsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = SeguridadConfigRutasTest.AplicacionPrueba.class,
        properties = {
                "federa.seguridad.exigir-autenticacion=true",
                "federa.seguridad.origenes=http://localhost:5173"
        })
@AutoConfigureMockMvc
class SeguridadConfigRutasTest {
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @Import({SeguridadConfig.class, JwtFiltro.class,
            AccesoCentralInterceptor.class, AccesoCentralConfig.class,
            PuntoDeEntradaNoAutorizado.class, AccesoDenegadoJson.class,
            ControladorPrueba.class})
    static class AplicacionPrueba {}

    @RestController
    @RequestMapping("/api/v1")
    static class ControladorPrueba {
        @PostMapping("/auth/acceso") void acceso() {}
        @GetMapping("/auth/yo") void yo() {}
        @GetMapping("/productores/{id}/imagenes") void verImagenes() {}
        @PostMapping("/productores/{id}/imagenes") void editarImagenes() {}
        @DeleteMapping("/productores/{id}/imagenes") void borrarImagenes() {}
        @PutMapping("/productores/{id}/numero-lote") void numeroLote() {}
        @PatchMapping("/productores/{id}/observacion") void observacion() {}
        @GetMapping("/productores/{id}/credencial/previa") void previaCarnetProductor() {}
        @GetMapping("/productores/{id}/credencial.pdf") void carnetProductor() {}
        @GetMapping("/configuracion/credencial") void verDiseno() {}
        @GetMapping("/configuracion/credencial/plantilla/{cara}") void verPlantilla() {}
        @PutMapping("/configuracion/credencial") void editarDiseno() {}
        @GetMapping("/centrales/{id}/fases-impresion") void verFases() {}
        @PostMapping("/centrales/{id}/fases-impresion/habilitar") void habilitarFase() {}
        @GetMapping("/sindicatos/{id}/credenciales/impresion") void estadoImpresion() {}
        @GetMapping("/sindicatos/{id}/credenciales/impresion/reversos.pdf") void reversos() {}
        @GetMapping("/sindicatos/{id}/informes/avance") void avanceSindicato() {}
        @GetMapping("/sindicatos/{id}/informes/revision-padron.pdf") void revisionPadronSindicato() {}
        @GetMapping("/sindicatos/{id}/informes/pre-impresion.pdf") void preImpresionSindicato() {}
        @GetMapping("/sindicatos/{id}/informes/nomina.pdf") void nominaSindicato() {}
        @GetMapping("/sindicatos/{id}/informes/fases") void fasesSindicato() {}
        @GetMapping("/sindicatos/{id}/informes/fases/{faseId}/informe.pdf") void informeFaseSindicato() {}
    }

    @jakarta.annotation.Resource MockMvc mvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean SesionUsuarioRepository sesiones;
    @MockitoBean com.federa.backend.repository.ProductorRepository productores;
    @MockitoBean com.federa.backend.repository.SindicatoRepository sindicatos;
    @MockitoBean com.federa.backend.repository.LoteRepository lotes;

    @Test
    void usuarioCentralPuedeHacerSoloLasOperacionesAutorizadasEnSuCentral() throws Exception {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "fotografo", null, AlcanceCentral.PERMISOS.stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList());
        auth.setDetails(new AlcanceCentral.Datos(3L, 1L));
        var autorizado = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth);
        var central = new com.federa.backend.model.Central(); central.setId(3L);
        var sindicato = new com.federa.backend.model.Sindicato(); sindicato.setCentral(central);
        var productor = new com.federa.backend.model.Productor(); productor.setSindicato(sindicato);
        org.mockito.Mockito.when(productores.findById(7L)).thenReturn(java.util.Optional.of(productor));
        mvc.perform(post("/api/v1/productores/7/imagenes").with(autorizado)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/productores/7/imagenes").with(autorizado)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/productores/7/observacion").with(autorizado)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/productores/7/numero-lote").with(autorizado)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/productores/7/credencial/previa").with(autorizado))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/productores/7/revision-sie").with(autorizado)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/lotes/7").with(autorizado)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/productores/7/observacion").with(autorizado)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/productores/7").with(autorizado)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/productores/7/credencial.pdf").with(autorizado)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cargos/7/imagenes").with(autorizado)).andExpect(status().isForbidden());
        central.setId(4L);
        mvc.perform(post("/api/v1/productores/7/imagenes").with(autorizado)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/productores/7/numero-lote").with(autorizado)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/productores/7/imagenes").with(autorizado)).andExpect(status().isForbidden());
    }

    @Test
    void soloElAccesoEsPublicoDentroDeAuth() throws Exception {
        mvc.perform(post("/api/v1/auth/acceso").contentType("application/json"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/yo"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/configuracion/credencial/plantilla/CARA"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/configuracion/credencial"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PRODUCTORES_VER")
    void consultaPuedeVerImagenesPeroNoModificarlas() throws Exception {
        mvc.perform(get("/api/v1/productores/7/imagenes"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/productores/7/imagenes"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/productores/7/credencial.pdf"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sindicatos/3/credenciales/impresion"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sindicatos/3/credenciales/impresion/reversos.pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CARNETS_IMPRIMIR")
    void impresionPuedeLeerElDisenoPeroNoCambiarlo() throws Exception {
        mvc.perform(get("/api/v1/configuracion/credencial"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/configuracion/credencial"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/productores/7/credencial.pdf"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "INFORMES_DESCARGAR")
    void informesPuedeConsultarFasesPeroNoAbrirlas() throws Exception {
        mvc.perform(get("/api/v1/centrales/2/fases-impresion"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/centrales/2/fases-impresion/habilitar"))
                .andExpect(status().isForbidden());
    }

    @Test
    void informesDelSindicatoExigenPermisoYAlcance() throws Exception {
        var central = new com.federa.backend.model.Central(); central.setId(3L);
        var sindicato = new com.federa.backend.model.Sindicato();
        sindicato.setId(7L); sindicato.setCentral(central);
        org.mockito.Mockito.when(sindicatos.findById(7L)).thenReturn(java.util.Optional.of(sindicato));
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "reportes", null, java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("INFORMES_DESCARGAR")));
        auth.setDetails(new AlcanceCentral.Datos(3L, 1L, false, java.util.Set.of(7L)));
        var autorizado = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth);
        mvc.perform(get("/api/v1/sindicatos/7/informes/avance").with(autorizado))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sindicatos/7/informes/revision-padron.pdf").with(autorizado))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sindicatos/7/informes/pre-impresion.pdf").with(autorizado))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sindicatos/7/informes/nomina.pdf").with(autorizado))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sindicatos/7/informes/fases").with(autorizado))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sindicatos/7/informes/fases/11/informe.pdf").with(autorizado))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/sindicatos/8/informes/avance").with(autorizado))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sindicatos/8/informes/revision-padron.pdf").with(autorizado))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sindicatos/8/informes/pre-impresion.pdf").with(autorizado))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sindicatos/8/informes/nomina.pdf").with(autorizado))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sindicatos/8/informes/fases").with(autorizado))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sindicatos/8/informes/fases/11/informe.pdf").with(autorizado))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FASES_GESTIONAR")
    void gestorPuedeHabilitarFases() throws Exception {
        mvc.perform(post("/api/v1/centrales/2/fases-impresion/habilitar"))
                .andExpect(status().isOk());
    }
}
