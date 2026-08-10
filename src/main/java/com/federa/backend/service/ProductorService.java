package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.almacen.TransaccionArchivos;
import com.federa.backend.dto.ProductorDetalleResponse;
import com.federa.backend.dto.ProductorRequest;
import com.federa.backend.dto.ProductorResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.TipoImagen;
import com.federa.backend.repository.ImagenCargoRepository;
import com.federa.backend.repository.ImagenProductorRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.TenenciaLoteRepository;
import com.federa.backend.util.Paginas;
import com.federa.backend.util.Textos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ProductorService {

    private final ProductorRepository productorRepository;
    private final TenenciaLoteRepository tenenciaRepository;
    private final ImagenProductorRepository imagenRepository;
    private final ImagenCargoRepository imagenCargoRepository;
    private final SindicatoService sindicatoService;
    private final AlmacenObjetos almacen;

    public ProductorService(ProductorRepository productorRepository,
                            TenenciaLoteRepository tenenciaRepository,
                            ImagenProductorRepository imagenRepository,
                            ImagenCargoRepository imagenCargoRepository,
                            SindicatoService sindicatoService,
                            AlmacenObjetos almacen) {
        this.productorRepository = productorRepository;
        this.tenenciaRepository = tenenciaRepository;
        this.imagenRepository = imagenRepository;
        this.imagenCargoRepository = imagenCargoRepository;
        this.sindicatoService = sindicatoService;
        this.almacen = almacen;
    }

    public Page<ProductorResponse> listar(Long sindicatoId, Long centralId, String texto, Pageable pageable) {
        return conImagenes(productorRepository
                .filtrar(sindicatoId, centralId, Textos.limpiar(texto),
                        Paginas.conOrdenEstable(pageable)));
    }

    public ProductorDetalleResponse obtener(Long id) {
        return ProductorDetalleResponse.desde(buscar(id));
    }

    public Page<ProductorResponse> sinFoto(Pageable pageable) {
        return conImagenes(
                productorRepository.findSinFoto(Paginas.conOrdenEstable(pageable)));
    }

    public List<String> cedulasDuplicadas() {
        return productorRepository.findCedulasDuplicadas();
    }

    public List<String> carnetsDuplicados() {
        return productorRepository.findCarnetsDuplicados();
    }

    /** Todos los productores que comparten una misma cédula. */
    public List<ProductorResponse> porCedula(String ci) {
        return conImagenes(productorRepository.findByCi(ci));
    }

    /** Todos los productores que comparten un mismo carné. */
    public List<ProductorResponse> porCarnet(String carnet) {
        return conImagenes(productorRepository.findByCarnetProductor(carnet));
    }

    @Transactional
    public ProductorResponse crear(ProductorRequest request) {
        Productor productor = new Productor();
        aplicar(productor, request);
        return ProductorResponse.desde(productorRepository.save(productor));
    }

    @Transactional
    public ProductorResponse actualizar(Long id, ProductorRequest request) {
        Productor productor = buscar(id);
        aplicar(productor, request);
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        productorRepository.flush();
        return ProductorResponse.desde(productor);
    }

    /**
     * Borra el productor con sus observaciones, imágenes y períodos de
     * tenencia. <b>Sus lotes no</b>: la tierra pertenece al sindicato y se
     * queda ahí, con o sin él.
     * <p>
     * Las filas se van por cascade, pero los archivos del almacén no: el disco
     * no sabe nada de JPA. Hay que leer sus claves antes de borrar y quitarlos
     * después de confirmar, o quedan huérfanos ocupando espacio para siempre.
     */
    @Transactional
    public void eliminar(Long id) {
        Productor productor = buscar(id);

        // Un productor con lotes a su nombre no se borra: la tierra no
        // desaparece con él, y borrarlo dejaría parcelas sin dueño y sin
        // constancia de quién las tenía. Primero se traspasan, y así el
        // historial dice a quién pasaron.
        long lotes = tenenciaRepository.countByProductorIdAndVigenteIsTrue(id);
        if (lotes > 0) {
            throw new ReglaNegocioException(String.format(
                    "%s tiene %d lote(s) a su nombre. Traspasalos primero: si lo borrás ahora, "
                    + "las parcelas quedan sin tenedor y sin registro de a quién pasaron. "
                    + "Si se fue del sindicato, también podés deshabilitarlo en vez de borrarlo.",
                    productor.getNombreCompleto(), lotes));
        }

        // Sus fotos y, además, las firmas de los cargos que haya ocupado: dos
        // orígenes distintos de archivos que apuntan a la misma persona.
        List<String> claves = new ArrayList<>(imagenRepository.findClavesPorProductor(id));
        claves.addAll(imagenCargoRepository.findClavesPorProductor(id));

        productorRepository.delete(productor);

        if (!claves.isEmpty()) {
            TransaccionArchivos.alConfirmar(() -> claves.forEach(almacen::borrar));
        }
    }

    /**
     * Aplica la corrección de nombre propuesta en la revisión: pasa
     * "Nombre x"/"Apellido x" a los campos definitivos y limpia la propuesta.
     */
    @Transactional
    public ProductorResponse confirmarCorreccionNombre(Long id) {
        Productor productor = buscar(id);
        if (productor.getNombresCorregidos() != null) {
            productor.setNombres(productor.getNombresCorregidos());
            productor.setNombresCorregidos(null);
        }
        if (productor.getApellidosCorregidos() != null) {
            productor.setApellidos(productor.getApellidosCorregidos());
            productor.setApellidosCorregidos(null);
        }
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        productorRepository.flush();
        return ProductorResponse.desde(productor);
    }

    private void aplicar(Productor productor, ProductorRequest request) {
        Sindicato sindicato = sindicatoService.buscar(request.sindicatoId());
        productor.setNombres(Textos.normalizar(request.nombres()));
        productor.setApellidos(Textos.normalizar(request.apellidos()));
        productor.setCi(Textos.limpiar(request.ci()));
        productor.setCarnetProductor(Textos.limpiar(request.carnetProductor()));
        productor.setNombresCorregidos(Textos.normalizar(request.nombresCorregidos()));
        productor.setApellidosCorregidos(Textos.normalizar(request.apellidosCorregidos()));
        productor.setFotoDescripcion(Textos.limpiar(request.fotoDescripcion()));
        productor.setMarcado(Boolean.TRUE.equals(request.marcado()));
        productor.setSindicato(sindicato);
    }

    /**
     * Habilita o deshabilita el registro.
     * <p>
     * Deshabilitar no borra: la fila queda con todas sus relaciones y se puede
     * volver a habilitar. Es la salida para lo que no se puede eliminar porque
     * tiene registros colgando.
     */
    @Transactional
    public ProductorResponse cambiarEstado(Long id, boolean estado) {
        Productor entidad = buscar(id);
        entidad.setEstado(estado);
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        productorRepository.flush();
        return ProductorResponse.desde(entidad);
    }

    Productor buscar(Long id) {
        return productorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("productor", id));
    }

    // ---------- Enriquecido con las imágenes ----------

    private Page<ProductorResponse> conImagenes(Page<Productor> pagina) {
        Map<Long, Map<TipoImagen, String>> porProductor = urlesDe(pagina.getContent());
        return pagina.map(p -> ProductorResponse.desde(
                p, porProductor.getOrDefault(p.getId(), Map.of())));
    }

    private List<ProductorResponse> conImagenes(List<Productor> productores) {
        Map<Long, Map<TipoImagen, String>> porProductor = urlesDe(productores);
        return productores.stream()
                .map(p -> ProductorResponse.desde(
                        p, porProductor.getOrDefault(p.getId(), Map.of())))
                .toList();
    }

    /**
     * URL de cada imagen de los productores de la página, en <b>una sola
     * consulta</b>.
     * <p>
     * Recorrer {@code p.getImagenes()} por fila dispararía un SELECT por
     * productor: con páginas de 25 son 25 consultas de más, y en el listado
     * completo del padrón sería inaceptable.
     */
    private Map<Long, Map<TipoImagen, String>> urlesDe(List<Productor> productores) {
        if (productores.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = productores.stream().map(Productor::getId).toList();

        Map<Long, Map<TipoImagen, String>> mapa = new HashMap<>();
        for (Object[] fila : imagenRepository.findClavesPorProductores(ids)) {
            mapa.computeIfAbsent((Long) fila[0], k -> new EnumMap<>(TipoImagen.class))
                    .put((TipoImagen) fila[1], almacen.urlPublica((String) fila[2]));
        }
        return mapa;
    }
}
