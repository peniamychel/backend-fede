package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.almacen.TransaccionArchivos;
import com.federa.backend.dto.ConvocadoResponse;
import com.federa.backend.dto.RegistroAsistenciaResponse;
import com.federa.backend.dto.ReunionRequest;
import com.federa.backend.dto.ReunionResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Asistencia;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.LlamadaLista;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Reunion;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoReunion;
import com.federa.backend.repository.AsistenciaRepository;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.LlamadaListaRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.ReunionRepository;
import com.federa.backend.repository.VetoRepository;
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
 *   <li><b>Dirigentes de la central</b>: secretarios generales y de relaciones de sus
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
            List.of(TipoCargo.SECRETARIO_GENERAL, TipoCargo.SECRETARIO_RELACIONES);

    private final ReunionRepository reunionRepository;
    private final VetoRepository vetoRepository;
    private final AlmacenObjetos almacen;
    private final AsistenciaRepository asistenciaRepository;
    private final LlamadaListaRepository llamadaRepository;
    private final ProductorRepository productorRepository;
    private final CargoRepository cargoRepository;
    private final SindicatoService sindicatoService;
    private final CentralService centralService;
    private final FederacionService federacionService;

    public ReunionService(ReunionRepository reunionRepository,
                          VetoRepository vetoRepository,
                          AlmacenObjetos almacen,
                          AsistenciaRepository asistenciaRepository,
                          LlamadaListaRepository llamadaRepository,
                          ProductorRepository productorRepository,
                          CargoRepository cargoRepository,
                          SindicatoService sindicatoService,
                          CentralService centralService,
                          FederacionService federacionService) {
        this.reunionRepository = reunionRepository;
        this.vetoRepository = vetoRepository;
        this.almacen = almacen;
        this.asistenciaRepository = asistenciaRepository;
        this.llamadaRepository = llamadaRepository;
        this.productorRepository = productorRepository;
        this.cargoRepository = cargoRepository;
        this.sindicatoService = sindicatoService;
        this.centralService = centralService;
        this.federacionService = federacionService;
    }

    // ------------------------------------------------------------ reuniones

    public List<ReunionResponse> listar(Long sindicatoId, Long centralId, Long federacionId,
                                        TipoReunion tipo, String texto) {
        return reunionRepository
                .filtrar(sindicatoId, centralId, federacionId, tipo, Textos.limpiar(texto))
                .stream()
                .map(this::conRecuento)
                .toList();
    }

    /**
     * Cuántas reuniones hay de cada tipo, con el mismo filtro pero sin el tipo.
     * <p>
     * Es lo que va en las solapas. El tipo no entra en la cuenta a propósito:
     * la solapa de «Ampliado» tiene que decir cuántos ampliados hay aunque en
     * ese momento se esté mirando otra solapa, o el número cambiaría al tocarla
     * y no serviría para decidir adónde ir.
     * <p>
     * Los tipos sin ninguna reunión salen en cero y no ausentes: una solapa que
     * aparece y desaparece es más difícil de encontrar que una que dice cero.
     */
    public Map<TipoReunion, Long> contarPorTipo(Long sindicatoId, Long centralId,
                                                Long federacionId, String texto) {
        Map<TipoReunion, Long> cuenta = new LinkedHashMap<>();
        for (TipoReunion t : TipoReunion.values()) {
            cuenta.put(t, 0L);
        }
        for (TipoReunion t : reunionRepository.tiposQueCoinciden(
                sindicatoId, centralId, federacionId, Textos.limpiar(texto))) {
            cuenta.merge(t, 1L, Long::sum);
        }
        return cuenta;
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
        // Apagar los vetos con alguno ya decidido dejaría esa decisión colgando
        // de una asamblea que, según el sistema, no podía tomarla. Cuenta las
        // dos direcciones: haber levantado un veto también es haber decidido.
        if (reunion.isVetosHabilitados() && !Boolean.TRUE.equals(peticion.vetosHabilitados())) {
            long decididos = vetoRepository.decididosEn(id).size();
            if (decididos > 0) {
                throw new ReglaNegocioException(String.format(
                        "En «%s» se decidieron %d veto(s). Apagar los vetos los dejaría sin la "
                        + "asamblea que los respalda; para eso hay que levantarlos, en otra "
                        + "reunión.", reunion.getTitulo(), decididos));
            }
        }

        aplicar(reunion, peticion);
        reunionRepository.flush();
        return conRecuento(reunion);
    }

    /**
     * Cierra o reabre el pase de lista de la reunión.
     * <p>
     * Al cerrarlo se cierra también la vuelta que haya quedado abierta: si no,
     * la reunión diría que ya no se pasa lista mientras una llamada sigue
     * admitiendo registros.
     */
    @Transactional
    public ReunionResponse cambiarCierre(Long id, boolean cerrada) {
        Reunion reunion = buscar(id);
        reunion.setCerrada(cerrada);
        if (cerrada) {
            llamadaRepository.findByReunionIdAndAbiertaIsTrue(id)
                    .ifPresent(abierta -> abierta.cerrar(java.time.LocalDateTime.now()));
        }
        reunionRepository.flush();
        return conRecuento(reunion);
    }

    // ------------------------------------------------- las vueltas de lista

    /**
     * Abre una vuelta de lista.
     * <p>
     * En una asamblea se llama lista más de una vez: al empezar, más tarde para
     * los que llegaron tarde, y a veces al final. Cada vuelta tiene su propia
     * lista de presentes.
     */
    @Transactional
    public LlamadaLista abrirLlamada(Long reunionId, String nota) {
        Reunion reunion = buscar(reunionId);
        if (reunion.isCerrada()) {
            throw new ReglaNegocioException("La lista de «" + reunion.getTitulo()
                    + "» está cerrada: ya no se pueden abrir más llamadas.");
        }
        llamadaRepository.findByReunionIdAndAbiertaIsTrue(reunionId).ifPresent(abierta -> {
            throw new ReglaNegocioException("La " + ordinal(abierta.getNumero())
                    + " llamada sigue abierta. Cerrala antes de abrir otra.");
        });

        LlamadaLista llamada = new LlamadaLista();
        llamada.setReunion(reunion);
        llamada.setNumero((int) llamadaRepository.countByReunionId(reunionId) + 1);
        llamada.setNota(Textos.limpiar(nota));
        llamada.setAbierta(true);
        return llamadaRepository.saveAndFlush(llamada);
    }

    /** Cierra la vuelta: lo que se registre de acá en más va a la siguiente. */
    @Transactional
    public LlamadaLista cerrarLlamada(Long llamadaId) {
        LlamadaLista llamada = buscarLlamada(llamadaId);
        if (!llamada.isAbierta()) {
            throw new ReglaNegocioException(
                    "La " + ordinal(llamada.getNumero()) + " llamada ya estaba cerrada.");
        }
        llamada.cerrar(java.time.LocalDateTime.now());
        return llamadaRepository.saveAndFlush(llamada);
    }

    public List<LlamadaLista> llamadasDe(Long reunionId) {
        return llamadaRepository.findByReunionIdOrderByNumeroAsc(reunionId);
    }

    public long presentesEn(Long llamadaId) {
        return asistenciaRepository.countByLlamadaId(llamadaId);
    }

    LlamadaLista buscarLlamada(Long id) {
        return llamadaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("llamada de lista", id));
    }

    /** "primera", "segunda"… para que los mensajes se lean como se habla. */
    static String ordinal(int numero) {
        return switch (numero) {
            case 1 -> "primera";
            case 2 -> "segunda";
            case 3 -> "tercera";
            case 4 -> "cuarta";
            case 5 -> "quinta";
            default -> numero + "ª";
        };
    }

    @Transactional
    public void eliminar(Long id) {
        Reunion reunion = buscar(id);
        long presentes = asistenciaRepository.countByLlamadaReunionId(id);
        if (presentes > 0) {
            throw new ReglaNegocioException("La reunión «" + reunion.getTitulo() + "» ya tiene "
                    + presentes + " asistencia(s) registrada(s). Borrarla perdería la lista; "
                    + "si no va más, deshabilitala.");
        }
        // Una reunión que decidió un veto no se borra: el veto quedaría sin la
        // asamblea que lo respalda, que es justamente lo que le da validez.
        long decisiones = vetoRepository.findByReunionIdOrderByDesdeDesc(id).size()
                + vetoRepository.findByReunionLevantaId(id).size();
        if (decisiones > 0) {
            throw new ReglaNegocioException("En la reunión «" + reunion.getTitulo() + "» se "
                    + "decidieron " + decisiones + " veto(s). Borrarla dejaría esas decisiones "
                    + "sin la asamblea que las respalda.");
        }

        // La fila se va con el delete, pero el archivo del acta no: el disco no
        // sabe nada de JPA. Hay que leer su clave antes y quitarlo después de
        // confirmar, o queda ocupando espacio para siempre sin que nadie sepa
        // de qué reunión era.
        List<String> actas = reunion.getHojasActa().stream()
                .map(com.federa.backend.model.HojaActa::getClave).toList();
        reunionRepository.delete(reunion);
        if (!actas.isEmpty()) {
            TransaccionArchivos.alConfirmar(() -> actas.forEach(almacen::borrar));
        }
    }

    // ------------------------------------------------------------- la lista

    /**
     * Quiénes deberían estar, y quiénes ya están.
     * <p>
     * Sale ordenada por apellido para poder buscar a alguien a ojo cuando el
     * carnet no aparece.
     */
    public List<ConvocadoResponse> lista(Long llamadaId) {
        LlamadaLista llamada = buscarLlamada(llamadaId);
        Reunion reunion = llamada.getReunion();
        Set<Long> presentes = new HashSet<>(
                asistenciaRepository.findProductoresPresentes(llamadaId));
        Map<Long, java.time.LocalDateTime> momentos = new LinkedHashMap<>();
        for (Asistencia a : asistenciaRepository.findByLlamadaIdOrderByRegistradaEnAsc(llamadaId)) {
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
    public RegistroAsistenciaResponse registrarPorCodigo(Long llamadaId, String codigo) {
        LlamadaLista llamada = buscarLlamada(llamadaId);
        Reunion reunion = llamada.getReunion();
        Long reunionId = reunion.getId();
        if (!llamada.isAbierta()) {
            throw new ReglaNegocioException("La " + ordinal(llamada.getNumero())
                    + " llamada ya se cerró. Abrí otra si todavía hay que registrar gente.");
        }

        String limpio = Textos.limpiar(codigo);
        if (limpio == null) {
            throw new ReglaNegocioException("No llegó ningún código.");
        }
        Productor productor = productorRepository.findByCodigo(limpio.toUpperCase())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Ninguna credencial tiene el código " + limpio + "."));

        // Quien está observado por la asamblea no participa: no cuenta para el
        // quórum ni figura en el acta como presente. Se comprueba antes que la
        // convocatoria porque es lo más importante que hay para decir de esa
        // persona, y quien está pasando lista tiene que oírlo primero.
        vetoRepository.findByProductorIdAndVigenteIsTrue(productor.getId())
                .ifPresent(veto -> {
                    throw new ReglaNegocioException(String.format(
                            "%s está observado por la asamblea desde el %s (%s). Mientras el "
                            + "veto siga, no participa de las reuniones ni cuenta para el "
                            + "quórum.",
                            productor.getNombreCompleto(), veto.getDesde(), veto.getMotivo()));
                });

        List<Convocado> lista = convocados(reunion);
        Convocado convocado = lista.stream()
                .filter(c -> c.productor().getId().equals(productor.getId()))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException(String.format(
                        "%s no está convocado a esta reunión. %s",
                        productor.getNombreCompleto(), reunion.getTipo().getDetalle())));

        Optional<Asistencia> yaEstaba = asistenciaRepository
                .findByLlamadaIdAndProductorId(llamadaId, productor.getId());
        if (yaEstaba.isPresent()) {
            // No es un error: quien pasa lista escanea de nuevo por las dudas,
            // y necesita que se lo confirmen, no que se lo reproche.
            return respuesta(RegistroAsistenciaResponse.Resultado.REPETIDO,
                    productor.getNombreCompleto() + " ya estaba en esta llamada.",
                    convocado, yaEstaba.get().getRegistradaEn(), reunionId, lista.size());
        }

        Asistencia asistencia = new Asistencia(llamada, productor);
        asistenciaRepository.saveAndFlush(asistencia);

        return respuesta(RegistroAsistenciaResponse.Resultado.REGISTRADO,
                productor.getNombreCompleto() + " quedó registrado.",
                convocado, asistencia.getRegistradaEn(), reunionId, lista.size());
    }

    /** Da de baja una asistencia, para cuando se escaneó al que no era. */
    @Transactional
    public void quitarAsistencia(Long llamadaId, Long productorId) {
        Asistencia asistencia = asistenciaRepository
                .findByLlamadaIdAndProductorId(llamadaId, productorId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Esa persona no figura como presente en esta vuelta de lista."));
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
        Set<Long> observados = observados();

        return switch (reunion.getTipo()) {
            case SINDICATO -> deProductores(productorRepository
                    .findBySindicatoIdOrderByApellidosAscNombresAsc(id), observados);

            case AMPLIADO -> deProductores(productorRepository
                    .findBySindicatoCentralIdOrderByApellidosAscNombresAsc(id), observados);

            case DIRIGENTES_CENTRAL -> deCargos(
                    cargoRepository.findDirigentesDeSindicatosDeCentral(id, DIRIGENCIA),
                    observados);

            case DIRIGENTES_FEDERACION -> deCargos(
                    cargoRepository.findDirigentesDeFederacion(id, DIRIGENCIA), observados);
        };
    }

    /**
     * Quiénes están vetados hoy, en todo el padrón.
     * <p>
     * Se traen todos de una vez y no de a uno por convocado: en un ampliado de
     * central serían cientos de consultas, y los vetados vigentes son un puñado.
     */
    private Set<Long> observados() {
        return vetoRepository.buscar(null, null, true).stream()
                .map(v -> v.getProductor().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Convocados por ser afiliados.
     * <p>
     * Se dejan fuera dos grupos, por la misma razón: no cuentan para el quórum.
     * Los deshabilitados, que ya no son del padrón, y los observados por la
     * asamblea, que están suspendidos de sus derechos mientras dure el veto.
     */
    private List<Convocado> deProductores(List<Productor> productores, Set<Long> observados) {
        return productores.stream()
                .filter(Productor::isEstado)
                .filter(p -> !observados.contains(p.getId()))
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
    private List<Convocado> deCargos(List<Cargo> cargos, Set<Long> observados) {
        List<Convocado> lista = new ArrayList<>();
        for (Cargo c : cargos) {
            Productor p = c.getProductor();
            if (!p.isEstado() || observados.contains(p.getId())) {
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
        reunion.setVetosHabilitados(Boolean.TRUE.equals(peticion.vetosHabilitados()));
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
                asistenciaRepository.findProductoresPresentesEnLaReunion(reunion.getId()).size());
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
                asistenciaRepository.findProductoresPresentesEnLaReunion(reunionId).size(),
                convocados);
    }

    Reunion buscar(Long id) {
        return reunionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("reunión", id));
    }
}
