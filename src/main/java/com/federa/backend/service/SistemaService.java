package com.federa.backend.service;

import com.federa.backend.dto.SistemaResponse;
import com.federa.backend.dto.TenenciaResponse;
import com.federa.backend.dto.TraspasoLoteRequest;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Sistema;
import com.federa.backend.model.TenenciaSistema;
import com.federa.backend.repository.SistemaRepository;
import com.federa.backend.repository.TenenciaSistemaRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Sistemas y su traslado entre lotes.
 * <p>
 * Funciona igual que la tenencia de un lote, con los papeles cambiados: acá lo
 * que se mueve es el sistema y el lugar es el lote. Y las reglas son dos, las
 * dos garantizadas por claves únicas de la base: un sistema está en un solo
 * lote a la vez, y un lote tiene a lo sumo un sistema.
 */
@Service
@Transactional(readOnly = true)
public class SistemaService {

    private final SistemaRepository sistemaRepository;
    private final TenenciaSistemaRepository tenenciaRepository;
    private final LoteService loteService;

    public SistemaService(SistemaRepository sistemaRepository,
                          TenenciaSistemaRepository tenenciaRepository,
                          LoteService loteService) {
        this.sistemaRepository = sistemaRepository;
        this.tenenciaRepository = tenenciaRepository;
        this.loteService = loteService;
    }

    // ------------------------------------------------------------- consulta

    /**
     * @param soloDisponibles solo los que no están en ningún lote
     * @param sindicatoId     solo los instalados en lotes de ese sindicato
     */
    public List<SistemaResponse> listar(boolean soloDisponibles, Long sindicatoId) {
        List<Sistema> sistemas;
        if (soloDisponibles) {
            sistemas = sistemaRepository.findDisponibles();
        } else if (sindicatoId != null) {
            sistemas = sistemaRepository.findEnSindicato(sindicatoId);
        } else {
            sistemas = sistemaRepository.findAllByOrderByCodigoAsc();
        }
        return sistemas.stream().map(this::conUbicacion).toList();
    }

    public SistemaResponse obtener(Long id) {
        return conUbicacion(buscar(id));
    }

    /** Por dónde pasó el sistema, del período más reciente al primero. */
    public List<TenenciaResponse> historial(Long sistemaId) {
        buscar(sistemaId);
        return tenenciaRepository.findHistorialDeSistema(sistemaId).stream()
                .map(TenenciaResponse::deSistema)
                .toList();
    }

    /** Qué sistemas pasaron por un lote. */
    public List<TenenciaResponse> historialDeLote(Long loteId) {
        loteService.buscar(loteId);
        return tenenciaRepository.findHistorialDeLote(loteId).stream()
                .map(TenenciaResponse::deSistema)
                .toList();
    }

    // -------------------------------------------------------------- cambios

    @Transactional
    public SistemaResponse crear(SistemaResponse.Peticion peticion) {
        String codigo = Textos.limpiar(peticion.codigo());
        verificarCodigoLibre(codigo, null);

        Sistema sistema = new Sistema();
        sistema.setCodigo(codigo);
        sistema.setDescripcion(Textos.limpiar(peticion.descripcion()));
        return conUbicacion(sistemaRepository.save(sistema));
    }

    @Transactional
    public SistemaResponse actualizar(Long id, SistemaResponse.Peticion peticion) {
        Sistema sistema = buscar(id);
        String codigo = Textos.limpiar(peticion.codigo());
        verificarCodigoLibre(codigo, id);

        sistema.setCodigo(codigo);
        sistema.setDescripcion(Textos.limpiar(peticion.descripcion()));
        sistemaRepository.flush();
        return conUbicacion(sistema);
    }

    /**
     * Instala el sistema en un lote, o lo retira.
     * <p>
     * Si el lote ya tenía otro sistema, se rechaza en vez de reemplazarlo en
     * silencio: sacar un sistema es un hecho con fecha y motivo propios, y
     * dejarlo implícito perdería esa constancia.
     */
    @Transactional
    public SistemaResponse trasladar(Long sistemaId, Long loteId,
                                     TraspasoLoteRequest peticion) {
        Sistema sistema = buscar(sistemaId);
        LocalDate desde = peticion.desdeOHoy();

        TenenciaSistema actual = tenenciaRepository
                .findBySistemaIdAndVigenteIsTrue(sistemaId).orElse(null);

        if (actual != null) {
            if (loteId != null && actual.getLote().getId().equals(loteId)) {
                throw new ReglaNegocioException(String.format(
                        "El sistema %s ya está en el lote %s desde el %s.",
                        sistema.getCodigo(), actual.getLote().getCodigo(), actual.getDesde()));
            }
            if (desde.isBefore(actual.getDesde())) {
                throw new ReglaNegocioException(String.format(
                        "El traslado empieza el %s, antes de que el sistema llegara al lote "
                        + "actual (%s). Corregí la fecha.", desde, actual.getDesde()));
            }
            // Mismo cálculo que en la tenencia de un lote: si el traslado es el
            // mismo día en que llegó, el período se cierra ese día en vez de
            // retroceder antes de su propio inicio.
            actual.terminar(LoteService.cierreDe(actual.getDesde(), desde));
            actual.setMotivo(peticion.motivo());
            if (peticion.observaciones() != null) {
                actual.setObservaciones(Textos.limpiar(peticion.observaciones()));
            }
            tenenciaRepository.saveAndFlush(actual);
        } else if (loteId == null) {
            throw new ReglaNegocioException(
                    "El sistema " + sistema.getCodigo() + " no está en ningún lote.");
        }

        if (loteId != null) {
            Lote lote = loteService.buscar(loteId);
            tenenciaRepository.findByLoteIdAndVigenteIsTrue(loteId).ifPresent(ocupado -> {
                throw new ReglaNegocioException(String.format(
                        "El lote %s ya tiene el sistema %s desde el %s. Retiralo primero: un "
                        + "lote lleva un solo sistema.",
                        lote.getCodigo(), ocupado.getSistema().getCodigo(), ocupado.getDesde()));
            });

            TenenciaSistema nueva = new TenenciaSistema();
            nueva.setSistema(sistema);
            nueva.setLote(lote);
            nueva.iniciar(desde);
            nueva.setMotivo(peticion.motivo());
            nueva.setObservaciones(Textos.limpiar(peticion.observaciones()));
            tenenciaRepository.save(nueva);
            sistema.getTenencias().add(nueva);
        }

        tenenciaRepository.flush();
        return conUbicacion(buscar(sistemaId));
    }

    @Transactional
    public void eliminar(Long id) {
        Sistema sistema = buscar(id);
        if (tenenciaRepository.findBySistemaIdAndVigenteIsTrue(id).isPresent()) {
            throw new ReglaNegocioException("El sistema " + sistema.getCodigo() + " está "
                    + "instalado en un lote. Retiralo antes de darlo de baja.");
        }
        sistemaRepository.delete(sistema);
    }

    // ----------------------------------------------------------- auxiliares

    private void verificarCodigoLibre(String codigo, Long idActual) {
        sistemaRepository.findByCodigoIgnoreCase(codigo)
                .filter(otro -> !otro.getId().equals(idActual))
                .ifPresent(otro -> {
                    throw new ReglaNegocioException(
                            "Ya hay un sistema con el código " + codigo + ".");
                });
    }

    private SistemaResponse conUbicacion(Sistema sistema) {
        return SistemaResponse.desde(sistema,
                tenenciaRepository.findBySistemaIdAndVigenteIsTrue(sistema.getId())
                        .orElse(null));
    }

    Sistema buscar(Long id) {
        return sistemaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("sistema", id));
    }
}
