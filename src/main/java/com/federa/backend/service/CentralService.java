package com.federa.backend.service;

import com.federa.backend.dto.CentralRequest;
import com.federa.backend.dto.CentralResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CentralService {

    private final CentralRepository centralRepository;
    private final SindicatoRepository sindicatoRepository;
    private final FederacionService federacionService;

    public CentralService(CentralRepository centralRepository,
                          SindicatoRepository sindicatoRepository,
                          FederacionService federacionService) {
        this.centralRepository = centralRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.federacionService = federacionService;
    }

    public List<CentralResponse> listar(Long federacionId) {
        List<Central> centrales = federacionId != null
                ? centralRepository.findByFederacionIdOrderByNombreAsc(federacionId)
                : centralRepository.findAll(Sort.by("nombre"));
        return centrales.stream().map(CentralResponse::desde).toList();
    }

    public CentralResponse obtener(Long id) {
        return CentralResponse.desde(buscar(id));
    }

    @Transactional
    public CentralResponse crear(CentralRequest request) {
        Federacion federacion = federacionService.buscar(request.federacionId());
        String nombre = Textos.normalizar(request.nombre());
        String numero = Textos.limpiar(request.numero());
        verificarNombreLibre(federacion.getId(), nombre, null);
        verificarNumeroLibre(numero, null);

        Central central = new Central();
        central.setNombre(nombre);
        central.setNumero(numero);
        central.setFederacion(federacion);
        return CentralResponse.desde(centralRepository.save(central));
    }

    @Transactional
    public CentralResponse actualizar(Long id, CentralRequest request) {
        Central central = buscar(id);
        Federacion federacion = federacionService.buscar(request.federacionId());
        String nombre = Textos.normalizar(request.nombre());
        String numero = Textos.limpiar(request.numero());
        verificarNombreLibre(federacion.getId(), nombre, id);
        verificarNumeroLibre(numero, id);

        central.setNombre(nombre);
        central.setNumero(numero);
        central.setFederacion(federacion);
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        centralRepository.flush();
        return CentralResponse.desde(central);
    }

    /**
     * No se permite borrar una central que todavía tiene sindicatos: el
     * cascade arrastraría a sus productores, lotes y observaciones. Primero hay
     * que mover o eliminar los sindicatos.
     */
    @Transactional
    public void eliminar(Long id) {
        Central central = buscar(id);
        long sindicatos = sindicatoRepository.countByCentralId(id);
        if (sindicatos > 0) {
            throw new ReglaNegocioException(
                    "La central " + central.getNombre() + " tiene " + sindicatos
                            + " sindicato(s); reasignálos o eliminálos antes de borrarla");
        }
        centralRepository.delete(central);
    }

    /** El nombre solo tiene que ser único dentro de la federación. */
    private void verificarNombreLibre(Long federacionId, String nombre, Long idActual) {
        centralRepository.findByFederacionIdAndNombreIgnoreCase(federacionId, nombre)
                .filter(otra -> !otra.getId().equals(idActual))
                .ifPresent(otra -> {
                    throw new ReglaNegocioException(
                            "La federación ya tiene una central llamada " + nombre);
                });
    }

    /**
     * El número, en cambio, es único entre todas las centrales.
     * <p>
     * Se comprueba acá para poder devolver un mensaje que diga cuál es la otra
     * central. La clave única de la base sigue estando: es la que cubre el caso
     * de dos altas al mismo tiempo, que esta consulta no puede ver.
     */
    private void verificarNumeroLibre(String numero, Long idActual) {
        if (numero == null) {
            return;
        }
        centralRepository.findByNumero(numero)
                .filter(otra -> !otra.getId().equals(idActual))
                .ifPresent(otra -> {
                    throw new ReglaNegocioException(
                            "El número " + numero + " ya lo tiene la central "
                                    + otra.getNombre());
                });
    }

    /**
     * Habilita o deshabilita el registro.
     * <p>
     * Deshabilitar no borra: la fila queda con todas sus relaciones y se puede
     * volver a habilitar. Es la salida para lo que no se puede eliminar porque
     * tiene registros colgando.
     */
    @Transactional
    public CentralResponse cambiarEstado(Long id, boolean estado) {
        Central entidad = buscar(id);
        entidad.setEstado(estado);
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        centralRepository.flush();
        return CentralResponse.desde(entidad);
    }

    Central buscar(Long id) {
        return centralRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("central", id));
    }
}
