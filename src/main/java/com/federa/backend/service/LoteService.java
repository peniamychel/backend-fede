package com.federa.backend.service;

import com.federa.backend.dto.LoteRequest;
import com.federa.backend.dto.LoteResponse;
import com.federa.backend.dto.TenenciaResponse;
import com.federa.backend.dto.TraspasoLoteRequest;
import com.federa.backend.dto.UbicacionRequest;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.TenenciaLote;
import com.federa.backend.model.TenenciaSistema;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.model.enums.Mercado;
import com.federa.backend.repository.LoteRepository;
import com.federa.backend.repository.TenenciaLoteRepository;
import com.federa.backend.repository.TenenciaSistemaRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lotes y su tenencia.
 * <p>
 * La idea que ordena todo: <b>el lote pertenece al sindicato y no se mueve</b>.
 * Lo que cambia es quién lo tiene, y eso se guarda como una sucesión de
 * períodos. Un traspaso no pisa un dato: cierra el período anterior y abre uno
 * nuevo, así el padrón puede decir quién tenía la parcela en cualquier fecha.
 */
@Service
@Transactional(readOnly = true)
public class LoteService {

    private final LoteRepository loteRepository;
    private final TenenciaLoteRepository tenenciaRepository;
    private final TenenciaSistemaRepository tenenciaSistemaRepository;
    private final ProductorService productorService;
    private final SindicatoService sindicatoService;

    public LoteService(LoteRepository loteRepository,
                       TenenciaLoteRepository tenenciaRepository,
                       TenenciaSistemaRepository tenenciaSistemaRepository,
                       ProductorService productorService,
                       SindicatoService sindicatoService) {
        this.loteRepository = loteRepository;
        this.tenenciaRepository = tenenciaRepository;
        this.tenenciaSistemaRepository = tenenciaSistemaRepository;
        this.productorService = productorService;
        this.sindicatoService = sindicatoService;
    }

    // ------------------------------------------------------------- consulta

    public List<LoteResponse> listar(Long productorId, Long sindicatoId) {
        List<Lote> lotes;
        if (productorId != null) {
            lotes = loteRepository.findVigentesDeProductor(productorId);
        } else if (sindicatoId != null) {
            lotes = loteRepository.findBySindicatoIdOrderByNumeroAscExtensionAsc(sindicatoId);
        } else {
            lotes = loteRepository.findAll();
        }
        return conTenenciaYSistema(lotes);
    }

    /** Los lotes del sindicato que hoy no tiene nadie. */
    public List<LoteResponse> sinTenedor(Long sindicatoId) {
        sindicatoService.buscar(sindicatoId);
        return conTenenciaYSistema(loteRepository.findSinTenedor(sindicatoId));
    }

    public LoteResponse obtener(Long id) {
        return LoteResponse.desde(buscar(id));
    }

    /** Quién tuvo el lote, del período más reciente al primero. */
    public List<TenenciaResponse> historial(Long loteId) {
        buscar(loteId);
        return tenenciaRepository.findHistorialDeLote(loteId).stream()
                .map(TenenciaResponse::deLote)
                .toList();
    }

    /** Qué lotes tuvo un productor, incluidos los que ya no tiene. */
    public List<TenenciaResponse> historialDeProductor(Long productorId) {
        productorService.buscar(productorId);
        return tenenciaRepository.findHistorialDeProductor(productorId).stream()
                .map(TenenciaResponse::deLote)
                .toList();
    }

    public List<String> numerosDuplicados(Long sindicatoId) {
        return loteRepository.findNumerosDuplicadosEnSindicato(sindicatoId);
    }

    public List<LoteResponse> conEstadoDesconocido() {
        return conTenenciaYSistema(loteRepository.findConEstadoDesconocido());
    }

    // -------------------------------------------------------------- cambios

    /**
     * Da de alta una parcela en un sindicato, y opcionalmente le abre su primer
     * período de tenencia.
     * <p>
     * El tenedor es opcional porque una parcela puede existir sin que se sepa
     * todavía quién la tiene: es preferible tenerla registrada y sin dueño a no
     * tenerla.
     */
    @Transactional
    public LoteResponse crear(LoteRequest request) {
        Sindicato sindicato = sindicatoService.buscar(request.sindicatoId());

        Lote lote = new Lote();
        lote.setSindicato(sindicato);
        aplicar(lote, request);
        loteRepository.save(lote);

        if (request.productorId() != null) {
            Productor productor = productorService.buscar(request.productorId());
            verificarMismoSindicato(productor, lote);
            verificarSinParcela(productor);
            tenenciaRepository.save(productor.tomarLote(lote, LocalDate.now()));
        }

        loteRepository.flush();
        return LoteResponse.desde(lote);
    }

