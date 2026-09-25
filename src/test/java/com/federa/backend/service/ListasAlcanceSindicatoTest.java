package com.federa.backend.service;

import com.federa.backend.model.*;
import com.federa.backend.repository.*;
import com.federa.backend.seguridad.AlcanceCentral;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ListasAlcanceSindicatoTest {
    @BeforeEach void alcance() {
        var auth = new UsernamePasswordAuthenticationToken("fotos", null, List.of());
        auth.setDetails(new AlcanceCentral.Datos(3L, 1L, false, Set.of(7L, 8L)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void paginaYBusquedaSeFiltranEnLaConsultaAntesDePaginar() {
        var repo = mock(ProductorRepository.class);
        when(repo.filtrarConAlcance(isNull(), eq(3L), eq("MARIA"), anyString(), anyString(),
                eq(true), eq(Set.of(7L, 8L)), any())).thenReturn(Page.empty());
        var service = new ProductorService(repo, mock(TenenciaLoteRepository.class),
                mock(ImagenProductorRepository.class), mock(ImagenCargoRepository.class),
                mock(SindicatoService.class), mock(NumeradorPadron.class),
                mock(com.federa.backend.almacen.AlmacenObjetos.class),
                mock(VetoRepository.class), mock(com.federa.backend.repository.CargoRepository.class),
                mock(com.federa.backend.repository.AsistenciaRepository.class),
                mock(com.federa.backend.repository.DetalleGrupoImpresionCredencialRepository.class));
        assertEquals(0, service.listar(null, null, "MARIA", PageRequest.of(0, 25)).getTotalElements());
        verify(repo).filtrarConAlcance(isNull(), eq(3L), eq("MARIA"), anyString(), anyString(),
                eq(true), eq(Set.of(7L, 8L)), any());
        verify(repo, never()).filtrar(any(), any(), any(), any(), any(), any());
    }

    @Test void listaDeSindicatosSoloIncluyeLosElegidos() {
        var repo = mock(SindicatoRepository.class);
        var c = new Central(); c.setId(3L); c.setNombre("CENTRAL");
        var permitidos = new ArrayList<Sindicato>();
        for (long id : List.of(7L, 8L, 9L)) {
            var s = new Sindicato(); s.setId(id); s.setNombre("SINDICATO " + id); s.setCentral(c);
            permitidos.add(s);
        }
        when(repo.findByCentralIdOrderByNombreAsc(3L)).thenReturn(permitidos);
        var service = new SindicatoService(repo, mock(ProductorRepository.class),
                mock(CentralService.class), mock(NumeradorPadron.class));
        assertEquals(List.of(7L, 8L), service.listar(null).stream().map(s -> s.id()).toList());
    }
}
