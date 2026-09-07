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
import com.federa.backend.model.TenenciaLote;
import com.federa.backend.model.enums.TipoImagen;
import com.federa.backend.model.enums.EstadoRevisionSieProductor;
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
    private final NumeradorPadron numerador;
    private final AlmacenObjetos almacen;

    public ProductorService(ProductorRepository productorRepository,
                            TenenciaLoteRepository tenenciaRepository,
                            ImagenProductorRepository imagenRepository,
                            ImagenCargoRepository imagenCargoRepository,
                            SindicatoService sindicatoService,
                            NumeradorPadron numerador,
                            AlmacenObjetos almacen) {
        this.productorRepository = productorRepository;
        this.tenenciaRepository = tenenciaRepository;
        this.imagenRepository = imagenRepository;
        this.imagenCargoRepository = imagenCargoRepository;
        this.sindicatoService = sindicatoService;
        this.numerador = numerador;
        this.almacen = almacen;
    }

    public Page<ProductorResponse> listar(Long sindicatoId, Long centralId, String texto, Pageable pageable) {
        String busqueda = Textos.normalizar(texto);
        String patron = busqueda == null ? null : "%" + busqueda + "%";
        return conImagenes(productorRepository
                .filtrar(sindicatoId, centralId, busqueda, patron,
                        Textos.patronBusqueda(busqueda),
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

    /** Todos los productores que comparten una misma cédula. */
    public List<ProductorResponse> porCedula(String ci) {
        return conImagenes(productorRepository.findByCi(ci));
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
        boolean cambioDeIdentidad = cambioDeIdentidad(productor, request);
        String identidadAnterior = identidadDe(productor);
        aplicar(productor, request);
        if (cambioDeIdentidad && productor.getRevisionSieEstado() != null) {
            marcarCorregidoManualmente(productor, identidadAnterior);
        }
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        productorRepository.flush();
        return ProductorResponse.desde(productor);
    }

    /**
     * Borra el productor con sus imágenes y sus períodos de
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
        claves.addAll(imagenCargoRepository.findClavesOriginalesPorProductor(id));

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
        boolean corrigio = productor.getNombresCorregidos() != null
                || productor.getApellidosCorregidos() != null;
        String identidadAnterior = identidadDe(productor);
        if (productor.getNombresCorregidos() != null) {
            productor.setNombres(productor.getNombresCorregidos());
            productor.setNombresCorregidos(null);
        }
        if (productor.getApellidosCorregidos() != null) {
            productor.setApellidos(productor.getApellidosCorregidos());
            productor.setApellidosCorregidos(null);
        }
        if (corrigio && productor.getRevisionSieEstado() != null) {
            marcarCorregidoManualmente(productor, identidadAnterior);
        }
        // Se fuerza el UPDATE antes de mapear: el oyente de auditoría escribe
        // updatedAt recién al grabar, y sin esto la respuesta saldría con la
        // fecha vieja aunque la base quede bien.
        productorRepository.flush();
        return ProductorResponse.desde(productor);
    }

    private void aplicar(Productor productor, ProductorRequest request) {
        Sindicato sindicato = sindicatoService.buscar(request.sindicatoId());
        // Antes de pisarlo: de dónde venía decide si conserva su número o le
        // toca uno nuevo.
        Sindicato anterior = productor.getSindicato();
        productor.setNombres(Textos.normalizarParaGuardar(request.nombres()));
        productor.setApellidos(Textos.normalizarParaGuardar(request.apellidos()));
        productor.setCi(Textos.limpiar(request.ci()));
        productor.setNombresCorregidos(Textos.normalizarParaGuardar(request.nombresCorregidos()));
        productor.setApellidosCorregidos(Textos.normalizarParaGuardar(request.apellidosCorregidos()));
        productor.setFotoDescripcion(Textos.limpiar(request.fotoDescripcion()));
        productor.setMarcado(Boolean.TRUE.equals(request.marcado()));
        productor.setSindicato(sindicato);
        numerar(productor, anterior, sindicato);
    }

    private boolean cambioDeIdentidad(Productor productor, ProductorRequest request) {
        String nombresActuales = productor.getNombresCorregidos() != null
                ? productor.getNombresCorregidos() : productor.getNombres();
        String apellidosActuales = productor.getApellidosCorregidos() != null
                ? productor.getApellidosCorregidos() : productor.getApellidos();
        String nombresNuevos = request.nombresCorregidos() != null
                ? request.nombresCorregidos() : request.nombres();
        String apellidosNuevos = request.apellidosCorregidos() != null
                ? request.apellidosCorregidos() : request.apellidos();
        return !java.util.Objects.equals(
                        Textos.normalizarParaGuardar(nombresActuales),
                        Textos.normalizarParaGuardar(nombresNuevos))
                || !java.util.Objects.equals(
                        Textos.normalizarParaGuardar(apellidosActuales),
                        Textos.normalizarParaGuardar(apellidosNuevos))
                || !java.util.Objects.equals(
                        Textos.limpiar(productor.getCi()), Textos.limpiar(request.ci()));
    }

    private void marcarCorregidoManualmente(Productor productor, String identidadAnterior) {
        productor.setRevisionSiePendiente(false);
        productor.setRevisionSieEstado(EstadoRevisionSieProductor.CORREGIDO_MANUAL);
        productor.setRevisionSieMensaje(
                "Corrección manual posterior a SIE: «" + identidadAnterior
                        + "» cambió a «" + identidadDe(productor) + "».");
        productor.setSieNombresSugeridos(null);
        productor.setSieApellidosSugeridos(null);
    }

    private String identidadDe(Productor productor) {
        String nombres = productor.getNombresCorregidos() != null
                ? productor.getNombresCorregidos() : productor.getNombres();
        String apellidos = productor.getApellidosCorregidos() != null
                ? productor.getApellidosCorregidos() : productor.getApellidos();
        String nombre = (nombres + " " + (apellidos == null ? "" : apellidos)).trim();
        String ci = Textos.limpiar(productor.getCi());
        return ci == null ? nombre : nombre + " · CI " + ci;
    }

    /**
     * Le da al productor el número que le toca dentro de su central.
     * <p>
     * Se lo numera en dos casos: cuando todavía no tiene número —el alta, y las
     * filas que quedaron sin migrar— y cuando se muda a un sindicato de otra
     * central. Lo segundo es necesario porque el número pertenece a la
     * numeración de una central: llevárselo a otra chocaría con el que allá ya
     * tiene alguien.
     * <p>
     * Cambiar de sindicato dentro de la misma central no lo toca: el código
     * sigue siendo válido y una credencial impresa sigue sirviendo.
     */
    private void numerar(Productor productor, Sindicato anterior, Sindicato nuevo) {
        Long centralNueva = nuevo.getCentral().getId();
        boolean cambioDeCentral = anterior == null
                || !centralNueva.equals(anterior.getCentral().getId());
        if (productor.getCorrelativo() != null && !cambioDeCentral) {
            return;
        }
        productor.setCorrelativo(numerador.siguiente(centralNueva));
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

    /** Marca al productor como observado durante una revision manual. */
    @Transactional
    public ProductorResponse observar(Long id, String texto) {
        String motivo = Textos.limpiar(texto);
        if (motivo == null) {
            throw new ReglaNegocioException(
                    "Escribí el motivo antes de marcar al productor como observado");
        }
        Productor entidad = buscar(id);
        entidad.setObservacionManual(motivo);
        productorRepository.flush();
        return ProductorResponse.desde(entidad);
    }

    /** Quita la observacion administrativa y vuelve a evaluar la impresion. */
    @Transactional
    public ProductorResponse quitarObservacion(Long id) {
        Productor entidad = buscar(id);
        entidad.setObservacionManual(null);
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
        Map<Long, List<TenenciaLote>> tenencias = tenenciasDe(pagina.getContent());
        return pagina.map(p -> ProductorResponse.desde(
                p, porProductor.getOrDefault(p.getId(), Map.of()),
                tenencias.getOrDefault(p.getId(), List.of())));
    }

    private List<ProductorResponse> conImagenes(List<Productor> productores) {
        Map<Long, Map<TipoImagen, String>> porProductor = urlesDe(productores);
        Map<Long, List<TenenciaLote>> tenencias = tenenciasDe(productores);
        return productores.stream()
                .map(p -> ProductorResponse.desde(
                        p, porProductor.getOrDefault(p.getId(), Map.of()),
                        tenencias.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    private Map<Long, List<TenenciaLote>> tenenciasDe(List<Productor> productores) {
        if (productores.isEmpty()) return Map.of();
        Map<Long, List<TenenciaLote>> resultado = new HashMap<>();
        for (TenenciaLote t : tenenciaRepository.findVigentesDeProductores(
                productores.stream().map(Productor::getId).toList())) {
            resultado.computeIfAbsent(t.getProductor().getId(), id -> new ArrayList<>()).add(t);
        }
        return resultado;
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
