package com.federa.backend.service;

import com.federa.backend.dto.EstadoFasesImpresionCentral;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Central;
import com.federa.backend.model.FaseImpresionCarnet;
import com.federa.backend.model.Productor;
import com.federa.backend.model.ProductorFaseImpresion;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.EstadoFaseImpresionCarnet;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.FaseImpresionCarnetRepository;
import com.federa.backend.repository.ProductorFaseImpresionRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.SindicatoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Abre, cierra y controla los participantes de las fases de impresión. */
@Service
@Transactional(readOnly = true)
public class FaseImpresionCarnetService {

    private final CentralRepository centralRepository;
    private final SindicatoRepository sindicatoRepository;
    private final ProductorRepository productorRepository;
    private final FaseImpresionCarnetRepository faseRepository;
    private final ProductorFaseImpresionRepository participanteRepository;

    public FaseImpresionCarnetService(
            CentralRepository centralRepository,
            SindicatoRepository sindicatoRepository,
            ProductorRepository productorRepository,
            FaseImpresionCarnetRepository faseRepository,
            ProductorFaseImpresionRepository participanteRepository) {
        this.centralRepository = centralRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.productorRepository = productorRepository;
        this.faseRepository = faseRepository;
        this.participanteRepository = participanteRepository;
    }

    public EstadoFasesImpresionCentral estado(Long centralId) {
        Central central = centralRepository.findById(centralId)
                .orElseThrow(() -> new RecursoNoEncontradoException("central", centralId));
        List<EstadoFasesImpresionCentral.Fase> historial = faseRepository
                .findByCentralIdOrderByNumeroDesc(centralId).stream()
                .map(this::resumen)
                .toList();
        EstadoFasesImpresionCentral.Fase activa = historial.stream()
                .filter(fase -> fase.estado() == EstadoFaseImpresionCarnet.ABIERTA)
                .findFirst().orElse(null);
        return new EstadoFasesImpresionCentral(
                central.getId(), central.getNombre(), activa, historial);
    }

    @Transactional
    public EstadoFasesImpresionCentral abrir(Long centralId) {
        Central central = centralRepository.findByIdParaNumerar(centralId)
                .orElseThrow(() -> new RecursoNoEncontradoException("central", centralId));
        if (faseActiva(centralId).isPresent()) {
            throw new ReglaNegocioException(
                    "La central ya tiene una fase de impresión habilitada");
        }

        Optional<FaseImpresionCarnet> anterior = faseRepository
                .findFirstByCentralIdOrderByNumeroDesc(centralId);
        int numero = Math.max(central.getUltimaFaseImpresionNumero(),
                anterior.map(FaseImpresionCarnet::getNumero).orElse(0)) + 1;
        LocalDateTime ahora = LocalDateTime.now();
        FaseImpresionCarnet fase = faseRepository.saveAndFlush(
                new FaseImpresionCarnet(central, numero, ahora));

        List<Productor> productores = productorRepository
                .findBySindicatoCentralIdOrderByApellidosAscNombresAsc(centralId);
        Map<Long, Incorporacion> incluidos = new LinkedHashMap<>();
        if (numero == 1) {
            for (Productor productor : productores) {
                boolean yaImpreso = productor.getCredencialImpresiones() > 0;
                incluidos.put(productor.getId(), new Incorporacion(
                        productor, false, !yaImpreso, yaImpreso ? 1 : 0));
            }
        } else if (anterior.isPresent()) {
            for (ProductorFaseImpresion pendiente : participanteRepository
                    .findByFaseIdAndPendienteTrue(anterior.get().getId())) {
                incluidos.put(pendiente.getProductor().getId(), new Incorporacion(
                        pendiente.getProductor(), pendiente.isReimpresion(), true, 0));
            }
        }

        for (Productor productor : productores) {
            if (!productor.isFaseImpresionPendiente()) continue;
            Incorporacion previa = incluidos.get(productor.getId());
            boolean reimpresion = productor.isReimpresionFasePendiente()
                    || (previa != null && previa.reimpresion());
            incluidos.put(productor.getId(),
                    new Incorporacion(productor, reimpresion, true, 0));
        }

        List<ProductorFaseImpresion> participantes = new ArrayList<>(incluidos.size());
        for (Incorporacion incorporacion : incluidos.values()) {
            participantes.add(new ProductorFaseImpresion(
                    fase, incorporacion.productor(), ahora, incorporacion.pendiente(),
                    incorporacion.reimpresion(), incorporacion.impresionesEnFase()));
            // La marca también alimenta el icono de los listados. Permanece
            // hasta que la cara se registra realmente como impresa; si la
            // fase se cierra antes, el productor pasa pendiente a la próxima.
            incorporacion.productor().setFaseImpresionPendiente(
                    incorporacion.pendiente());
            incorporacion.productor().setReimpresionFasePendiente(
                    incorporacion.pendiente() && incorporacion.reimpresion());
        }
        participanteRepository.saveAll(participantes);
        central.setFaseImpresionActivaNumero(numero);
        central.setUltimaFaseImpresionNumero(numero);
        productorRepository.flush();
        centralRepository.flush();
        return estado(centralId);
    }

