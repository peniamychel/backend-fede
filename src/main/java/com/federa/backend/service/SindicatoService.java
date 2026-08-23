package com.federa.backend.service;

import com.federa.backend.dto.SindicatoRequest;
import com.federa.backend.dto.SindicatoResponse;
import com.federa.backend.dto.UbicacionRequest;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Central;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SindicatoService {

    private final SindicatoRepository sindicatoRepository;
    private final ProductorRepository productorRepository;
    private final CentralService centralService;
    private final NumeradorPadron numerador;

    public SindicatoService(SindicatoRepository sindicatoRepository,
                            ProductorRepository productorRepository,
                            CentralService centralService,
                            NumeradorPadron numerador) {
        this.sindicatoRepository = sindicatoRepository;
        this.productorRepository = productorRepository;
        this.centralService = centralService;
        this.numerador = numerador;
    }

    public List<SindicatoResponse> listar(Long centralId) {
        List<Sindicato> sindicatos = centralId != null
                ? sindicatoRepository.findByCentralIdOrderByNombreAsc(centralId)
                : sindicatoRepository.findAll(org.springframework.data.domain.Sort.by("nombre"));
        return sindicatos.stream().map(SindicatoResponse::desde).toList();
    }

    public SindicatoResponse obtener(Long id) {
        return SindicatoResponse.desde(buscar(id));
    }

    /**
     * Los que ya tienen la sede marcada, para dibujarlos todos en un mapa.
     * <p>
     * El filtro va en la consulta y no acá: pedir los 107 sindicatos para
     * descartar la mayoría en memoria sería trabajo de más en cada apertura del
     * mapa.
     */
    public List<SindicatoResponse> listarConUbicacion(Long centralId) {
        return sindicatoRepository.findConUbicacion(centralId).stream()
                .map(SindicatoResponse::desde)
                .toList();
    }

    /** Marca o mueve la sede del sindicato. */
    @Transactional
    public SindicatoResponse marcarUbicacion(Long id, UbicacionRequest request) {
        Sindicato sindicato = buscar(id);
        sindicato.marcarUbicacion(request.latitud(), request.longitud());
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        sindicatoRepository.flush();
        return SindicatoResponse.desde(sindicato);
    }

    /** Quita la ubicación. El sindicato sigue existiendo, solo pierde el punto. */
    @Transactional
    public SindicatoResponse borrarUbicacion(Long id) {
        Sindicato sindicato = buscar(id);
        sindicato.borrarUbicacion();
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        sindicatoRepository.flush();
        return SindicatoResponse.desde(sindicato);
    }

    @Transactional
    public SindicatoResponse crear(SindicatoRequest request) {
        Central central = centralService.buscar(request.centralId());
        String nombre = Textos.normalizar(request.nombre());
        String numero = Textos.limpiar(request.numero());
        verificarNombreLibre(central.getId(), nombre, null);
        verificarNumeroLibre(numero, null);

        Sindicato sindicato = new Sindicato();
        sindicato.setNombre(nombre);
        sindicato.setNumero(numero);
        sindicato.setCentral(central);
        return SindicatoResponse.desde(sindicatoRepository.save(sindicato));
    }

    @Transactional
    public SindicatoResponse actualizar(Long id, SindicatoRequest request) {
        Sindicato sindicato = buscar(id);
        Central central = centralService.buscar(request.centralId());
        String nombre = Textos.normalizar(request.nombre());
        String numero = Textos.limpiar(request.numero());
        verificarNombreLibre(central.getId(), nombre, id);
        verificarNumeroLibre(numero, id);

        // Mudar el sindicato muda a toda su gente, y el número que llevaban
        // pertenecía a la numeración de la central que dejan.
        boolean cambioDeCentral = !central.getId().equals(sindicato.getCentral().getId());

        sindicato.setNombre(nombre);
        sindicato.setNumero(numero);
        sindicato.setCentral(central);
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        sindicatoRepository.flush();
        if (cambioDeCentral) {
            numerador.renumerar(sindicato.getId(), central.getId());
        }
        return SindicatoResponse.desde(sindicato);
    }

    @Transactional
    public void eliminar(Long id) {
        Sindicato sindicato = buscar(id);
        long productores = productorRepository.countBySindicatoId(id);
        if (productores > 0) {
            throw new ReglaNegocioException(
                    "El sindicato " + sindicato.getNombre() + " tiene " + productores
                            + " productor(es); reasignálos o eliminálos antes de borrarlo");
        }
        sindicatoRepository.delete(sindicato);
    }

    /**
     * El nombre solo tiene que ser único dentro de la central: en el padrón hay
     * sindicatos homónimos en centrales distintas y eso es válido.
     */
    private void verificarNombreLibre(Long centralId, String nombre, Long idActual) {
        sindicatoRepository.findByCentralIdAndNombreIgnoreCase(centralId, nombre)
                .filter(otro -> !otro.getId().equals(idActual))
                .ifPresent(otro -> {
                    throw new ReglaNegocioException(
                            "La central ya tiene un sindicato llamado " + nombre);
                });
    }

    /**
     * El número sí es único entre todos los sindicatos, no solo dentro de la
     * central: lo asigna la federación.
     * <p>
     * Se comprueba acá para nombrar al otro sindicato en el mensaje. La clave
     * única {@code uk_sindicato_numero} queda igual como respaldo, para el caso
     * de dos altas simultáneas que esta consulta no alcanza a ver.
     */
    private void verificarNumeroLibre(String numero, Long idActual) {
        if (numero == null) {
            return;
        }
        sindicatoRepository.findByNumero(numero)
                .filter(otro -> !otro.getId().equals(idActual))
                .ifPresent(otro -> {
                    throw new ReglaNegocioException(
                            "El número " + numero + " ya lo tiene el sindicato "
                                    + otro.getNombre() + ", de la central "
                                    + otro.getCentral().getNombre());
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
    public SindicatoResponse cambiarEstado(Long id, boolean estado) {
        Sindicato entidad = buscar(id);
        entidad.setEstado(estado);
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        sindicatoRepository.flush();
        return SindicatoResponse.desde(entidad);
    }

    Sindicato buscar(Long id) {
        return sindicatoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", id));
    }
}
