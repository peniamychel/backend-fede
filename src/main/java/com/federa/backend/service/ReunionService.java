package com.federa.backend.service;

import com.federa.backend.dto.ConvocadoResponse;
import com.federa.backend.dto.RegistroAsistenciaResponse;
import com.federa.backend.dto.ReunionRequest;
import com.federa.backend.dto.ReunionResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Asistencia;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Reunion;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoReunion;
import com.federa.backend.repository.AsistenciaRepository;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.ReunionRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reuniones y pase de lista.
 * <p>
 * El corazón de esta clase es {@link #convocados}: cada tipo de reunión llama a
 * gente distinta, y de esa lista depende todo lo demás. Pasar lista es
 * contrastar un código contra ella.
 * <ul>
 *   <li><b>Sindicato</b>: sus propios productores.</li>
 *   <li><b>Ampliado</b>: los productores de todos los sindicatos de la
 *       central.</li>
 *   <li><b>Dirigentes de la central</b>: presidentes y secretarios de sus
 *       sindicatos.</li>
 *   <li><b>Dirigentes de la federación</b>: los de las centrales y los de los
 *       sindicatos.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ReunionService {

    private static final Collator ALFABETO = Collator.getInstance(Locale.forLanguageTag("es"));

    /** Los dos cargos que se convocan a las reuniones de dirigentes. */
    private static final List<TipoCargo> DIRIGENCIA =
            List.of(TipoCargo.PRESIDENTE, TipoCargo.SECRETARIO);

    private final ReunionRepository reunionRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final ProductorRepository productorRepository;
    private final CargoRepository cargoRepository;
    private final SindicatoService sindicatoService;
    private final CentralService centralService;
    private final FederacionService federacionService;

    public ReunionService(ReunionRepository reunionRepository,
                          AsistenciaRepository asistenciaRepository,
                          ProductorRepository productorRepository,
                          CargoRepository cargoRepository,
                          SindicatoService sindicatoService,
                          CentralService centralService,
                          FederacionService federacionService) {
        this.reunionRepository = reunionRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.productorRepository = productorRepository;
        this.cargoRepository = cargoRepository;
        this.sindicatoService = sindicatoService;
        this.centralService = centralService;
        this.federacionService = federacionService;
    }

    // ------------------------------------------------------------ reuniones

    public List<ReunionResponse> listar(Long sindicatoId, Long centralId, Long federacionId) {
        return reunionRepository.filtrar(sindicatoId, centralId, federacionId).stream()
                .map(this::conRecuento)
                .toList();
    }

    public ReunionResponse obtener(Long id) {
        return conRecuento(buscar(id));
    }

    @Transactional
    public ReunionResponse crear(ReunionRequest peticion) {
        Reunion reunion = new Reunion();
        reunion.setTipo(peticion.tipo());
        aplicar(reunion, peticion);
        colgar(reunion, peticion.tipo(), peticion.convocanteId());
        return conRecuento(reunionRepository.save(reunion));
    }

    /**
     * Cambia los datos de la reunión, pero no su tipo ni quién convoca.
     * <p>
     * Esos dos definen la lista de convocados, y cambiarlos con asistencias ya
     * tomadas dejaría presentes a gente que la nueva convocatoria no llama.
     * Para eso se crea otra reunión.
     */
    @Transactional
    public ReunionResponse actualizar(Long id, ReunionRequest peticion) {
        Reunion reunion = buscar(id);
        if (peticion.tipo() != reunion.getTipo()
                || !peticion.convocanteId().equals(reunion.getConvocanteId())) {
            throw new ReglaNegocioException(
                    "No se puede cambiar el tipo de reunión ni quién convoca: eso cambiaría "
                    + "la lista de convocados y ya hay asistencias tomadas contra la actual. "
                    + "Creá otra reunión.");
        }
        aplicar(reunion, peticion);
        reunionRepository.flush();
        return conRecuento(reunion);
    }

    /** Cierra o reabre la lista. */
    @Transactional
    public ReunionResponse cambiarCierre(Long id, boolean cerrada) {
        Reunion reunion = buscar(id);
        reunion.setCerrada(cerrada);
        reunionRepository.flush();
        return conRecuento(reunion);
    }

    @Transactional
    public void eliminar(Long id) {
        Reunion reunion = buscar(id);
        long presentes = asistenciaRepository.countByReunionId(id);
        if (presentes > 0) {
            throw new ReglaNegocioException("La reunión «" + reunion.getTitulo() + "» ya tiene "
                    + presentes + " asistencia(s) registrada(s). Borrarla perdería la lista; "
                    + "si no va más, deshabilitala.");
        }
        reunionRepository.delete(reunion);
    }

    // ------------------------------------------------------------- la lista

    /**
     * Quiénes deberían estar, y quiénes ya están.
     * <p>
     * Sale ordenada por apellido para poder buscar a alguien a ojo cuando el
     * carnet no aparece.
     */
    public List<ConvocadoResponse> lista(Long reunionId) {
        Reunion reunion = buscar(reunionId);
        Set<Long> presentes = new HashSet<>(
                asistenciaRepository.findProductoresPresentes(reunionId));
        Map<Long, java.time.LocalDateTime> momentos = new LinkedHashMap<>();
        for (Asistencia a : asistenciaRepository.findByReunionIdOrderByRegistradaEnAsc(reunionId)) {
            momentos.put(a.getProductor().getId(), a.getRegistradaEn());
        }

        return convocados(reunion).stream()
                .map(c -> new ConvocadoResponse(
                        c.productor().getId(),
                        c.productor().getNombreCompleto(),
                        Textos.limpiar(c.productor().getCi()),
                        c.productor().getSindicato().getNombre(),
                        c.motivo(),
                        presentes.contains(c.productor().getId()),
                        momentos.get(c.productor().getId())))
                .toList();
    }

    /**
     * Registra a alguien por el código de su credencial.
     * <p>
     * Es la operación que se usa con el teléfono en la mano frente a una fila
     * de gente, así que los errores tienen que decir exactamente qué pasa:
     * código inexistente, persona no convocada, o lista cerrada.
     */
    @Transactional
    public RegistroAsistenciaResponse registrarPorCodigo(Long reunionId, String codigo) {
        Reunion reunion = buscar(reunionId);
        if (reunion.isCerrada()) {
            throw new ReglaNegocioException("La lista de «" + reunion.getTitulo()
                    + "» está cerrada. Reabrila si todavía hay que registrar gente.");
        }

        String limpio = Textos.limpiar(codigo);
        if (limpio == null) {
            throw new ReglaNegocioException("No llegó ningún código.");
        }
        Productor productor = productorRepository.findByCodigo(limpio.toUpperCase())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Ninguna credencial tiene el código " + limpio + "."));

        List<Convocado> lista = convocados(reunion);
        Convocado convocado = lista.stream()
                .filter(c -> c.productor().getId().equals(productor.getId()))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException(String.format(
                        "%s no está convocado a esta reunión. %s",
                        productor.getNombreCompleto(), reunion.getTipo().getDetalle())));

        Optional<Asistencia> yaEstaba = asistenciaRepository
                .findByReunionIdAndProductorId(reunionId, productor.getId());
        if (yaEstaba.isPresent()) {
            // No es un error: quien pasa lista escanea de nuevo por las dudas,
            // y necesita que se lo confirmen, no que se lo reproche.
            return respuesta(RegistroAsistenciaResponse.Resultado.REPETIDO,
                    productor.getNombreCompleto() + " ya estaba registrado.",
                    convocado, yaEstaba.get().getRegistradaEn(), reunionId, lista.size());
        }

        Asistencia asistencia = new Asistencia(reunion, productor);
        asistenciaRepository.saveAndFlush(asistencia);

        return respuesta(RegistroAsistenciaResponse.Resultado.REGISTRADO,
                productor.getNombreCompleto() + " quedó registrado.",
                convocado, asistencia.getRegistradaEn(), reunionId, lista.size());
    }

    /** Da de baja una asistencia, para cuando se escaneó al que no era. */
    @Transactional
    public void quitarAsistencia(Long reunionId, Long productorId) {
        Asistencia asistencia = asistenciaRepository
                .findByReunionIdAndProductorId(reunionId, productorId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Esa persona no figura como presente en la reunión."));
        asistenciaRepository.delete(asistencia);
    }

    // ------------------------------------------------- quiénes son convocados

    /** Un convocado y por qué lo está. */
    private record Convocado(Productor productor, String motivo) {
    }

    /**
     * La lista de convocados según el tipo. Es la regla central de todo esto.
     */
    private List<Convocado> convocados(Reunion reunion) {
        Long id = reunion.getConvocanteId();

        return switch (reunion.getTipo()) {
            case SINDICATO -> deProductores(productorRepository
                    .findBySindicatoIdOrderByApellidosAscNombresAsc(id));

            case AMPLIADO -> deProductores(productorRepository
                    .findBySindicatoCentralIdOrderByApellidosAscNombresAsc(id));

            case DIRIGENTES_CENTRAL -> deCargos(
                    cargoRepository.findDirigentesDeSindicatosDeCentral(id, DIRIGENCIA));

            case DIRIGENTES_FEDERACION -> deCargos(
                    cargoRepository.findDirigentesDeFederacion(id, DIRIGENCIA));
        };
    }

    /**
     * Convocados por ser afiliados. Se dejan fuera los deshabilitados: alguien
     * dado de baja no cuenta para el quórum.
     */
    private List<Convocado> deProductores(List<Productor> productores) {
        return productores.stream()
                .filter(Productor::isEstado)
                .map(p -> new Convocado(p, null))
                .toList();
    }

    /**
     * Convocados por ocupar un cargo, con el cargo como motivo.
     * <p>
     * Se ordena por apellido acá y no en la consulta porque la consulta ordena
     * por sindicato y cargo, que es útil para leer el directorio pero no para
     * buscar a una persona en una lista de asistencia.
     */
    private List<Convocado> deCargos(List<Cargo> cargos) {
        List<Convocado> lista = new ArrayList<>();
        for (Cargo c : cargos) {
            Productor p = c.getProductor();
            if (!p.isEstado()) {
                continue;
            }
            lista.add(new Convocado(p, c.getCargo().getEtiqueta() + " de "
                    + c.getDuenoNombre()));
        }
        lista.sort((a, b) -> ALFABETO.compare(
                a.productor().getNombreCompleto(), b.productor().getNombreCompleto()));
        return lista;
    }

    // ----------------------------------------------------------- auxiliares

    private void aplicar(Reunion reunion, ReunionRequest peticion) {
        reunion.setTitulo(Textos.limpiar(peticion.titulo()));
        reunion.setFecha(peticion.fecha());
        reunion.setLugar(Textos.limpiar(peticion.lugar()));
        reunion.setObservaciones(Textos.limpiar(peticion.observaciones()));
    }

    /**
     * Cuelga la reunión del nivel que corresponde a su tipo.
     * <p>
     * El nivel no se elige: lo impone el tipo. Un ampliado es de una central y
     * nada más, así que el id que llega se interpreta según eso y se verifica
     * que exista.
     */
    private void colgar(Reunion reunion, TipoReunion tipo, Long convocanteId) {
        Ambito ambito = tipo.getConvoca();
        switch (ambito) {
            case SINDICATO -> reunion.colgarDe(sindicatoService.buscar(convocanteId), null, null);
            case CENTRAL -> reunion.colgarDe(null, centralService.buscar(convocanteId), null);
            case FEDERACION ->
                    reunion.colgarDe(null, null, federacionService.buscar(convocanteId));
        }
    }

    private ReunionResponse conRecuento(Reunion reunion) {
        return ReunionResponse.desde(reunion,
                convocados(reunion).size(),
                (int) asistenciaRepository.countByReunionId(reunion.getId()));
    }

    private RegistroAsistenciaResponse respuesta(
            RegistroAsistenciaResponse.Resultado resultado, String mensaje,
            Convocado convocado, java.time.LocalDateTime momento, Long reunionId,
            int convocados) {
        Productor p = convocado.productor();
        return new RegistroAsistenciaResponse(
                resultado,
                mensaje,
                new ConvocadoResponse(p.getId(), p.getNombreCompleto(),
                        Textos.limpiar(p.getCi()), p.getSindicato().getNombre(),
                        convocado.motivo(), true, momento),
                (int) asistenciaRepository.countByReunionId(reunionId),
                convocados);
    }

    Reunion buscar(Long id) {
        return reunionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("reunión", id));
    }
}