    /**
     * Corrige los datos de la parcela. No toca al tenedor: cambiar de manos es
     * un traspaso, tiene fecha y motivo, y va por {@link #traspasar}.
     */
    @Transactional
    public LoteResponse actualizar(Long id, LoteRequest request) {
        Lote lote = buscar(id);
        if (!lote.getSindicato().getId().equals(request.sindicatoId())) {
            throw new ReglaNegocioException(
                    "Un lote no se muda de sindicato: la tierra no se mueve. Si el lote está "
                    + "mal ubicado, corregilo desde la base o dalo de baja y cargalo bien.");
        }
        aplicar(lote, request);
        loteRepository.flush();
        return LoteResponse.desde(lote);
    }

    /**
     * Pasa el lote a otro productor, o lo deja sin tenedor.
     * <p>
     * Cierra el período en curso el día anterior al nuevo, para que no haya dos
     * tenedores el mismo día. El anterior no se borra: queda en el historial,
     * que es justamente para lo que existe.
     */
    @Transactional
    public LoteResponse traspasar(Long loteId, TraspasoLoteRequest peticion) {
        Lote lote = buscar(loteId);
        LocalDate desde = peticion.desdeOHoy();

        TenenciaLote actual = tenenciaRepository.findByLoteIdAndVigenteIsTrue(loteId)
                .orElse(null);

        if (actual != null) {
            if (peticion.productorId() != null
                    && actual.getProductor().getId().equals(peticion.productorId())) {
                throw new ReglaNegocioException(String.format(
                        "El lote %s ya está a nombre de %s desde el %s.",
                        lote.getCodigo(), actual.getProductor().getNombreCompleto(),
                        actual.getDesde()));
            }
            if (desde.isBefore(actual.getDesde())) {
                throw new ReglaNegocioException(String.format(
                        "El traspaso empieza el %s, antes de que empezara la tenencia actual "
                        + "(%s). Corregí la fecha.", desde, actual.getDesde()));
            }
            actual.terminar(cierreDe(actual.getDesde(), desde));
            actual.setMotivo(peticion.motivo());
            if (peticion.observaciones() != null) {
                actual.setObservaciones(Textos.limpiar(peticion.observaciones()));
            }
            // Hay que vaciar la marca de vigencia antes de insertar la nueva, o
            // la clave única de la base rechaza la segunda.
            tenenciaRepository.saveAndFlush(actual);
        } else if (peticion.productorId() == null) {
            throw new ReglaNegocioException(
                    "El lote " + lote.getCodigo() + " ya está sin tenedor.");
        }

        if (peticion.productorId() != null) {
            Productor productor = productorService.buscar(peticion.productorId());
            verificarMismoSindicato(productor, lote);
            verificarSinParcela(productor);

            TenenciaLote nueva = productor.tomarLote(lote, desde);
            nueva.setMotivo(peticion.motivo());
            nueva.setObservaciones(Textos.limpiar(peticion.observaciones()));
            tenenciaRepository.save(nueva);
        }

        loteRepository.flush();
        return LoteResponse.desde(buscar(loteId));
    }

    /** Marca o mueve el punto de la parcela en el mapa. */
    @Transactional
    public LoteResponse marcarUbicacion(Long id, UbicacionRequest peticion) {
        Lote lote = buscar(id);
        lote.marcarUbicacion(peticion.latitud(), peticion.longitud());
        loteRepository.flush();
        return LoteResponse.desde(lote);
    }

    /** Quita el punto. La parcela sigue existiendo, solo deja de estar ubicada. */
    @Transactional
    public LoteResponse borrarUbicacion(Long id) {
        Lote lote = buscar(id);
        lote.borrarUbicacion();
        loteRepository.flush();
        return LoteResponse.desde(lote);
    }

    /**
     * Los lotes de un sindicato que ya tienen punto, para dibujarlos juntos.
     * <p>
     * El filtro va en la consulta y no acá: pedir todas las parcelas para
     * descartar la mayoría en memoria sería trabajo de más en cada apertura del
     * mapa, y un sindicato puede tener cientos.
     */
    public List<LoteResponse> conUbicacion(Long sindicatoId) {
        sindicatoService.buscar(sindicatoId);
        return conTenenciaYSistema(loteRepository.findConUbicacion(sindicatoId));
    }

    @Transactional
    public void eliminar(Long id) {
        Lote lote = buscar(id);
        if (tenenciaSistemaRepository.findByLoteIdAndVigenteIsTrue(id).isPresent()) {
            throw new ReglaNegocioException("El lote " + lote.getCodigo() + " tiene un sistema "
                    + "instalado. Trasladalo a otro lote antes de dar de baja la parcela.");
        }
        loteRepository.delete(lote);
    }

