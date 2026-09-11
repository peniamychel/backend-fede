package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenLocal;
import com.federa.backend.dto.*;
import com.federa.backend.exception.PlanillaInvalidaException;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.*;
import com.federa.backend.model.enums.*;
import com.federa.backend.repository.*;
import com.federa.backend.util.Textos;
import com.federa.backend.util.SimilitudNombres;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Analiza y aplica la nómina completa de productores con SISTEMA de UDESTRO. */
@Service
@Transactional(readOnly = true)
public class ConciliacionUdestroService {

    private static final String FEDERACION_TRABAJO = "CARRASCO TROPICAL";
    private static final int MAX_FILAS = 20_000;
    private static final int MAX_NOMBRE = 60;
    private static final int MAX_CI = 20;
    private static final String OBSERVACION_CONFLICTO =
            "Conflicto de identidad detectado en conciliación UDESTRO";
    private static final int SIMILITUD_MINIMA_MISMA_PERSONA = 50;

    private final LectorPlanilla lector;
    private final FederacionRepository federaciones;
    private final CentralRepository centrales;
    private final SindicatoRepository sindicatos;
    private final ProductorRepository productores;
    private final TenenciaLoteRepository tenencias;
    private final ImagenProductorRepository imagenes;
    private final ConciliacionUdestroRepository conciliaciones;
    private final FilaConciliacionUdestroRepository filas;
    private final NumeradorPadron numerador;
    private final LoteService loteService;
    private final EntityManager entityManager;

    public ConciliacionUdestroService(LectorPlanilla lector,
                                      FederacionRepository federaciones,
                                      CentralRepository centrales,
                                      SindicatoRepository sindicatos,
                                      ProductorRepository productores,
                                      TenenciaLoteRepository tenencias,
                                      ImagenProductorRepository imagenes,
                                      ConciliacionUdestroRepository conciliaciones,
                                       FilaConciliacionUdestroRepository filas,
                                       NumeradorPadron numerador,
                                       LoteService loteService,
                                       EntityManager entityManager) {
        this.lector = lector;
        this.federaciones = federaciones;
        this.centrales = centrales;
        this.sindicatos = sindicatos;
        this.productores = productores;
        this.tenencias = tenencias;
        this.imagenes = imagenes;
        this.conciliaciones = conciliaciones;
        this.filas = filas;
        this.numerador = numerador;
        this.loteService = loteService;
        this.entityManager = entityManager;
    }

    /** Crea una vista previa persistente. No modifica productores, lotes ni jerarquía. */
    @Transactional
    public ConciliacionUdestroResponse analizar(byte[] contenido, String nombreArchivo) {
        List<LectorPlanilla.Fila> fuente = lector.leer(new ByteArrayInputStream(contenido));
        if (fuente.isEmpty()) {
            throw new PlanillaInvalidaException("La planilla UDESTRO no contiene productores.");
        }
        if (fuente.size() > MAX_FILAS) {
            throw new PlanillaInvalidaException("La planilla supera el máximo de "
                    + MAX_FILAS + " filas.");
        }

        Federacion federacion = federaciones.findByNombreIgnoreCase(FEDERACION_TRABAJO)
                .orElseThrow(() -> new ReglaNegocioException(
                        "No existe la federación única " + FEDERACION_TRABAJO + "."));

        ConciliacionUdestro conciliacion = new ConciliacionUdestro();
        conciliacion.setFederacion(federacion);
        conciliacion.setNombreArchivo(limitarNombreArchivo(nombreArchivo));
        conciliacion.setSha256(sha256(contenido));
        conciliacion.setFilasExcel(fuente.size());
        conciliacion.setFase(EstadoConciliacionUdestro.BORRADOR);
        conciliaciones.save(conciliacion);

        Contexto contexto = cargarContexto(federacion);
        Map<String, List<LectorPlanilla.Fila>> fuentePorCi = fuente.stream()
                .collect(Collectors.groupingBy(f -> claveCi(f.ci()), LinkedHashMap::new,
                        Collectors.toList()));
        Set<String> cisFuenteValidas = fuentePorCi.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue().size() == 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        List<FilaConciliacionUdestro> propuestas = new ArrayList<>();
        for (LectorPlanilla.Fila fila : fuente) {
            propuestas.add(analizarFuente(conciliacion, fila, fuentePorCi, contexto));
        }
        for (Productor actual : contexto.productoresFederacion()) {
            String ci = claveCi(actual.getCi());
            if (ci == null || !cisFuenteValidas.contains(ci)) {
                FilaConciliacionUdestro ausencia = analizarAusencia(conciliacion, actual,
                        contexto.vigentesPorProductor().getOrDefault(actual.getId(), List.of()));
                if (ausencia != null) propuestas.add(ausencia);
            }
        }
        filas.saveAll(propuestas);
        filas.flush();
        return resumen(conciliacion, propuestas);
    }

