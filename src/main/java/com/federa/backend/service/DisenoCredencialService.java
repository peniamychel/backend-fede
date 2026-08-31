package com.federa.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.DisenoCredencial;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.ConfiguracionCredencial;
import com.federa.backend.repository.ConfiguracionCredencialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DisenoCredencialService {

    private static final Logger log = LoggerFactory.getLogger(DisenoCredencialService.class);
    private static final long ID = 1L;
    private static final int MAXIMO_ELEMENTOS = 60;
    private static final Pattern RECURSO_IMAGEN = Pattern.compile(
            "configuracion/credencial/objetos/[a-f0-9-]+\\.png");
    // Se conservan las claves históricas para no perder plantillas existentes.
    // La extensión es solo parte de la clave interna: las cargas nuevas se
    // guardan como PNG y el controlador declara el MIME leyendo el contenido.
    static final String PLANTILLA_CARA = "configuracion/credencial/plantilla-cara.jpg";
    static final String PLANTILLA_REVERSO = "configuracion/credencial/plantilla-reverso.jpg";

    private final ConfiguracionCredencialRepository repository;
    private final ObjectMapper objectMapper;
    private final AlmacenObjetos almacen;
    private final ProcesadorImagenes procesador;

    public DisenoCredencialService(ConfiguracionCredencialRepository repository,
                                   ObjectMapper objectMapper,
                                   AlmacenObjetos almacen,
                                   ProcesadorImagenes procesador) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.almacen = almacen;
        this.procesador = procesador;
    }

    @Transactional(readOnly = true)
    public DisenoCredencial actual() {
        return repository.findById(ID).map(this::leer).orElseGet(DisenoCredencial::porDefecto);
    }

    @Transactional(readOnly = true)
    public DisenoCredencial.Editor editor() {
        return respuesta(actual());
    }

    @Transactional
    public DisenoCredencial.Editor guardar(DisenoCredencial diseno) {
        diseno = normalizar(diseno);
        validar(diseno);
        Set<String> recursosAnteriores = recursosPersonalizados(actual());
        ConfiguracionCredencial entidad = repository.findById(ID)
                .orElseGet(ConfiguracionCredencial::new);
        entidad.setId(ID);
        try {
            entidad.setDisenoJson(objectMapper.writeValueAsString(diseno));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar el diseño de la credencial", e);
        }
        repository.save(entidad);
        Set<String> recursosVigentes = recursosPersonalizados(diseno);
        recursosAnteriores.stream()
                .filter(recurso -> !recursosVigentes.contains(recurso))
                .forEach(almacen::borrar);
        return respuesta(diseno);
    }

    @Transactional
    public DisenoCredencial.Editor restablecer() {
        recursosPersonalizados(actual()).forEach(almacen::borrar);
        repository.deleteById(ID);
        almacen.borrar(PLANTILLA_CARA);
        almacen.borrar(PLANTILLA_REVERSO);
        return respuesta(DisenoCredencial.porDefecto());
    }

    @Transactional
    public DisenoCredencial.Editor guardarPlantilla(DisenoCredencial.Cara cara, byte[] contenido) {
        // La plantilla puede tener huecos transparentes —por ejemplo donde va
        // la foto—, por eso nunca se convierte a JPEG.
        var preparada = procesador.prepararPng(contenido, 1800, 1024 * 1024);
        almacen.guardar(clavePlantilla(cara), preparada.contenido());
        return respuesta(actual());
    }

    public record ImagenPersonalizada(String clave, String url) {
    }

    /** Guarda una imagen independiente que después formará parte del orden de capas. */
    @Transactional
    public ImagenPersonalizada guardarImagen(byte[] contenido) {
        var preparada = procesador.prepararPng(contenido, 1800, 1024 * 1024);
        String clave = "configuracion/credencial/objetos/" + UUID.randomUUID() + ".png";
        almacen.guardar(clave, preparada.contenido());
        return new ImagenPersonalizada(clave, almacen.urlPublica(clave));
    }

    public record PlantillaArchivo(byte[] contenido, String tipoMime) {
    }

    @Transactional(readOnly = true)
    public PlantillaArchivo plantilla(DisenoCredencial.Cara cara) {
        String clave = clavePlantilla(cara);
        if (!almacen.existe(clave)) {
            throw new com.federa.backend.exception.RecursoNoEncontradoException(
                    "No hay una plantilla personalizada para " + cara.name().toLowerCase(Locale.ROOT));
        }
        byte[] contenido = almacen.leer(clave);
        return new PlantillaArchivo(contenido, esPng(contenido)
                ? "image/png" : "image/jpeg");
    }

    @Transactional(readOnly = true)
    public DisenoCredencial.Editor restablecerPlantilla(DisenoCredencial.Cara cara) {
        almacen.borrar(clavePlantilla(cara));
        return respuesta(actual());
    }

    private DisenoCredencial.Editor respuesta(DisenoCredencial diseno) {
        return new DisenoCredencial.Editor(
                diseno,
                DisenoCredencial.catalogo(),
                urlPlantilla(DisenoCredencial.Cara.CARA),
                urlPlantilla(DisenoCredencial.Cara.REVERSO));
    }

    private String urlPlantilla(DisenoCredencial.Cara cara) {
        String clave = clavePlantilla(cara);
        return almacen.existe(clave)
                ? ApiRutas.V1 + "/configuracion/credencial/plantilla/" + cara.name()
                    + "?v=" + System.currentTimeMillis()
                : null;
    }

    static String clavePlantilla(DisenoCredencial.Cara cara) {
        return cara == DisenoCredencial.Cara.CARA ? PLANTILLA_CARA : PLANTILLA_REVERSO;
    }

    private boolean esPng(byte[] contenido) {
        return contenido != null && contenido.length >= 8
                && (contenido[0] & 0xff) == 0x89
                && contenido[1] == 0x50
                && contenido[2] == 0x4e
                && contenido[3] == 0x47
                && contenido[4] == 0x0d
                && contenido[5] == 0x0a
                && contenido[6] == 0x1a
                && contenido[7] == 0x0a;
    }

    private DisenoCredencial leer(ConfiguracionCredencial entidad) {
        try {
            DisenoCredencial diseno = normalizar(objectMapper.readValue(
                    entidad.getDisenoJson(), DisenoCredencial.class));
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
        campos.add(DisenoCredencial.CAMPO_PLANTILLA);
        campos.add(DisenoCredencial.CAMPO_IMAGEN_PERSONALIZADA);
        Set<String> ids = new HashSet<>();
        Set<DisenoCredencial.Cara> plantillas = new HashSet<>();
        for (DisenoCredencial.Elemento e : diseno.elementos()) {
            if (e == null || e.id() == null || e.id().isBlank() || !ids.add(e.id())) {
                throw new ReglaNegocioException("Cada elemento debe tener un identificador único");
            }
            if (e.cara() == null || e.tipo() == null || e.alineacion() == null
                    || !campos.contains(e.campo())) {
                throw new ReglaNegocioException("El elemento " + e.id() + " tiene un campo no válido");
            }
            if (DisenoCredencial.CAMPO_PLANTILLA.equals(e.campo())) {
                if (e.tipo() != DisenoCredencial.Tipo.PLANTILLA
                        || !plantillas.add(e.cara())) {
                    throw new ReglaNegocioException(
                            "Debe existir una sola plantilla por cada cara");
                }
            } else if (e.tipo() == DisenoCredencial.Tipo.PLANTILLA) {
                throw new ReglaNegocioException("Una plantilla tiene un campo no válido");
            }
            if (DisenoCredencial.CAMPO_IMAGEN_PERSONALIZADA.equals(e.campo())) {
                if (e.tipo() != DisenoCredencial.Tipo.IMAGEN
                        || e.recurso() == null
                        || !RECURSO_IMAGEN.matcher(e.recurso()).matches()
                        || !almacen.existe(e.recurso())) {
                    throw new ReglaNegocioException(
                            "La imagen personalizada no existe o no es válida");
                }
            } else if (e.recurso() != null && !e.recurso().isBlank()) {
                throw new ReglaNegocioException(
                        "Solo las imágenes personalizadas pueden tener un recurso asociado");
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
        if (plantillas.size() != DisenoCredencial.Cara.values().length) {
            throw new ReglaNegocioException("El diseño debe conservar la plantilla de ambas caras");
        }
    }

    /**
     * Incorpora las capas y valores que no existían en diseños guardados por
     * versiones anteriores.
     */
    private DisenoCredencial normalizar(DisenoCredencial diseno) {
        if (diseno == null || diseno.elementos() == null) return diseno;
        Set<DisenoCredencial.Cara> presentes = new HashSet<>();
        diseno.elementos().stream()
                .filter(e -> e != null
                        && e.tipo() == DisenoCredencial.Tipo.PLANTILLA
                        && DisenoCredencial.CAMPO_PLANTILLA.equals(e.campo()))
                .forEach(e -> presentes.add(e.cara()));
        boolean fuentesCompletas = diseno.elementos().stream()
                .allMatch(e -> e == null || e.fuente() != null);
        if (presentes.size() == DisenoCredencial.Cara.values().length
                && fuentesCompletas) {
            return diseno;
        }

        List<DisenoCredencial.Elemento> elementos = new ArrayList<>();
        for (DisenoCredencial.Cara cara : DisenoCredencial.Cara.values()) {
            if (!presentes.contains(cara)) elementos.add(elementoPlantilla(cara));
        }
        diseno.elementos().stream()
                .map(this::normalizarFuente)
                .forEach(elementos::add);
        return new DisenoCredencial(diseno.ancho(), diseno.alto(), List.copyOf(elementos));
    }

    private DisenoCredencial.Elemento normalizarFuente(DisenoCredencial.Elemento e) {
        if (e == null || e.fuente() != null) return e;
        return new DisenoCredencial.Elemento(
                e.id(), e.cara(), e.tipo(), e.campo(), e.etiqueta(),
                e.x(), e.y(), e.ancho(), e.alto(), e.tamanoFuente(), e.negrita(),
                e.alineacion(), e.color(), e.texto(), DisenoCredencial.Fuente.ROBOTO,
                e.recurso());
    }

    private DisenoCredencial.Elemento elementoPlantilla(DisenoCredencial.Cara cara) {
        return new DisenoCredencial.Elemento(
                "plantilla-" + cara.name().toLowerCase(Locale.ROOT), cara,
                DisenoCredencial.Tipo.PLANTILLA, DisenoCredencial.CAMPO_PLANTILLA,
                "Plantilla", 0f, 0f, 242.65f, 153.01f, 5.5f, false,
                DisenoCredencial.Alineacion.CENTRO, "#000000", "",
                DisenoCredencial.Fuente.ROBOTO, null);
    }

    private Set<String> recursosPersonalizados(DisenoCredencial diseno) {
        Set<String> recursos = new HashSet<>();
        if (diseno == null || diseno.elementos() == null) return recursos;
        diseno.elementos().stream()
                .filter(e -> e != null
                        && DisenoCredencial.CAMPO_IMAGEN_PERSONALIZADA.equals(e.campo())
                        && e.recurso() != null)
                .map(DisenoCredencial.Elemento::recurso)
                .forEach(recursos::add);
        return recursos;
    }
}