    @Transactional
    public EstadoFasesImpresionCentral cerrar(Long centralId, Long faseId) {
        FaseImpresionCarnet fase = faseRepository.findById(faseId)
                .orElseThrow(() -> new RecursoNoEncontradoException("fase de impresión", faseId));
        if (!centralId.equals(fase.getCentral().getId())) {
            throw new ReglaNegocioException("La fase no pertenece a esta central");
        }
        if (!fase.estaAbierta()) {
            throw new ReglaNegocioException("La fase de impresión ya está cerrada");
        }
        fase.setEstado(EstadoFaseImpresionCarnet.CERRADA);
        fase.setCerradaEn(LocalDateTime.now());
        fase.getCentral().setFaseImpresionActivaNumero(null);
        faseRepository.flush();
        centralRepository.flush();
        return estado(centralId);
    }

    @Transactional
    public ProductorFaseImpresion agregarParaReimpresion(Long productorId) {
        Productor productor = productorRepository.findById(productorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("productor", productorId));
        if (productor.getCredencialImpresiones() < 1) {
            throw new ReglaNegocioException(
                    "El productor todavía no tiene un carnet impreso para reimprimir");
        }
        FaseImpresionCarnet fase = exigirActiva(
                productor.getSindicato().getCentral().getId());
        ProductorFaseImpresion participante = participanteRepository
                .findByFaseIdAndProductorId(fase.getId(), productorId)
                .orElseGet(() -> new ProductorFaseImpresion(
                        fase, productor, LocalDateTime.now(), true, true, 0));
        participante.setPendiente(true);
        participante.setReimpresion(true);
        productor.setFaseImpresionPendiente(true);
        productor.setReimpresionFasePendiente(true);
        return participanteRepository.save(participante);
    }