    public ConciliacionUdestroResponse obtener(Long id) {
        ConciliacionUdestro conciliacion = buscar(id);
        return resumen(conciliacion,
                filas.findByConciliacionIdOrderByIdAsc(conciliacion.getId()));
    }

    public ConciliacionUdestroResponse ultimoBorrador() {
        ConciliacionUdestro conciliacion = conciliaciones
                .findFirstByFaseOrderByCreatedAtDesc(EstadoConciliacionUdestro.BORRADOR)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "borrador de conciliación UDESTRO", 0L));
        return resumen(conciliacion,
                filas.findByConciliacionIdOrderByIdAsc(conciliacion.getId()));
    }

    public PagedModel<FilaConciliacionUdestroResponse> listarFilas(
            Long id, AccionConciliacionUdestro accion, Pageable pageable) {
        buscar(id);
        Page<FilaConciliacionUdestro> pagina = accion == null
                ? filas.findByConciliacionId(id, pageable)
                : filas.findByConciliacionIdAndAccion(id, accion, pageable);
        return new PagedModel<>(mapearPagina(pagina));
    }

    /** Guarda una decisión en el borrador; todavía no modifica el padrón. */
    @Transactional
    public FilaConciliacionUdestroResponse decidir(Long conciliacionId, Long filaId,
                                                   DecisionUdestroRequest request) {
        ConciliacionUdestro conciliacion = conciliaciones.findByIdParaActualizar(conciliacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("conciliación UDESTRO",
                        conciliacionId));
        exigirBorrador(conciliacion);
        FilaConciliacionUdestro fila = filas.findByIdAndConciliacionId(filaId, conciliacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "fila de conciliación UDESTRO", filaId));
        if (fila.getAccion() != AccionConciliacionUdestro.CONFLICTO_IDENTIDAD) {
            throw new ReglaNegocioException("Solo se puede decidir una fila con conflicto.");
        }

        if (request.decision() == DecisionConflictoUdestro.PENDIENTE) {
            fila.setDecisionConflicto(DecisionConflictoUdestro.PENDIENTE);
            fila.setProductorSeleccionadoId(null);
            fila.setProductorActualizadoEn(null);
            fila.setClasificacionAnterior(null);
        } else {
            if (request.productorId() == null ||
                    !idsCandidatos(fila).contains(request.productorId())) {
                throw new ReglaNegocioException(
                        "Elegí uno de los productores mostrados en el conflicto.");
            }
            Productor seleccionado = productores.findById(request.productorId())
                    .orElseThrow(() -> new ReglaNegocioException(
                            "El productor elegido ya no existe. Volvé a analizar el Excel."));
            fila.setDecisionConflicto(request.decision());
            fila.setProductorSeleccionadoId(request.productorId());
            fila.setProductorActualizadoEn(seleccionado.getUpdatedAt());
            List<TenenciaLote> vigentes = tenencias
                    .findHistorialDeProductor(seleccionado.getId()).stream()
                    .filter(TenenciaLote::estaVigente).toList();
            fila.setClasificacionAnterior(clasificacion(seleccionado, vigentes));
        }
        filas.flush();
        return mapearFila(fila, cargarCandidatos(List.of(fila)));
    }

    /** Revalida y ejecuta la propuesta una única vez. */
    @Transactional
    public ConciliacionUdestroResponse aplicar(Long id,
            AplicarConciliacionUdestroRequest request) {
        ConciliacionUdestro conciliacion = conciliaciones.findByIdParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("conciliación UDESTRO", id));
        exigirBorrador(conciliacion);
        List<FilaConciliacionUdestro> propuestas =
                filas.findByConciliacionIdOrderByIdAsc(id);
        validarAplicable(propuestas, request.aprobarSindicatosNuevos());
        validarCedulasSinCambios(propuestas);

        // Son filas de una vista previa: al aplicar solo se leen. Desvincularlas
        // evita que Hibernate conserve y revise miles de entidades hasta el
        // final de la transacción, sin perder la atomicidad de la operación.
        propuestas.forEach(entityManager::detach);

        Map<Long, Central> centralesPorId = centrales
                .findByFederacionIdOrderByNombreAsc(conciliacion.getFederacion().getId()).stream()
                .collect(Collectors.toMap(Central::getId, Function.identity()));
        Map<String, Sindicato> sindicatosDestino = new HashMap<>();
        for (Central central : centralesPorId.values()) {
            for (Sindicato sindicato : sindicatos.findByCentralIdOrderByNombreAsc(central.getId())) {
                sindicatosDestino.put(claveSindicato(central.getId(), sindicato.getNombre()),
                        sindicato);
            }
        }

        Set<Long> idsActuales = idsProductores(propuestas);
        Map<Long, Productor> actuales = productores.findAllById(idsActuales).stream()
                .collect(Collectors.toMap(Productor::getId, Function.identity()));
        Map<Long, List<TenenciaLote>> vigentesPorProductor = idsActuales.isEmpty()
                ? Map.of()
                : tenencias.findVigentesDeProductores(new ArrayList<>(idsActuales)).stream()
                        .collect(Collectors.groupingBy(t -> t.getProductor().getId()));
        Map<Long, Integer> siguientePorCentral = new HashMap<>();
        Map<String, Lote> gruposARecalcular = new LinkedHashMap<>();

        for (FilaConciliacionUdestro propuesta : propuestas) {
            switch (propuesta.getAccion()) {
                case ALTA_SISTEMA -> crearProductor(propuesta, centralesPorId, sindicatosDestino,
                        siguientePorCentral);
                case CAMBIAR_A_SISTEMA -> cambiarClasificacion(
                        actualVigente(propuesta, actuales), EstadoLote.CON_SISTEMA,
                        vigentesPorProductor, gruposARecalcular);
                case OBSERVAR_Y_CAMBIAR_A_SISTEMA -> observarYClasificar(
                        propuesta, actualVigente(propuesta, actuales), vigentesPorProductor,
                        gruposARecalcular);
                case CAMBIAR_A_BLANCO -> cambiarClasificacion(
                        actualVigente(propuesta, actuales), EstadoLote.BLANCO,
                        vigentesPorProductor, gruposARecalcular);
                case CONFLICTO_IDENTIDAD -> aplicarConflicto(propuesta, actuales,
                        centralesPorId, sindicatosDestino, siguientePorCentral,
                        vigentesPorProductor, gruposARecalcular);
                case CONSERVAR -> { /* Se guarda en el informe; no toca el padrón. */ }
                case ERROR -> throw new ReglaNegocioException(
                        "La conciliación contiene errores pendientes.");
            }
        }
        productores.flush();
        loteService.recalcularCodigosDeGrupos(gruposARecalcular.values());
        productores.flush();

        conciliacion.setFase(EstadoConciliacionUdestro.APLICADA);
        conciliacion.setAplicadaEn(LocalDateTime.now());
        conciliaciones.flush();
        return resumen(conciliacion, propuestas);
    }

    private FilaConciliacionUdestro analizarFuente(ConciliacionUdestro conciliacion,
            LectorPlanilla.Fila fuente,
            Map<String, List<LectorPlanilla.Fila>> fuentePorCi, Contexto contexto) {
        FilaConciliacionUdestro fila = baseFuente(conciliacion, fuente);
        String ci = claveCi(fuente.ci());
        if (ci == null) return error(fila, "La cédula es obligatoria en la nómina UDESTRO.");
        if (fuentePorCi.getOrDefault(ci, List.of()).size() > 1) {
            String numeros = fuentePorCi.get(ci).stream().map(f -> String.valueOf(f.numero()))
                    .collect(Collectors.joining(", "));
            return error(fila, "La cédula se repite en las filas " + numeros + " del Excel.");
        }
        if (excede(fuente.central(), MAX_NOMBRE) || excede(fuente.sindicato(), MAX_NOMBRE)
                || excede(fuente.nombres(), MAX_NOMBRE) || excede(fuente.apellidos(), MAX_NOMBRE)
                || excede(Textos.limpiar(fuente.ci()), MAX_CI)) {
            return error(fila, "Uno de los datos supera la longitud admitida.");
        }
        if (Textos.limpiar(fuente.central()) == null || Textos.limpiar(fuente.sindicato()) == null
                || Textos.limpiar(fuente.nombres()) == null
                || Textos.limpiar(fuente.apellidos()) == null) {
            return error(fila, "Central, sindicato, nombres, apellidos y CI son obligatorios.");
        }

        Central central = contexto.centralesPorNombre().get(Textos.normalizar(fuente.central()));
        if (central == null) return error(fila, "La central no está registrada en "
                + FEDERACION_TRABAJO + ". Creala manualmente con su abreviatura.");
        fila.setCentralDestinoId(central.getId());
        Sindicato sindicato = contexto.sindicatosPorClave().get(
                claveSindicato(central.getId(), fuente.sindicato()));
        fila.setSindicatoDestinoId(sindicato == null ? null : sindicato.getId());
        fila.setSindicatoNuevo(sindicato == null);

        List<Productor> candidatos = contexto.productoresPorCi().getOrDefault(ci, List.of());
        if (candidatos.isEmpty()) {
            fila.setAccion(AccionConciliacionUdestro.ALTA_SISTEMA);
            fila.setMotivo("No existe en el padrón; se registrará con SISTEMA y sin lote.");
            return fila;
        }

        if (candidatos.size() == 1) {
            Productor actual = candidatos.get(0);
            List<TenenciaLote> vigentes = contexto.vigentesPorProductor()
                    .getOrDefault(actual.getId(), List.of());
            prepararActual(fila, actual, clasificacion(actual, vigentes));
            fila.setCandidatosIds(String.valueOf(actual.getId()));
            int similitud = similitudIdentidad(fuente, actual);
            fila.setSimilitudNombre(similitud);
            if (vigentes.size() > 1) {
                return error(fila, "El productor tiene más de una parcela vigente; revisala "
                        + "manualmente antes de conciliar.");
            }
            if (similitud < SIMILITUD_MINIMA_MISMA_PERSONA) {
                fila.setAccion(AccionConciliacionUdestro.OBSERVAR_Y_CAMBIAR_A_SISTEMA);
                fila.setMotivo("La CI coincide, pero el nombre solo tiene " + similitud
                        + "% de similitud. Se conservarán los datos actuales, pasará a "
                        + "SISTEMA y quedará observado.");
                return fila;
            }
            if (clasificacion(actual, vigentes) == EstadoLote.CON_SISTEMA) {
                fila.setAccion(AccionConciliacionUdestro.CONSERVAR);
                fila.setMotivo(motivoCoincidencia(similitud, central, sindicato, actual,
                        "Ya está clasificado con SISTEMA."));
            } else {
                fila.setAccion(AccionConciliacionUdestro.CAMBIAR_A_SISTEMA);
                String motivo = vigentes.isEmpty()
                        ? "Quedará con SISTEMA pendiente hasta asignarle número de lote."
                        : "Su parcela se clasificará con SISTEMA.";
                fila.setMotivo(motivoCoincidencia(similitud, central, sindicato, actual,
                        motivo));
            }
            return fila;
        }

        fila.setAccion(AccionConciliacionUdestro.CONFLICTO_IDENTIDAD);
        fila.setDecisionConflicto(DecisionConflictoUdestro.PENDIENTE);
        fila.setCandidatosIds(candidatos.stream().map(Productor::getId)
                .map(String::valueOf).collect(Collectors.joining(",")));
        fila.setMotivo(motivoConflicto(fuente, central, sindicato, candidatos));
        return fila;
    }

    private FilaConciliacionUdestro analizarAusencia(ConciliacionUdestro conciliacion,
            Productor actual, List<TenenciaLote> vigentes) {
        EstadoLote clasificacion = clasificacion(actual, vigentes);
        boolean tieneClasificacionQueSeConserva = clasificacion == EstadoLote.BLANCO
                || clasificacion == EstadoLote.FRACCIONADO
                || clasificacion == EstadoLote.DETALLISTA
                || clasificacion == EstadoLote.COMUNITARIO;
        boolean pasaABlanco = !tieneClasificacionQueSeConserva;
        FilaConciliacionUdestro fila = new FilaConciliacionUdestro();
        fila.setConciliacion(conciliacion);
        fila.setCentralNombre(actual.getSindicato().getCentral().getNombre());
        fila.setSindicatoNombre(actual.getSindicato().getNombre());
        fila.setNombresUdestro(actual.getNombres());
        fila.setApellidosUdestro(actual.getApellidos());
        fila.setCi(actual.getCi());
        prepararActual(fila, actual, clasificacion);
        if (!pasaABlanco) {
            fila.setAccion(AccionConciliacionUdestro.CONSERVAR);
            fila.setMotivo("No aparece en UDESTRO; conserva su clasificación "
                    + clasificacion.name() + ".");
            return fila;
        }
        if (vigentes.size() > 1) {
            return error(fila, "El productor ausente de UDESTRO tiene más de una parcela "
                    + "vigente; debe revisarse manualmente.");
        }
        fila.setAccion(AccionConciliacionUdestro.CAMBIAR_A_BLANCO);
        fila.setMotivo(vigentes.isEmpty()
                ? "No aparece en UDESTRO; quedará BLANCO pendiente hasta asignarle lote."
                : "No aparece en la nómina completa UDESTRO; pasará a BLANCO.");
        return fila;
    }

    private FilaConciliacionUdestro baseFuente(ConciliacionUdestro conciliacion,
                                               LectorPlanilla.Fila fuente) {
        FilaConciliacionUdestro fila = new FilaConciliacionUdestro();
        fila.setConciliacion(conciliacion);
        fila.setNumeroFila(fuente.numero());
        fila.setCentralNombre(Textos.normalizarParaGuardar(fuente.central()));
        fila.setSindicatoNombre(Textos.normalizarParaGuardar(fuente.sindicato()));
        fila.setNombresUdestro(Textos.normalizarParaGuardar(fuente.nombres()));
        fila.setApellidosUdestro(Textos.normalizarParaGuardar(fuente.apellidos()));
        fila.setCi(Textos.limpiar(fuente.ci()));
        return fila;
    }

    private void prepararActual(FilaConciliacionUdestro fila, Productor p,
                                EstadoLote clasificacion) {
        fila.setProductorId(p.getId());
        fila.setProductorActualizadoEn(p.getUpdatedAt());
        fila.setClasificacionAnterior(clasificacion);
    }

    private FilaConciliacionUdestro error(FilaConciliacionUdestro fila, String motivo) {
        fila.setAccion(AccionConciliacionUdestro.ERROR);
        fila.setMotivo(motivo);
        return fila;
    }

    private void aplicarConflicto(FilaConciliacionUdestro fila,
            Map<Long, Productor> actuales, Map<Long, Central> centralesPorId,
            Map<String, Sindicato> sindicatosDestino, Map<Long, Integer> siguientePorCentral,
            Map<Long, List<TenenciaLote>> vigentesPorProductor,
            Map<String, Lote> gruposARecalcular) {
        Productor elegido = actuales.get(fila.getProductorSeleccionadoId());
        if (elegido == null) {
            throw new ReglaNegocioException("El productor elegido en el conflicto ya no existe.");
        }
        validarSnapshot(fila, elegido);
        if (fila.getDecisionConflicto() == DecisionConflictoUdestro.MISMA_PERSONA) {
            cambiarClasificacion(elegido, EstadoLote.CON_SISTEMA, vigentesPorProductor,
                    gruposARecalcular);
            return;
        }
        if (fila.getDecisionConflicto() == DecisionConflictoUdestro.PERSONAS_DISTINTAS) {
            String detalle = OBSERVACION_CONFLICTO + " (CI " + fila.getCi()
                    + ", fila " + fila.getNumeroFila() + ").";
            String existente = Textos.limpiar(elegido.getObservacionManual());
            elegido.setObservacionManual(existente == null ? detalle
                    : limitarObservacion(existente + ". " + detalle));
            crearProductor(fila, centralesPorId, sindicatosDestino, siguientePorCentral);
            return;
        }
        throw new ReglaNegocioException("Hay un conflicto de identidad sin resolver.");
    }

    private Productor crearProductor(FilaConciliacionUdestro fila,
            Map<Long, Central> centralesPorId, Map<String, Sindicato> sindicatosDestino,
            Map<Long, Integer> siguientePorCentral) {
        Central central = centralesPorId.get(fila.getCentralDestinoId());
        if (central == null) throw new ReglaNegocioException(
                "La central de la fila " + fila.getNumeroFila() + " ya no existe.");
        String clave = claveSindicato(central.getId(), fila.getSindicatoNombre());
        Sindicato sindicato = sindicatosDestino.get(clave);
        if (sindicato == null) {
            sindicato = new Sindicato();
            sindicato.setNombre(fila.getSindicatoNombre());
            sindicato.setCentral(central);
            sindicato = sindicatos.save(sindicato);
            sindicatosDestino.put(clave, sindicato);
        }
        Productor nuevo = new Productor();
        nuevo.setNombres(fila.getNombresUdestro());
        nuevo.setApellidos(fila.getApellidosUdestro());
        nuevo.setCi(fila.getCi());
        nuevo.setSindicato(sindicato);
        nuevo.setClasificacionPendiente(EstadoLote.CON_SISTEMA);
        nuevo.setRevisionSiePendiente(true);
        int correlativo = siguientePorCentral.computeIfAbsent(central.getId(), numerador::siguiente);
        nuevo.setCorrelativo(correlativo);
        siguientePorCentral.put(central.getId(), NumeradorPadron.despuesDe(correlativo));
        Productor guardado = productores.save(nuevo);
        // GenerationType.IDENTITY ejecuta este INSERT inmediatamente. Ya no se
        // necesita mantener la entidad en el contexto durante miles de altas.
        entityManager.detach(guardado);
        return guardado;
    }

    private void cambiarClasificacion(Productor productor, EstadoLote destino,
            Map<Long, List<TenenciaLote>> vigentesPorProductor,
            Map<String, Lote> gruposARecalcular) {
        List<TenenciaLote> vigentes = vigentesPorProductor.getOrDefault(
                productor.getId(), List.of());
        if (vigentes.size() > 1) throw new ReglaNegocioException(
                productor.getNombreCompleto() + " tiene más de una parcela vigente.");
        if (vigentes.isEmpty()) {
            productor.setClasificacionPendiente(destino);
            return;
        }
        Lote lote = vigentes.get(0).getLote();
        boolean cambiaPrioridad = (lote.getEstadoLote() == EstadoLote.CON_SISTEMA)
                != (destino == EstadoLote.CON_SISTEMA);
        lote.setEstadoLote(destino);
        lote.setEstadoOriginal(destino == EstadoLote.CON_SISTEMA ? "SISTEMA" : destino.name());
        productor.setClasificacionPendiente(null);
        if (cambiaPrioridad && lote.getNumero() != null && !lote.getNumero().isBlank()) {
            gruposARecalcular.put(claveGrupo(lote), lote);
        }
    }

    private void observarYClasificar(FilaConciliacionUdestro fila, Productor productor,
            Map<Long, List<TenenciaLote>> vigentesPorProductor,
            Map<String, Lote> gruposARecalcular) {
        cambiarClasificacion(productor, EstadoLote.CON_SISTEMA, vigentesPorProductor,
                gruposARecalcular);
        String nombreUdestro = (fila.getNombresUdestro() + " "
                + fila.getApellidosUdestro()).trim();
        String detalle = "El nombre registrado no corresponde a la CI " + fila.getCi()
                + " según UDESTRO. UDESTRO indica: " + nombreUdestro + ". Similitud: "
                + fila.getSimilitudNombre() + "%";
        String existente = Textos.limpiar(productor.getObservacionManual());
        productor.setObservacionManual(existente == null ? limitarObservacion(detalle)
                : limitarObservacion(detalle + ". Observación anterior: " + existente));
    }

    private void validarAplicable(List<FilaConciliacionUdestro> propuestas,
                                  boolean aprobarSindicatos) {
        long errores = propuestas.stream().filter(f -> f.getAccion()
                == AccionConciliacionUdestro.ERROR).count();
        long pendientes = propuestas.stream().filter(f -> f.getAccion()
                        == AccionConciliacionUdestro.CONFLICTO_IDENTIDAD)
                .filter(f -> f.getDecisionConflicto() == null || f.getDecisionConflicto()
                        == DecisionConflictoUdestro.PENDIENTE).count();
        if (errores > 0) throw new ReglaNegocioException(
                "La conciliación tiene " + errores + " error(es) que deben corregirse.");
        if (pendientes > 0) throw new ReglaNegocioException(
                "Falta resolver " + pendientes + " conflicto(s) de identidad.");
        if (!aprobarSindicatos && propuestas.stream().anyMatch(FilaConciliacionUdestro::isSindicatoNuevo)) {
            throw new ReglaNegocioException("Debés aprobar la creación de los sindicatos nuevos.");
        }
    }

    private void validarCedulasSinCambios(List<FilaConciliacionUdestro> propuestas) {
        Map<String, Set<Long>> actuales = new HashMap<>();
        for (Object[] dato : productores.findCedulasParaImportacion()) {
            String clave = claveCi((String) dato[0]);
            if (clave != null) actuales.computeIfAbsent(clave, k -> new LinkedHashSet<>())
                    .add((Long) dato[1]);
        }
        for (FilaConciliacionUdestro fila : propuestas) {
            if (fila.getNumeroFila() == null || fila.getAccion() == AccionConciliacionUdestro.ERROR)
                continue;
            Set<Long> ahora = actuales.getOrDefault(claveCi(fila.getCi()), Set.of());
            Set<Long> antes = new LinkedHashSet<>(idsCandidatos(fila));
            if (fila.getProductorId() != null) antes.add(fila.getProductorId());
            if (!ahora.equals(antes)) throw new ReglaNegocioException(
                    "El padrón cambió para la CI " + fila.getCi()
                            + " después del análisis. Volvé a analizar el Excel.");
        }
    }

    private Productor actualVigente(FilaConciliacionUdestro fila,
                                    Map<Long, Productor> actuales) {
        Productor actual = actuales.get(fila.getProductorId());
        if (actual == null) throw new ReglaNegocioException(
                "Un productor de la propuesta ya no existe. Volvé a analizar el Excel.");
        validarSnapshot(fila, actual);
        return actual;
    }

    private void validarSnapshot(FilaConciliacionUdestro fila, Productor actual) {
        if (fila.getProductorActualizadoEn() != null
                && !Objects.equals(fila.getProductorActualizadoEn(), actual.getUpdatedAt())) {
            throw new ReglaNegocioException("Los datos de " + actual.getNombreCompleto()
                    + " cambiaron después del análisis. Volvé a analizar el Excel.");
        }
    }

    private Contexto cargarContexto(Federacion federacion) {
        Map<String, Central> centralesPorNombre = centrales
                .findByFederacionIdOrderByNombreAsc(federacion.getId()).stream()
                .collect(Collectors.toMap(c -> Textos.normalizar(c.getNombre()),
                        Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<String, Sindicato> sindicatosPorClave = new LinkedHashMap<>();
        for (Central central : centralesPorNombre.values()) {
            for (Sindicato sindicato : sindicatos.findByCentralIdOrderByNombreAsc(central.getId())) {
                sindicatosPorClave.put(claveSindicato(central.getId(), sindicato.getNombre()),
                        sindicato);
            }
        }
        List<Productor> todos = productores.findAllParaConciliacionUdestro();
        Map<String, List<Productor>> porCi = new HashMap<>();
        Map<Long, List<TenenciaLote>> vigentes = new HashMap<>();
        for (Productor p : todos) {
            String ci = claveCi(p.getCi());
            if (ci != null) porCi.computeIfAbsent(ci, k -> new ArrayList<>()).add(p);
            vigentes.put(p.getId(), p.getTenencias().stream()
                    .filter(TenenciaLote::estaVigente).toList());
        }
        List<Productor> deFederacion = todos.stream()
                .filter(p -> p.getSindicato().getCentral().getFederacion().getId()
                        .equals(federacion.getId())).toList();
        return new Contexto(centralesPorNombre, sindicatosPorClave, porCi, vigentes, deFederacion);
    }

    private Page<FilaConciliacionUdestroResponse> mapearPagina(
            Page<FilaConciliacionUdestro> pagina) {
        Map<Long, CandidatoUdestro> candidatos = cargarCandidatos(pagina.getContent());
        return pagina.map(f -> mapearFila(f, candidatos));
    }

    private FilaConciliacionUdestroResponse mapearFila(FilaConciliacionUdestro fila,
                                                        Map<Long, CandidatoUdestro> candidatos) {
        List<CandidatoUdestro> lista = idsCandidatos(fila).stream()
                .map(candidatos::get).filter(Objects::nonNull).toList();
        return FilaConciliacionUdestroResponse.desde(fila, lista);
    }

    private Map<Long, CandidatoUdestro> cargarCandidatos(
            Collection<FilaConciliacionUdestro> propuestas) {
        Set<Long> ids = propuestas.stream().flatMap(f -> idsCandidatos(f).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) return Map.of();
        Map<Long, Productor> porId = productores.findAllById(ids).stream()
                .collect(Collectors.toMap(Productor::getId, Function.identity()));
        Map<Long, List<TenenciaLote>> vigentes = tenencias.findVigentesDeProductores(
                new ArrayList<>(ids)).stream().collect(Collectors.groupingBy(
                        t -> t.getProductor().getId()));
        Map<Long, String> fotos = new HashMap<>();
        for (Object[] imagen : imagenes.findClavesPorProductores(new ArrayList<>(ids))) {
            if (imagen[1] == TipoImagen.ORIGINAL) {
                fotos.put((Long) imagen[0], AlmacenLocal.RUTA_PUBLICA + imagen[2]);
            }
        }
        Map<Long, CandidatoUdestro> resultado = new LinkedHashMap<>();
        for (Long id : ids) {
            Productor p = porId.get(id);
            if (p != null) resultado.put(id, CandidatoUdestro.desde(p,
                    clasificacion(p, vigentes.getOrDefault(id, List.of())), fotos.get(id)));
        }
        return resultado;
    }

    private ConciliacionUdestroResponse resumen(ConciliacionUdestro conciliacion,
            List<FilaConciliacionUdestro> propuestas) {
        long altas = contar(propuestas, AccionConciliacionUdestro.ALTA_SISTEMA);
        long sistema = contar(propuestas, AccionConciliacionUdestro.CAMBIAR_A_SISTEMA);
        long observados = contar(propuestas,
                AccionConciliacionUdestro.OBSERVAR_Y_CAMBIAR_A_SISTEMA);
        long blanco = contar(propuestas, AccionConciliacionUdestro.CAMBIAR_A_BLANCO);
        long conservar = contar(propuestas, AccionConciliacionUdestro.CONSERVAR);
        long conflictos = contar(propuestas, AccionConciliacionUdestro.CONFLICTO_IDENTIDAD);
        long pendientes = propuestas.stream().filter(f -> f.getAccion()
                        == AccionConciliacionUdestro.CONFLICTO_IDENTIDAD)
                .filter(f -> f.getDecisionConflicto() == null || f.getDecisionConflicto()
                        == DecisionConflictoUdestro.PENDIENTE).count();
        long errores = contar(propuestas, AccionConciliacionUdestro.ERROR);
        List<SindicatoNuevo> nuevos = propuestas.stream().filter(FilaConciliacionUdestro::isSindicatoNuevo)
                .map(f -> new SindicatoNuevo(f.getCentralNombre(), f.getSindicatoNombre()))
                .distinct().sorted(Comparator.comparing(SindicatoNuevo::central)
                        .thenComparing(SindicatoNuevo::sindicato)).toList();
        return new ConciliacionUdestroResponse(conciliacion.getId(), conciliacion.getFase(),
                conciliacion.getNombreArchivo(), conciliacion.getSha256(),
                conciliacion.getFederacion().getId(), conciliacion.getFederacion().getNombre(),
                conciliacion.getFilasExcel(), altas, sistema, observados, blanco, conservar,
                conflictos,
                pendientes, errores, nuevos,
                conciliacion.getFase() == EstadoConciliacionUdestro.BORRADOR
                        && pendientes == 0 && errores == 0,
                conciliacion.getCreatedAt(), conciliacion.getAplicadaEn());
    }

    private long contar(List<FilaConciliacionUdestro> filas,
                        AccionConciliacionUdestro accion) {
        return filas.stream().filter(f -> f.getAccion() == accion).count();
    }

    private Set<Long> idsProductores(List<FilaConciliacionUdestro> propuestas) {
        Set<Long> ids = new LinkedHashSet<>();
        for (FilaConciliacionUdestro f : propuestas) {
            if (f.getProductorId() != null) ids.add(f.getProductorId());
            ids.addAll(idsCandidatos(f));
        }
        return ids;
    }

    private List<Long> idsCandidatos(FilaConciliacionUdestro fila) {
        if (fila.getCandidatosIds() == null || fila.getCandidatosIds().isBlank()) return List.of();
        return Arrays.stream(fila.getCandidatosIds().split(","))
                .filter(s -> !s.isBlank()).map(Long::valueOf).toList();
    }

    private EstadoLote clasificacion(Productor p, List<TenenciaLote> vigentes) {
        return vigentes.isEmpty() ? p.getClasificacionPendiente()
                : vigentes.get(0).getLote().getEstadoLote();
    }

    private boolean mismaIdentidad(LectorPlanilla.Fila fuente, Productor p) {
        String esperado = Textos.normalizar(fuente.nombres() + " " + fuente.apellidos());
        return Objects.equals(esperado, Textos.normalizar(p.getNombreCompleto()));
    }

    private int similitudIdentidad(LectorPlanilla.Fila fuente, Productor p) {
        return SimilitudNombres.porcentaje(
                fuente.nombres() + " " + fuente.apellidos(), p.getNombreCompleto());
    }

    private String motivoCoincidencia(int similitud, Central central, Sindicato sindicato,
            Productor actual, String resultado) {
        String ubicacion = mismaUbicacion(central, sindicato, actual) ? ""
                : " Se conservarán su central y sindicato actuales.";
        return "Coincidencia de nombre: " + similitud + "%. " + resultado + ubicacion;
    }

    private boolean mismaUbicacion(Central central, Sindicato sindicato, Productor p) {
        return sindicato != null && p.getSindicato().getId().equals(sindicato.getId())
                && p.getSindicato().getCentral().getId().equals(central.getId());
    }

    private String motivoConflicto(LectorPlanilla.Fila fuente, Central central,
            Sindicato sindicato, List<Productor> candidatos) {
        if (candidatos.size() > 1) return "La CI pertenece a varios productores actuales. "
                + "Elegí la identidad que corresponde antes de aplicar.";
        Productor p = candidatos.get(0);
        if (!mismaIdentidad(fuente, p)) return "La CI coincide, pero el nombre de UDESTRO "
                + "es diferente al registrado.";
        if (!mismaUbicacion(central, sindicato, p)) return "La CI y el nombre coinciden, pero "
                + "la central o el sindicato son diferentes. La conciliación no lo trasladará.";
        return "La identidad requiere revisión manual.";
    }

    private String claveCi(String ci) {
        return ImportacionService.claveCedula(ci);
    }

    private String claveSindicato(Long centralId, String sindicato) {
        return centralId + "|" + Textos.normalizar(sindicato);
    }

    private String claveGrupo(Lote lote) {
        return lote.getSindicato().getId() + "|" + Textos.normalizar(lote.getNumero());
    }

    private boolean excede(String valor, int maximo) {
        String limpio = Textos.limpiar(valor);
        return limpio != null && limpio.length() > maximo;
    }

    private String limitarNombreArchivo(String nombre) {
        String limpio = Textos.limpiar(nombre);
        if (limpio == null) return "udestro.xlsx";
        return limpio.length() <= 180 ? limpio : limpio.substring(0, 180);
    }

    private String limitarObservacion(String observacion) {
        return observacion.length() <= 500 ? observacion : observacion.substring(0, 500);
    }

    private String sha256(byte[] contenido) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(contenido);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("La JVM no ofrece SHA-256", imposible);
        }
    }

    private ConciliacionUdestro buscar(Long id) {
        return conciliaciones.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("conciliación UDESTRO", id));
    }

    private void exigirBorrador(ConciliacionUdestro conciliacion) {
        if (conciliacion.getFase() != EstadoConciliacionUdestro.BORRADOR) {
            throw new ReglaNegocioException("La conciliación ya fue aplicada y no puede cambiarse.");
        }
    }

    private record Contexto(
            Map<String, Central> centralesPorNombre,
            Map<String, Sindicato> sindicatosPorClave,
            Map<String, List<Productor>> productoresPorCi,
            Map<Long, List<TenenciaLote>> vigentesPorProductor,
            List<Productor> productoresFederacion) {
    }
}
