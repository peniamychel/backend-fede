package com.federa.backend.service;

import com.federa.backend.dto.LoteRequest;
import com.federa.backend.dto.LoteResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Productor;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.model.enums.Mercado;
import com.federa.backend.repository.LoteRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LoteService {

    private final LoteRepository loteRepository;
    private final ProductorService productorService;

    public LoteService(LoteRepository loteRepository, ProductorService productorService) {
        this.loteRepository = loteRepository;
        this.productorService = productorService;
    }

    public List<LoteResponse> listar(Long productorId, Long sindicatoId) {
        List<Lote> lotes;
        if (productorId != null) {
            lotes = loteRepository.findByProductorId(productorId);
        } else if (sindicatoId != null) {
            lotes = loteRepository.findByProductorSindicatoId(sindicatoId);
        } else {
            lotes = loteRepository.findAll();
        }
        return lotes.stream().map(LoteResponse::desde).toList();
    }

    public LoteResponse obtener(Long id) {
        return LoteResponse.desde(buscar(id));
    }

    /** Números repetidos dentro de un sindicato: el hallazgo más común del padrón. */
    public List<String> numerosDuplicados(Long sindicatoId) {
        return loteRepository.findNumerosDuplicadosEnSindicato(sindicatoId);
    }

    /** Lotes cuyo estado no encajó en ninguna categoría conocida al importar. */
    public List<LoteResponse> conEstadoDesconocido() {
        return loteRepository.findConEstadoDesconocido().stream().map(LoteResponse::desde).toList();
    }

    @Transactional
    public LoteResponse crear(LoteRequest request) {
        Lote lote = new Lote();
        aplicar(lote, request);
        return LoteResponse.desde(loteRepository.save(lote));
    }

    @Transactional
    public LoteResponse actualizar(Long id, LoteRequest request) {
        Lote lote = buscar(id);
        aplicar(lote, request);
        return LoteResponse.desde(lote);
    }

    @Transactional
    public void eliminar(Long id) {
        loteRepository.delete(buscar(id));
    }

    /**
     * El estado llega como texto libre: se guarda tal cual en
     * {@code estadoOriginal} y normalizado en {@code estado}. Si la escritura
     * no se reconoce queda como {@code DESCONOCIDO} en vez de rechazar la
     * carga, para no perder el dato de origen.
     */
    private void aplicar(Lote lote, LoteRequest request) {
        Productor productor = productorService.buscar(request.productorId());
        lote.setNumero(Textos.limpiar(request.numero()));
        lote.setExtension(ExtensionLote.desde(request.extension()));
        lote.setEstadoOriginal(Textos.limpiar(request.estado()));
        lote.setEstadoLote(EstadoLote.desde(request.estado()));
        lote.setMercado(Mercado.desde(request.mercado()));
        lote.setProductor(productor);
    }

    Lote buscar(Long id) {
        return loteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("lote", id));
    }
}
