package com.federa.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.dto.DisenoCredencial;
import com.federa.backend.model.ConfiguracionCredencial;
import com.federa.backend.repository.ConfiguracionCredencialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisenoCredencialServiceTest {

    private final ConfiguracionCredencialRepository repository =
            mock(ConfiguracionCredencialRepository.class);
    private final AlmacenObjetos almacen = mock(AlmacenObjetos.class);
    private final ProcesadorImagenes procesador = mock(ProcesadorImagenes.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DisenoCredencialService service = new DisenoCredencialService(
            repository, objectMapper, almacen, procesador);

    @Test
    @DisplayName("los diseños anteriores reciben una capa de plantilla por cada cara")
    void migraPlantillasACapas() throws Exception {
        DisenoCredencial base = DisenoCredencial.porDefecto();
        List<DisenoCredencial.Elemento> anteriores = base.elementos().stream()
                .filter(e -> e.tipo() != DisenoCredencial.Tipo.PLANTILLA)
                .toList();
        ConfiguracionCredencial entidad = new ConfiguracionCredencial();
        entidad.setId(1L);
        entidad.setDisenoJson(objectMapper.writeValueAsString(
                new DisenoCredencial(base.ancho(), base.alto(), anteriores)));
        when(repository.findById(1L)).thenReturn(Optional.of(entidad));

        DisenoCredencial actualizado = service.editor().diseno();

        assertThat(actualizado.elementos())
                .filteredOn(e -> e.tipo() == DisenoCredencial.Tipo.PLANTILLA)
                .extracting(DisenoCredencial.Elemento::cara)
                .containsExactlyInAnyOrder(DisenoCredencial.Cara.CARA,
                        DisenoCredencial.Cara.REVERSO);
    }

    @Test
    @DisplayName("los diseños anteriores reciben Roboto como fuente predeterminada")
    void migraFuentePredeterminada() throws Exception {
        ObjectNode json = (ObjectNode) objectMapper.valueToTree(DisenoCredencial.porDefecto());
        json.withArray("elementos").forEach(elemento ->
                ((ObjectNode) elemento).remove("fuente"));
        ConfiguracionCredencial entidad = new ConfiguracionCredencial();
        entidad.setId(1L);
        entidad.setDisenoJson(objectMapper.writeValueAsString(json));
        when(repository.findById(1L)).thenReturn(Optional.of(entidad));

        DisenoCredencial actualizado = service.editor().diseno();

        assertThat(actualizado.elementos())
                .extracting(DisenoCredencial.Elemento::fuente)
                .containsOnly(DisenoCredencial.Fuente.ROBOTO);
    }

    @Test
    @DisplayName("una imagen nueva se normaliza como PNG y recibe una clave única")
    void guardaImagenPersonalizada() {
        byte[] png = {9, 8, 7};
        when(procesador.prepararPng(any(byte[].class), eq(1800), eq(1024 * 1024)))
                .thenReturn(new ProcesadorImagenes.Variante(png, 300, 200, "image/png"));
        when(almacen.urlPublica(anyString())).thenAnswer(
                invocacion -> "/api/v1/archivos/" + invocacion.getArgument(0, String.class));

        DisenoCredencialService.ImagenPersonalizada imagen =
                service.guardarImagen(new byte[]{1, 2, 3});

        assertThat(imagen.clave())
                .matches("configuracion/credencial/objetos/[a-f0-9-]+\\.png");
        assertThat(imagen.url()).endsWith(imagen.clave());
        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        verify(almacen).guardar(clave.capture(), eq(png));
        assertThat(clave.getValue()).isEqualTo(imagen.clave());
    }

    @Test
    @DisplayName("la plantilla conserva PNG y su canal transparente")
    void guardaPlantillaComoPng() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        String clave = DisenoCredencialService.clavePlantilla(DisenoCredencial.Cara.CARA);
        when(procesador.prepararPng(any(byte[].class), eq(1800), eq(1024 * 1024)))
                .thenReturn(new ProcesadorImagenes.Variante(png, 856, 540, "image/png"));
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(almacen.existe(clave)).thenReturn(true);
        when(almacen.leer(clave)).thenReturn(png);

        service.guardarPlantilla(DisenoCredencial.Cara.CARA, new byte[]{1, 2, 3});
        DisenoCredencialService.PlantillaArchivo guardada =
                service.plantilla(DisenoCredencial.Cara.CARA);

        verify(almacen).guardar(clave, png);
        assertThat(guardada.contenido()).isEqualTo(png);
        assertThat(guardada.tipoMime()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("una plantilla JPEG anterior continúa siendo legible")
    void conservaCompatibilidadConPlantillaJpeg() {
        String clave = DisenoCredencialService.clavePlantilla(DisenoCredencial.Cara.REVERSO);
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        when(almacen.existe(clave)).thenReturn(true);
        when(almacen.leer(clave)).thenReturn(jpeg);

        assertThat(service.plantilla(DisenoCredencial.Cara.REVERSO).tipoMime())
                .isEqualTo("image/jpeg");
    }
}
