package com.federa.backend.service;

import com.federa.backend.dto.ObservacionRequest;
import com.federa.backend.dto.ObservacionResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Observacion;
import com.federa.backend.model.Productor;
import com.federa.backend.repository.ObservacionRepository;
import com.federa.backend.util.Paginas;
import com.federa.backend.util.Textos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ObservacionService {

    private final ObservacionRepository observacionRepository;
    private final ProductorService productorService;

    public ObservacionService(ObservacionRepository observacionRepository,
                              ProductorService productorService) {
        this.observacionRepository = observacionRepository;
        this.productorService = productorService;
    }

    public Page<ObservacionResponse> listar(Long productorId, Long sindicatoId, Long centralId,
                                            boolean soloPendientes, String texto, Pageable pageable) {
        return observacionRepository
                .filtrar(productorId, sindicatoId, centralId, soloPendientes, Textos.limpiar(texto),
                        Paginas.conOrdenEstable(pageable))
                .map(ObservacionResponse::desde);
    }

    public ObservacionResponse obtener(Long id) {
        return ObservacionResponse.desde(buscar(id));
    }

    public List<ObservacionResponse> porProductor(Long productorId) {
        return observacionRepository.findByProductorIdOrderByIdAsc(productorId).stream()
                .map(ObservacionResponse::desde).toList();
    }

    /** Total de observaciones sin resolver en todo el padrón. */
    public long pendientes() {
        return observacionRepository.countByResueltaFalse();
    }

    @Transactional
    public ObservacionResponse crear(ObservacionRequest request) {
        Productor productor = productorService.buscar(request.productorId());
        Observacion observacion = new Observacion();
        observacion.setMensaje(mensajeDe(request));
        observacion.setProductor(productor);
        return ObservacionResponse.desde(observacionRepository.save(observacion));
    }

    @Transactional
    public ObservacionResponse actualizar(Long id, ObservacionRequest request) {
        Observacion observacion = buscar(id);
        observacion.setMensaje(mensajeDe(request));
        observacion.setProductor(productorService.buscar(request.productorId()));
        return ObservacionResponse.desde(observacion);
    }

    /**
     * Solo recorta espacios. A diferencia de {@link Textos#limpiar(String)} no
     * anula el guion suelto: el mensaje es texto libre y no puede quedar nulo.
     * {@code @NotBlank} ya garantiza que traiga contenido.
     */
    private String mensajeDe(ObservacionRequest request) {
        return request.mensaje().trim().replaceAll("\\s+", " ");
    }

    @Transactional
    public ObservacionResponse resolver(Long id) {
        Observacion observacion = buscar(id);
        observacion.resolver();
        return ObservacionResponse.desde(observacion);
    }

    @Transactional
    public ObservacionResponse reabrir(Long id) {
        Observacion observacion = buscar(id);
        observacion.reabrir();
        return ObservacionResponse.desde(observacion);
    }

    @Transactional
    public void eliminar(Long id) {
        observacionRepository.delete(buscar(id));
    }

    Observacion buscar(Long id) {
        return observacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("observación", id));
    }
}
