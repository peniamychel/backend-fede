package com.federa.backend.service;

import com.federa.backend.dto.FederacionRequest;
import com.federa.backend.dto.FederacionResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Federacion;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.FederacionRepository;
import com.federa.backend.util.Textos;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FederacionService {

    private final FederacionRepository federacionRepository;
    private final CentralRepository centralRepository;

    public FederacionService(FederacionRepository federacionRepository,
                             CentralRepository centralRepository) {
        this.federacionRepository = federacionRepository;
        this.centralRepository = centralRepository;
    }

    public List<FederacionResponse> listar() {
        return federacionRepository.findAll(Sort.by("nombre")).stream()
                .map(FederacionResponse::desde)
                .toList();
    }

    public FederacionResponse obtener(Long id) {
        return FederacionResponse.desde(buscar(id));
    }

    @Transactional
    public FederacionResponse crear(FederacionRequest request) {
        String nombre = Textos.normalizar(request.nombre());
        String numero = Textos.limpiar(request.numero());
        if (federacionRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ReglaNegocioException("Ya existe una federación llamada " + nombre);
        }
        verificarNumeroLibre(numero, null);

        Federacion federacion = new Federacion();
        federacion.setNombre(nombre);
        federacion.setNumero(numero);
        return FederacionResponse.desde(federacionRepository.save(federacion));
    }

    @Transactional
    public FederacionResponse actualizar(Long id, FederacionRequest request) {
        Federacion federacion = buscar(id);
        String nombre = Textos.normalizar(request.nombre());
        String numero = Textos.limpiar(request.numero());
        federacionRepository.findByNombreIgnoreCase(nombre)
                .filter(otra -> !otra.getId().equals(id))
                .ifPresent(otra -> {
                    throw new ReglaNegocioException("Ya existe una federación llamada " + nombre);
                });
        verificarNumeroLibre(numero, id);

        federacion.setNombre(nombre);
        federacion.setNumero(numero);
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        federacionRepository.flush();
        return FederacionResponse.desde(federacion);
    }

    /**
     * El número es único entre todas las federaciones.
     * <p>
     * Se comprueba acá para poder devolver un mensaje que diga cuál es la otra.
     * La clave única de la base sigue estando: es la que cubre el caso de dos
     * altas al mismo tiempo, que esta consulta no puede ver.
     */
    private void verificarNumeroLibre(String numero, Long idActual) {
        if (numero == null) {
            return;
        }
        federacionRepository.findByNumero(numero)
                .filter(otra -> !otra.getId().equals(idActual))
                .ifPresent(otra -> {
                    throw new ReglaNegocioException(
                            "El número " + numero + " ya lo tiene la federación "
                                    + otra.getNombre());
                });
    }

    /**
     * No se permite borrar una federación que todavía tiene centrales: el
     * cascade arrastraría al padrón entero.
     */
    @Transactional
    public void eliminar(Long id) {
        Federacion federacion = buscar(id);
        long centrales = centralRepository.countByFederacionId(id);
        if (centrales > 0) {
            throw new ReglaNegocioException(
                    "La federación " + federacion.getNombre() + " tiene " + centrales
                            + " central(es); reasignálas o eliminálas antes de borrarla");
        }
        federacionRepository.delete(federacion);
    }

    /**
     * Habilita o deshabilita el registro.
     * <p>
     * Deshabilitar no borra: la fila queda con todas sus relaciones y se puede
     * volver a habilitar. Es la salida para lo que no se puede eliminar porque
     * tiene registros colgando.
     */
    @Transactional
    public FederacionResponse cambiarEstado(Long id, boolean estado) {
        Federacion entidad = buscar(id);
        entidad.setEstado(estado);
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        federacionRepository.flush();
        return FederacionResponse.desde(entidad);
    }

    Federacion buscar(Long id) {
        return federacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("federación", id));
    }
}