    /** Valida la fase y prepara dentro de ella las reimpresiones selectivas. */
    @Transactional
    public FaseImpresionCarnet prepararSeleccion(
            Long sindicatoId, List<Productor> productores, boolean permitirReimpresion) {
        Sindicato sindicato = sindicatoRepository.findById(sindicatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", sindicatoId));
        FaseImpresionCarnet fase = exigirActiva(sindicato.getCentral().getId());
        for (Productor productor : productores) {
            ProductorFaseImpresion participante = participanteRepository
                    .findByFaseIdAndProductorId(fase.getId(), productor.getId())
                    .orElse(null);
            if (participante == null) {
                if (!permitirReimpresion || productor.getCredencialImpresiones() < 1) {
                    throw new ReglaNegocioException(productor.getNombreCompleto()
                            + " no está incluido en la fase de impresión habilitada");
                }
                participante = new ProductorFaseImpresion(
                        fase, productor, LocalDateTime.now(), true, true, 0);
            } else if (!participante.isPendiente()) {
                if (!permitirReimpresion) {
                    throw new ReglaNegocioException(productor.getNombreCompleto()
                            + " ya fue impreso en esta fase");
                }
                participante.setPendiente(true);
                participante.setReimpresion(true);
            }
            participanteRepository.save(participante);
        }
        return fase;
    }

    public FaseImpresionCarnet exigirActiva(Long centralId) {
        return faseActiva(centralId).orElseThrow(() -> new ReglaNegocioException(
                "Primero habilitá una fase de impresión de carnets en Avance de impresión"));
    }

    public Optional<FaseImpresionCarnet> faseActiva(Long centralId) {
        return faseRepository.findFirstByCentralIdAndEstadoOrderByNumeroDesc(
                centralId, EstadoFaseImpresionCarnet.ABIERTA);
    }

    public Map<Long, ProductorFaseImpresion> participantesActivos(Long centralId) {
        Optional<FaseImpresionCarnet> activa = faseActiva(centralId);
        if (activa.isEmpty()) return Map.of();
        Map<Long, ProductorFaseImpresion> resultado = new LinkedHashMap<>();
        for (ProductorFaseImpresion participante
                : participanteRepository.findTodosDeFase(activa.get().getId())) {
            resultado.put(participante.getProductor().getId(), participante);
        }
        return resultado;
    }

    public ProductorFaseImpresion participante(
            FaseImpresionCarnet fase, Long productorId) {
        return participanteRepository.findByFaseIdAndProductorId(fase.getId(), productorId)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El productor no está incluido en la fase de impresión habilitada"));
    }

    @Transactional
    public ProductorFaseImpresion registrarImpresion(
            FaseImpresionCarnet fase, Productor productor) {
        ProductorFaseImpresion participante = participante(fase, productor.getId());
        if (!participante.isPendiente()) {
            throw new ReglaNegocioException(productor.getNombreCompleto()
                    + " ya fue contabilizado en esta fase");
        }
        if (productor.getCredencialImpresiones() > 0) {
            participante.setReimpresion(true);
        }
        participante.setImpresionesEnFase(participante.getImpresionesEnFase() + 1);
        participante.setPendiente(false);
        productor.setFaseImpresionPendiente(false);
        productor.setReimpresionFasePendiente(false);
        return participanteRepository.save(participante);
    }

    /** Restaura o reaplica el resultado de una tanda revisada por el operador. */
    @Transactional
    public void revisarResultado(
            FaseImpresionCarnet fase, Long productorId,
            boolean pendienteAnterior, int impresionesAnteriores,
            boolean debeContar) {
        ProductorFaseImpresion participante = participante(fase, productorId);
        participante.setPendiente(debeContar ? false : pendienteAnterior);
        participante.setImpresionesEnFase(
                debeContar ? impresionesAnteriores + 1 : impresionesAnteriores);
        participante.getProductor().setFaseImpresionPendiente(
                debeContar ? false : pendienteAnterior);
        participante.getProductor().setReimpresionFasePendiente(
                !debeContar && pendienteAnterior && participante.isReimpresion());
        participanteRepository.save(participante);
    }

    public EstadoFasesImpresionCentral.Fase resumen(FaseImpresionCarnet fase) {
        List<ProductorFaseImpresion> participantes = participanteRepository
                .findTodosDeFase(fase.getId());
        int impresos = (int) participantes.stream()
                .filter(p -> p.getImpresionesEnFase() > 0).count();
        int pendientes = (int) participantes.stream()
                .filter(ProductorFaseImpresion::isPendiente).count();
        return new EstadoFasesImpresionCentral.Fase(
                fase.getId(), fase.getNumero(), fase.getEstado(), fase.getAbiertaEn(),
                fase.getCerradaEn(), participantes.size(), impresos, pendientes);
    }

    private record Incorporacion(Productor productor, boolean reimpresion,
                                 boolean pendiente, int impresionesEnFase) {
    }
}
