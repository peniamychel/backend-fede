package com.federa.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.federa.backend.dto.DisenoCredencial;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.ConfiguracionCredencial;
import com.federa.backend.repository.ConfiguracionCredencialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
public class DisenoCredencialService {

    private static final Logger log = LoggerFactory.getLogger(DisenoCredencialService.class);
    private static final long ID = 1L;
    private static final int MAXIMO_ELEMENTOS = 60;

    private final ConfiguracionCredencialRepository repository;
    private final ObjectMapper objectMapper;

    public DisenoCredencialService(ConfiguracionCredencialRepository repository,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DisenoCredencial actual() {
        return repository.findById(ID).map(this::leer).orElseGet(DisenoCredencial::porDefecto);
    }

    @Transactional(readOnly = true)
    public DisenoCredencial.Editor editor() {
        return new DisenoCredencial.Editor(actual(), DisenoCredencial.catalogo());
    }

    @Transactional
    public DisenoCredencial.Editor guardar(DisenoCredencial diseno) {
        validar(diseno);
        ConfiguracionCredencial entidad = repository.findById(ID)
                .orElseGet(ConfiguracionCredencial::new);
        entidad.setId(ID);
        try {
            entidad.setDisenoJson(objectMapper.writeValueAsString(diseno));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar el diseño de la credencial", e);
        }
        repository.save(entidad);
        return new DisenoCredencial.Editor(diseno, DisenoCredencial.catalogo());
    }

    @Transactional
    public DisenoCredencial.Editor restablecer() {
        repository.deleteById(ID);
        return new DisenoCredencial.Editor(
                DisenoCredencial.porDefecto(), DisenoCredencial.catalogo());
    }

    private DisenoCredencial leer(ConfiguracionCredencial entidad) {
        try {
            DisenoCredencial diseno = objectMapper.readValue(
                    entidad.getDisenoJson(), DisenoCredencial.class);
            validar(diseno);
            return diseno;
        } catch (Exception e) {
            log.error("La configuración de credencial guardada no es válida; se usa la predeterminada", e);
            return DisenoCredencial.porDefecto();
        }
    }

    private void validar(DisenoCredencial diseno) {
        if (diseno == null || diseno.elementos() == null) {
            throw new ReglaNegocioException("El diseño de la credencial está vacío");
        }
        if (Math.abs(diseno.ancho() - 242.65f) > 0.1f
                || Math.abs(diseno.alto() - 153.01f) > 0.1f) {
            throw new ReglaNegocioException("La credencial debe conservar el tamaño CR80");
        }
        if (diseno.elementos().size() > MAXIMO_ELEMENTOS) {
            throw new ReglaNegocioException("El diseño admite hasta " + MAXIMO_ELEMENTOS + " elementos");
        }

        Set<String> campos = new HashSet<>();
        DisenoCredencial.catalogo().forEach(c -> campos.add(c.campo()));
        Set<String> ids = new HashSet<>();
        for (DisenoCredencial.Elemento e : diseno.elementos()) {
            if (e == null || e.id() == null || e.id().isBlank() || !ids.add(e.id())) {
                throw new ReglaNegocioException("Cada elemento debe tener un identificador único");
            }
            if (e.cara() == null || e.tipo() == null || e.alineacion() == null
                    || !campos.contains(e.campo())) {
                throw new ReglaNegocioException("El elemento " + e.id() + " tiene un campo no válido");
            }
            if (e.x() < 0 || e.y() < 0 || e.ancho() < 2 || e.alto() < 2
                    || e.x() + e.ancho() > diseno.ancho() + 0.05f
                    || e.y() + e.alto() > diseno.alto() + 0.05f) {
                throw new ReglaNegocioException(
                        "El elemento " + e.etiqueta() + " sale de los límites de la tarjeta");
            }
            if (e.tamanoFuente() < 3f || e.tamanoFuente() > 40f) {
                throw new ReglaNegocioException("El tamaño de letra debe estar entre 3 y 40 puntos");
            }
            if (e.texto() != null && e.texto().length() > 300) {
                throw new ReglaNegocioException("El texto libre admite hasta 300 caracteres");
            }
        }
    }
}
