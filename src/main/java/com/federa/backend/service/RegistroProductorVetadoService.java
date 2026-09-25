package com.federa.backend.service;

import com.federa.backend.dto.ProductorRequest;
import com.federa.backend.dto.RegistrarProductorVetadoRequest;
import com.federa.backend.dto.VetoRequest;
import com.federa.backend.dto.VetoResponse;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.seguridad.AlcanceCentral;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra y veta en una sola transacción: nunca queda un alta a medias. */
@Service
public class RegistroProductorVetadoService {
    private final ProductorService productores;
    private final ProductorRepository repositorio;
    private final VetoService vetos;
    private final SindicatoService sindicatos;

    public RegistroProductorVetadoService(ProductorService productores,
                                          ProductorRepository repositorio,
                                          VetoService vetos,
                                          SindicatoService sindicatos) {
        this.productores = productores;
        this.repositorio = repositorio;
        this.vetos = vetos;
        this.sindicatos = sindicatos;
    }

    @Transactional
    public VetoResponse registrar(RegistrarProductorVetadoRequest request) {
        AlcanceCentral.verificarSindicato(request.sindicatoId());
        AlcanceCentral.limitar(sindicatos.buscar(request.sindicatoId()).getCentral().getId());
        String ci = Textos.limpiar(request.ci());
        if (ci == null) {
            throw new ReglaNegocioException("Hay que indicar una cédula válida para registrar el veto.");
        }
        if (repositorio.existeCedulaNormalizada(ci)) {
            throw new ReglaNegocioException(
                    "Esta cédula ya pertenece a un productor. No se creará un duplicado; "
                            + "buscalo y vetalo desde su sindicato.");
        }

        var creado = productores.crear(new ProductorRequest(
                request.nombres(), request.apellidos(), ci, null, null, null,
                false, request.sindicatoId()));
        // Al registrarlo desde vetados no se consulta SIE. Queda pendiente para
        // cuando se levante el veto y se revise su ficha.
        var productor = repositorio.findById(creado.id()).orElseThrow();
        productor.setRevisionSiePendiente(true);
        return vetos.vetar(new VetoRequest(creado.id(), null, request.motivo(), null));
    }
}
