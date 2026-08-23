package com.federa.backend.service;

import com.federa.backend.dto.VetoRequest;
import com.federa.backend.dto.VetoResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Reunion;
import com.federa.backend.model.Veto;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.ReunionRepository;
import com.federa.backend.repository.VetoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Vetos: quién está observado, por qué, y quién lo decidió.
 * <p>
 * Las reglas que impone este servicio no son técnicas, son de asamblea:
 * <ul>
 *   <li>La reunión tiene que estar habilitada para vetar. La mayoría de las
 *       asambleas son informativas, y ofrecer la sanción en todas invita a
 *       usarla donde no corresponde.</li>
 *   <li>El veto lo decide una reunión, y esa reunión tiene que tener su acta
 *       subida. Sin el documento, la sanción sería la palabra de quien la
 *       cargó.</li>
 *   <li>Hay que decir por qué. Un veto sin motivo no se puede revisar después,
 *       y quien lo sufre tiene derecho a saberlo.</li>
 *   <li>Levantarlo se decide en <b>otra</b> reunión, también con su acta. La
 *       misma que vetó no puede desdecirse en el mismo acto.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class VetoService {

    private final VetoRepository vetoRepository;
    private final ProductorRepository productorRepository;
    private final ReunionRepository reunionRepository;
    private final CargoRepository cargoRepository;

    public VetoService(VetoRepository vetoRepository,
                       ProductorRepository productorRepository,
                       ReunionRepository reunionRepository,
                       CargoRepository cargoRepository) {
        this.vetoRepository = vetoRepository;
        this.productorRepository = productorRepository;
        this.reunionRepository = reunionRepository;
        this.cargoRepository = cargoRepository;
    }

    /**
     * Busca vetados por cédula, código de credencial, nombre o apellido, y
     * opcionalmente dentro de un sindicato.
     */
    public List<VetoResponse> buscar(String texto, Long sindicatoId, boolean soloVigentes) {
        return vetoRepository.buscar(Textos.limpiar(texto), sindicatoId, soloVigentes)
                .stream().map(VetoResponse::desde).toList();
    }

    /** Todo lo que le pasó a una persona: lo vigente y lo ya levantado. */
    public List<VetoResponse> historialDe(Long productorId) {
        return vetoRepository.findByProductorIdOrderByDesdeDesc(productorId)
                .stream().map(VetoResponse::desde).toList();
    }

    /**
     * Todo lo que esa reunión decidió: los vetos que impuso y los que levantó.
     * <p>
     * Cuál es cuál se sabe mirando si la reunión figura como {@code reunion} o
     * como {@code reunionLevanta}.
     */
    public List<VetoResponse> deLaReunion(Long reunionId) {
        return vetoRepository.decididosEn(reunionId)
                .stream().map(VetoResponse::desde).toList();
    }

    /** El veto abierto de alguien, o null. Lo usa la credencial. */
    public Veto vigenteDe(Long productorId) {
        return vetoRepository.findByProductorIdAndVigenteIsTrue(productorId).orElse(null);
    }

    @Transactional
    public VetoResponse vetar(VetoRequest peticion) {
        Productor productor = productorRepository.findById(peticion.productorId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "productor", peticion.productorId()));
        Reunion reunion = buscarReunionConActa(peticion.reunionId());

        vetoRepository.findByProductorIdAndVigenteIsTrue(productor.getId())
                .ifPresent(abierto -> {
                    throw new ReglaNegocioException(String.format(
                            "%s ya está vetado desde el %s. Para cambiar el motivo hay que "
                            + "levantar ese veto y decidir uno nuevo.",
                            productor.getNombreCompleto(), abierto.getDesde()));
                });

        Veto veto = new Veto();
        veto.setProductor(productor);
        veto.setReunion(reunion);
        veto.setMotivo(exigirMotivo(peticion.motivo()));
        LocalDate desde = peticion.desde() == null ? reunion.getFecha() : peticion.desde();
        veto.iniciar(desde);

        dejarElCargo(productor, desde);

        return VetoResponse.desde(vetoRepository.saveAndFlush(veto));
    }

    /**
     * Lo saca de la lista.
     *
     * @param reunionId la reunión que lo decidió, que no puede ser la misma que
     *                  lo vetó y también tiene que tener su acta.
     */
    @Transactional
    public VetoResponse levantar(Long vetoId, Long reunionId, String motivo, LocalDate hasta) {
        Veto veto = vetoRepository.findById(vetoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("veto", vetoId));
        if (!veto.estaVigente()) {
            throw new ReglaNegocioException(String.format(
                    "El veto de %s ya se levantó el %s.",
                    veto.getProductor().getNombreCompleto(), veto.getHasta()));
        }

        Reunion reunion = buscarReunionConActa(reunionId);
        if (reunion.getId().equals(veto.getReunion().getId())) {
            throw new ReglaNegocioException(
                    "Sacarlo de la lista se decide en otra reunión, no en la misma que lo "
                    + "vetó.");
        }

        LocalDate fecha = hasta == null ? reunion.getFecha() : hasta;
        if (fecha.isBefore(veto.getDesde())) {
            throw new ReglaNegocioException(String.format(
                    "El veto se levantaría el %s, antes de haber empezado (%s).",
                    fecha, veto.getDesde()));
        }

        veto.levantar(reunion, exigirMotivo(motivo), fecha);
        return VetoResponse.desde(vetoRepository.saveAndFlush(veto));
    }

    /**
     * Al quedar observado deja el cargo que ocupaba, si ocupaba alguno.
     *
     * <p>Es la misma decisión: la asamblea que suspende a alguien de sus
     * derechos no lo deja al frente del sindicato. Y no se resuelve
     * rechazando el veto —«primero sacalo del cargo»—, porque el veto ya se
     * decidió en la reunión; el sistema registra lo que pasó, no lo condiciona.
     *
     * <p>El período se cierra, no se borra: queda en el historial del
     * directorio con la fecha en que terminó. Levantar el veto tampoco lo
     * devuelve al cargo; para eso hay que volver a nombrarlo, que es lo que
     * haría la organización.
     */
    private void dejarElCargo(Productor productor, LocalDate desde) {
        cargoRepository.findByProductorIdAndVigenteIsTrue(productor.getId())
                .ifPresent(cargo -> {
                    // Si el veto rige desde antes de que asumiera, el período
                    // no puede terminar antes de empezar: se cierra el mismo
                    // día en que empezó.
                    LocalDate hasta = desde.isBefore(cargo.getDesde()) ? cargo.getDesde() : desde;
                    cargo.terminar(hasta);
                    cargoRepository.saveAndFlush(cargo);
                });
    }

    /**
     * Cuántas decisiones sobre vetos tomó esa reunión, poniendo o quitando.
     * <p>
     * Lo consulta el acta antes de dejar quitar su última hoja, y la reunión
     * antes de dejar apagar sus vetos. Cuenta las dos direcciones: el acta que
     * respalda un levantamiento hace la misma falta que la que respalda un
     * veto.
     */
    public long cuantosEnLaReunion(Long reunionId) {
        return vetoRepository.decididosEn(reunionId).size();
    }

    private Reunion buscarReunionConActa(Long reunionId) {
        Reunion reunion = reunionRepository.findById(reunionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("reunión", reunionId));
        if (!reunion.isVetosHabilitados()) {
            throw new ReglaNegocioException(String.format(
                    "La reunión «%s» no está habilitada para decidir vetos. Si en esa "
                    + "asamblea se trató una sanción, habilitalo en la reunión y volvé a "
                    + "intentar.",
                    reunion.getTitulo()));
        }
        if (!reunion.isTieneActa()) {
            throw new ReglaNegocioException(String.format(
                    "La reunión «%s» no tiene el acta cargada. Subila primero: es el "
                    + "documento que respalda lo que ahí se decidió.",
                    reunion.getTitulo()));
        }
        return reunion;
    }

    private String exigirMotivo(String motivo) {
        String limpio = Textos.limpiar(motivo);
        if (limpio == null) {
            throw new ReglaNegocioException(
                    "Hay que decir el motivo, con el detalle que dé el acta.");
        }
        return limpio;
    }
}