    // --------------------------------------------------------------- reglas

    /**
     * El tenedor tiene que ser del mismo sindicato que la tierra.
     * <p>
     * No es un capricho: el padrón se organiza por sindicato, y un lote a
     * nombre de alguien de otra base no aparecería en ninguna nómina. Cuando
     * alguien de afuera compra, primero se lo pasa al sindicato donde está la
     * parcela.
     */
    private void verificarMismoSindicato(Productor productor, Lote lote) {
        if (!productor.getSindicato().getId().equals(lote.getSindicato().getId())) {
            throw new ReglaNegocioException(String.format(
                    "%s es del sindicato %s y el lote %s está en %s. Para que pueda tenerlo, "
                    + "primero hay que pasarlo a ese sindicato.",
                    productor.getNombreCompleto(), productor.getSindicato().getNombre(),
                    lote.getCodigo(), lote.getSindicato().getNombre()));
        }
        if (!productor.isEstado()) {
            throw new ReglaNegocioException(productor.getNombreCompleto()
                    + " está deshabilitado; habilitalo antes de darle un lote.");
        }
    }

    /**
     * Nadie puede tener dos parcelas a su nombre al mismo tiempo.
     * <p>
     * Es una regla del padrón, no una limitación técnica: la afiliación va
     * atada a una parcela, y con dos la persona contaría dos veces en las
     * nóminas y en los cupos. Quien compra otra parcela primero suelta la que
     * tenía.
     * <p>
     * Se comprueba sobre las tenencias vigentes, no sobre el historial: haber
     * tenido tierra antes no impide recibir otra hoy.
     */
    private void verificarSinParcela(Productor productor) {
        long cuantas = tenenciaRepository.countByProductorIdAndVigenteIsTrue(productor.getId());
        if (cuantas > 0) {
            throw new ReglaNegocioException(String.format(
                    "%s ya tiene una parcela a su nombre, y nadie puede tener dos. "
                    + "Traspasá la que tiene antes de darle esta.",
                    productor.getNombreCompleto()));
        }
    }

    /**
     * Qué día cerrar el período anterior cuando empieza uno nuevo.
     * <p>
     * Normalmente el día previo, para que no haya dos tenedores el mismo día.
     * Pero si el traspaso ocurre <b>el mismo día</b> en que empezó la tenencia
     * —vender lo que se acaba de recibir, o más común, corregir una carga
     * equivocada al rato— el día previo cae antes del inicio y el período
     * quedaría terminando antes de empezar. En ese caso se cierra el mismo día
     * en que abrió: duró un día, que es la verdad.
     */
    static LocalDate cierreDe(LocalDate inicioActual, LocalDate inicioNuevo) {
        LocalDate previo = inicioNuevo.minusDays(1);
        return previo.isBefore(inicioActual) ? inicioActual : previo;
    }

    private void aplicar(Lote lote, LoteRequest request) {
        lote.setNumero(Textos.limpiar(request.numero()));
        lote.setExtension(ExtensionLote.desde(request.extension()));
        lote.setEstadoOriginal(Textos.limpiar(request.estado()));
        lote.setEstadoLote(EstadoLote.desde(request.estado()));
        lote.setMercado(Mercado.desde(request.mercado()));
        lote.setSuperficie(request.superficie());
    }

    // ----------------------------------------------------------- auxiliares

    /**
     * Resuelve el tenedor y el sistema de cada lote en <b>dos consultas</b>, no
     * dos por fila.
     * <p>
     * Un sindicato puede tener cientos de parcelas; recorrer las colecciones de
     * cada una sería la diferencia entre una lista que carga y una que no.
     */
    private List<LoteResponse> conTenenciaYSistema(List<Lote> lotes) {
        if (lotes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = lotes.stream().map(Lote::getId).toList();

        Map<Long, TenenciaLote> tenedores = new HashMap<>();
        for (Long id : ids) {
            tenenciaRepository.findByLoteIdAndVigenteIsTrue(id)
                    .ifPresent(t -> tenedores.put(id, t));
        }

        Map<Long, TenenciaSistema> sistemas = new HashMap<>();
        for (TenenciaSistema t : tenenciaSistemaRepository.findVigentesDeLotes(ids)) {
            sistemas.put(t.getLote().getId(), t);
        }

        return lotes.stream()
                .map(l -> LoteResponse.desde(l, tenedores.get(l.getId()),
                        sistemas.get(l.getId())))
                .toList();
    }

    Lote buscar(Long id) {
        return loteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("lote", id));
    }
}
