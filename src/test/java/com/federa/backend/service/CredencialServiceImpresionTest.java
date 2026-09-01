package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.model.DetalleGrupoImpresionCredencial;
import com.federa.backend.model.GrupoImpresionCredencial;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.DetalleGrupoImpresionCredencialRepository;
import com.federa.backend.repository.GrupoImpresionCredencialRepository;
import com.federa.backend.repository.ImagenProductorRepository;
import com.federa.backend.repository.LoteRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.SindicatoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredencialServiceImpresionTest {

    @Mock ProductorRepository productorRepository;
    @Mock SindicatoRepository sindicatoRepository;
    @Mock LoteRepository loteRepository;
    @Mock ImagenProductorRepository imagenRepository;
    @Mock CargoRepository cargoRepository;
    @Mock AlmacenObjetos almacen;
    @Mock CredencialProductorPdf generador;
    @Mock CredencialDirigentePdf generadorDirigente;
    @Mock GeneradorQr generadorQr;
    @Mock RequisitosCredencial requisitos;
    @Mock VetoService vetoService;
    @Mock DisenoCredencialService disenoCredencialService;
    @Mock GrupoImpresionCredencialRepository grupoRepository;
    @Mock DetalleGrupoImpresionCredencialRepository detalleRepository;

    private CredencialService servicio;
    private Sindicato sindicato;
    private Productor productor;
    private LocalDateTime impresionAnterior;
    private CredencialService.PanelImpresionSindicato panel;

    @BeforeEach
    void preparar() {
        servicio = spy(new CredencialService(productorRepository, sindicatoRepository,
                loteRepository, imagenRepository, cargoRepository, almacen, generador,
                generadorDirigente, generadorQr, requisitos, vetoService,
                disenoCredencialService, grupoRepository, detalleRepository));
        sindicato = Sindicato.builder().id(13L).nombre("1RO DE MAYO").build();
        impresionAnterior = LocalDateTime.of(2026, 8, 20, 10, 30);
        productor = Productor.builder().id(81L).nombres("MARÍA").apellidos("PÉREZ")
                .sindicato(sindicato).credencialImpresiones(2)
                .credencialUltimaImpresion(impresionAnterior).build();
        panel = new CredencialService.PanelImpresionSindicato(
                13L, "1RO DE MAYO", 1, 1, 0, 0, 0, List.of(), List.of());
        doReturn(panel).when(servicio).panelImpresionSindicato(13L);
    }

    @Test
    void confirmarGuardaLaTandaYElEstadoAnterior() {
        when(productorRepository.findAllById(any())).thenReturn(List.of(productor));
        when(productorRepository.findAllByIdParaImpresion(anyList()))
                .thenReturn(List.of(productor));
        when(sindicatoRepository.findById(13L)).thenReturn(Optional.of(sindicato));
        when(grupoRepository.saveAndFlush(any())).thenAnswer(invocacion -> {
            GrupoImpresionCredencial grupo = invocacion.getArgument(0);
            grupo.setId(41L);
            return grupo;
        });

        servicio.confirmarAnversosImpresos(13L,
                new CredencialService.SeleccionImpresion(List.of(81L), true));

        assertThat(productor.getCredencialImpresiones()).isEqualTo(3);
        assertThat(productor.getCredencialUltimaImpresion()).isAfter(impresionAnterior);
        ArgumentCaptor<List<DetalleGrupoImpresionCredencial>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(detalleRepository).saveAll(captor.capture());
        DetalleGrupoImpresionCredencial detalle = captor.getValue().get(0);
        assertThat(detalle.getConteoAnterior()).isEqualTo(2);
        assertThat(detalle.getUltimaImpresionAnterior()).isEqualTo(impresionAnterior);
        assertThat(detalle.isContabilizado()).isTrue();
    }

    @Test
    void revisarPuedeCancelarYVolverAConfirmarSinDuplicarConteos() {
        LocalDateTime enviada = LocalDateTime.of(2026, 9, 1, 9, 15);
        productor.setCredencialImpresiones(3);
        productor.setCredencialUltimaImpresion(enviada);
        GrupoImpresionCredencial grupo = new GrupoImpresionCredencial(sindicato, enviada);
        grupo.setId(41L);
        DetalleGrupoImpresionCredencial detalle = new DetalleGrupoImpresionCredencial(
                grupo, productor, 2, impresionAnterior);
        when(grupoRepository.findFirstBySindicatoIdOrderByEnviadoEnDescIdDesc(13L))
                .thenReturn(Optional.of(grupo));
        when(detalleRepository.findByGrupoIdOrderByIdAsc(41L))
                .thenReturn(List.of(detalle));
        when(productorRepository.findAllByIdParaImpresion(anyList()))
                .thenReturn(List.of(productor));

        CredencialService.RevisionGrupoImpresion ninguno =
                new CredencialService.RevisionGrupoImpresion(41L, List.of());
        servicio.revisarUltimoGrupo(13L, ninguno);
        assertThat(productor.getCredencialImpresiones()).isEqualTo(2);
        assertThat(productor.getCredencialUltimaImpresion()).isEqualTo(impresionAnterior);
        assertThat(detalle.isContabilizado()).isFalse();

        servicio.revisarUltimoGrupo(13L, ninguno);
        assertThat(productor.getCredencialImpresiones()).isEqualTo(2);

        servicio.revisarUltimoGrupo(13L,
                new CredencialService.RevisionGrupoImpresion(41L, List.of(81L)));
        assertThat(productor.getCredencialImpresiones()).isEqualTo(3);
        assertThat(productor.getCredencialUltimaImpresion()).isEqualTo(enviada);
        assertThat(detalle.isContabilizado()).isTrue();
    }
}
