package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.dto.AsignarCargoRequest;
import com.federa.backend.dto.CargoResponse;
import com.federa.backend.dto.DirectorioResponse;
import com.federa.backend.dto.ProductorResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.ImagenCargoRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.VetoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Directorios de los tres niveles: sindicato, central y federación.
 * <p>
 * El mecanismo es el mismo en los tres y por eso vive en una sola clase; lo que
 * cambia es de dónde cuelga el cargo, qué cargos admite el nivel y de qué
 * conjunto de productores salen los candidatos. Eso lo resuelve {@link Ambito}.
 * <p>
 * Dos reglas atraviesan todo:
 * <ul>
 *   <li>Un candidato tiene que pertenecer al nivel: al sindicato, a alguno de
 *       los sindicatos de la central, o a la federación.</li>
 *   <li>Nadie ocupa dos cargos a la vez. Se comprueba acá para poder decir cuál
 *       ocupa, y además la garantiza la clave única
 *       {@code uk_cargo_productor_vigente}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class DirectorioService {

    private final CargoRepository cargoRepository;
    private final ImagenCargoRepository imagenRepository;
    private final ProductorRepository productorRepository;
    private final AlmacenObjetos almacen;
    private final SindicatoService sindicatoService;
    private final CentralService centralService;
    private final FederacionService federacionService;
    private final ProductorService productorService;
    private final VetoRepository vetoRepository;
    private final ReglasDirectorio reglas;

    public DirectorioService(CargoRepository cargoRepository,
                             ImagenCargoRepository imagenRepository,
                             ProductorRepository productorRepository,
                             AlmacenObjetos almacen,
                             SindicatoService sindicatoService,
                             CentralService centralService,
                             FederacionService federacionService,
                             ProductorService productorService,
                             VetoRepository vetoRepository,
                             ReglasDirectorio reglas) {
        this.cargoRepository = cargoRepository;
        this.imagenRepository = imagenRepository;
        this.productorRepository = productorRepository;
        this.almacen = almacen;
        this.sindicatoService = sindicatoService;
        this.centralService = centralService;
        this.federacionService = federacionService;
        this.productorService = productorService;
        this.vetoRepository = vetoRepository;
        this.reglas = reglas;
    }

    // ------------------------------------------------------------ consulta

    /** El directorio de un nivel: un puesto por cargo, ocupado o vacante. */
    public DirectorioResponse obtener(Ambito ambito, Long id) {
        String nombre = nombreDe(ambito, id);
        List<Cargo> vigentes = vigentesDe(ambito, id);

        List<DirectorioResponse.Puesto> puestos = ambito.getCargos().stream()
                .map(tipo -> new DirectorioResponse.Puesto(
                        tipo,
                        tipo.getEtiqueta(),
                        ambito.puedeFirmar(tipo),
                        vigentes.stream()
                                .filter(c -> c.getCargo() == tipo)
                                .findFirst()
                                .map(CargoResponse::desde)
                                .orElse(null)))
                .toList();

        return new DirectorioResponse(
                ambito,
                id,
                nombre,
                selloUrlDe(ambito, id),
                reglas.permitePieFirmaImagen(ambito),
                reglas.firmaObligatoria(ambito),
                reglas.selloObligatorio(ambito),
                puestos);
    }

    public List<CargoResponse> historial(Ambito ambito, Long id) {
        nombreDe(ambito, id);
        return conImagenes(switch (ambito) {
            case SINDICATO -> cargoRepository.findHistorialDeSindicato(id);
            case CENTRAL -> cargoRepository.findHistorialDeCentral(id);
            case FEDERACION -> cargoRepository.findHistorialDeFederacion(id);
        });
    }

    public List<CargoResponse> historialDeProductor(Long productorId) {
        productorService.buscar(productorId);
        return conImagenes(cargoRepository.findHistorialDeProductor(productorId));
    }

    /**
     * Productores que pueden ocupar un cargo de este nivel.
     * <p>
     * Salen del nivel —del sindicato, de los sindicatos de la central, o de
     * toda la federación— y se descartan dos grupos: los que ya ocupan un cargo
     * en cualquier nivel, y los deshabilitados. Un registro dado de baja no
     * puede presidir nada.
     * <p>
     * El descarte se hace con una sola consulta y no preguntando por cada
     * candidato: en una federación entera serían miles.
     */
    public List<ProductorResponse> candidatos(Ambito ambito, Long id) {
        nombreDe(ambito, id);

        List<Productor> delNivel = switch (ambito) {
            case SINDICATO -> productorRepository
                    .findBySindicatoIdOrderByApellidosAscNombresAsc(id);
            case CENTRAL -> productorRepository
                    .findBySindicatoCentralIdOrderByApellidosAscNombresAsc(id);
            case FEDERACION -> productorRepository
                    .findBySindicatoCentralFederacionIdOrderByApellidosAscNombresAsc(id);
        };
        if (delNivel.isEmpty()) {
            return List.of();
        }

        Set<Long> ocupados = new HashSet<>(cargoRepository.findProductoresConCargo(
                delNivel.stream().map(Productor::getId).toList()));

        // Los observados por la asamblea tampoco: ofrecerlos sería invitar a
        // elegir a alguien que el servidor va a rechazar, y peor todavía, a
        // discutirlo en la reunión antes de descubrirlo.
        Set<Long> vetados = new HashSet<>(
                vetoRepository.buscar(null, null, true).stream()
                        .map(v -> v.getProductor().getId())
                        .toList());

        return delNivel.stream()
                .filter(Productor::isEstado)
                .filter(p -> !ocupados.contains(p.getId()))
                .filter(p -> !vetados.contains(p.getId()))
                .map(p -> ProductorResponse.desde(p, Map.of()))
                .toList();
    }

    // ------------------------------------------------------------- cambios

    /**
     * Pone a un productor en un cargo. Si había alguien, su período se cierra
     * el día anterior al nuevo.
     */
    @Transactional
    public DirectorioResponse asignar(Ambito ambito, Long id, TipoCargo cargo,
                                      AsignarCargoRequest peticion) {
        if (!ambito.admite(cargo)) {
            throw new ReglaNegocioException(String.format(
                    "El directorio de %s no tiene el cargo de %s. Tiene: %s.",
                    ambito.getEtiqueta().toLowerCase(),
                    cargo.getEtiqueta().toLowerCase(),
                    ambito.getCargos().stream().map(TipoCargo::getEtiqueta).toList()));
        }

        String nombre = nombreDe(ambito, id);
        Productor productor = productorService.buscar(peticion.productorId());
        LocalDate desde = peticion.desdeOHoy();

        verificarPertenencia(ambito, id, nombre, productor);
        verificarSinVeto(productor);
        verificarSinOtroCargo(productor, ambito, cargo, id);

        Cargo actual = vigenteDe(ambito, id, cargo).orElse(null);
        if (actual != null) {
            if (desde.isBefore(actual.getDesde())) {
                throw new ReglaNegocioException(String.format(
                        "El nuevo período empieza el %s, antes de que empezara el actual (%s). "
                        + "Corregí la fecha o cerrá el período anterior primero.",
                        desde, actual.getDesde()));
            }
            // Se cierra el día anterior para que los períodos no se pisen: dos
            // presidentes el mismo día no es algo que quiera leer nadie después.
            //
            // Salvo que el relevo sea el mismo día en que asumió, que pasa al
            // corregir una asignación equivocada: ahí el día anterior caería
            // antes de su propio inicio y el período quedaría terminando antes
            // de empezar.
            actual.terminar(LoteService.cierreDe(actual.getDesde(), desde));
            // Hay que vaciar la marca de vigencia antes de insertar el nuevo, o
            // la clave única de la base rechaza el segundo.
            cargoRepository.saveAndFlush(actual);
        }

        Cargo nuevo = new Cargo();
        nuevo.setCargo(cargo);
        nuevo.setProductor(productor);
        nuevo.setPieFirma(productor.getNombreCompleto() + "\n"
                + cargo.getEtiqueta().toUpperCase());
        colgar(nuevo, ambito, id);
        nuevo.iniciar(desde);
        cargoRepository.save(nuevo);

        return obtener(ambito, id);
    }

    /** Cambia el texto que se imprime debajo de la firma del período. */
    @Transactional
    public CargoResponse actualizarPieFirma(Long cargoId, String valor) {
        Cargo cargo = cargoRepository.findById(cargoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("cargo", cargoId));
        if (!cargo.getAmbito().puedeFirmar(cargo.getCargo())) {
            throw new ReglaNegocioException("El cargo de "
                    + cargo.getCargo().getEtiqueta().toLowerCase()
                    + " no lleva firma.");
        }
        String pie = valor == null ? null
                : valor.replace("\r\n", "\n").replace('\r', '\n').strip();
        cargo.setPieFirma(pie == null || pie.isBlank() ? null : pie);
        cargoRepository.flush();
        return CargoResponse.desde(cargo);
    }

    /**
     * Deja el cargo vacante sin poner a nadie.
     * <p>
     * Existe porque una renuncia sin reemplazo es una situación real, y
     * obligar a nombrar a alguien para poder registrarla falsearía el
     * historial.
     */
    @Transactional
    public DirectorioResponse terminar(Ambito ambito, Long id, TipoCargo cargo,
                                       LocalDate hasta) {
        String nombre = nombreDe(ambito, id);

        Cargo actual = vigenteDe(ambito, id, cargo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay ningún " + cargo.getEtiqueta().toLowerCase()
                        + " en funciones en " + nombre + "."));

        LocalDate fecha = hasta != null ? hasta : LocalDate.now();
        if (fecha.isBefore(actual.getDesde())) {
            throw new ReglaNegocioException(String.format(
                    "No puede terminar el %s si empezó el %s.", fecha, actual.getDesde()));
        }

        actual.terminar(fecha);
        cargoRepository.flush();
        return obtener(ambito, id);
    }

    // ---------------------------------------------------------- las reglas

    /**
     * Quien está observado por la asamblea no dirige nada.
     * <p>
     * Un veto no es una anotación administrativa: es la organización diciendo
     * que esa persona está suspendida de sus derechos mientras dure. Ponerla al
     * frente de un sindicato en ese estado sería la organización contradiciendo
     * su propia decisión.
     */
    private void verificarSinVeto(Productor productor) {
        vetoRepository.findByProductorIdAndVigenteIsTrue(productor.getId())
                .ifPresent(veto -> {
                    throw new ReglaNegocioException(String.format(
                            "%s está observado por la asamblea desde el %s (%s). Mientras el "
                            + "veto siga, no puede ocupar un cargo: primero hay que levantarlo, "
                            + "en una reunión con su acta.",
                            productor.getNombreCompleto(), veto.getDesde(), veto.getMotivo()));
                });
    }

    /** El candidato tiene que pertenecer al nivel que va a dirigir. */
    private void verificarPertenencia(Ambito ambito, Long id, String nombre,
                                      Productor productor) {
        Sindicato suyo = productor.getSindicato();
        boolean pertenece = switch (ambito) {
            case SINDICATO -> suyo.getId().equals(id);
            case CENTRAL -> suyo.getCentral().getId().equals(id);
            case FEDERACION -> suyo.getCentral().getFederacion().getId().equals(id);
        };
        if (!pertenece) {
            throw new ReglaNegocioException(String.format(
                    "%s es del sindicato %s, que no pertenece a %s; no puede ocupar su "
                    + "directorio.",
                    productor.getNombreCompleto(), suyo.getNombre(), nombre));
        }
        if (!productor.isEstado()) {
            throw new ReglaNegocioException(String.format(
                    "%s está deshabilitado; habilitalo antes de darle un cargo.",
                    productor.getNombreCompleto()));
        }
    }

    /**
     * Nadie ocupa dos cargos a la vez.
     * <p>
     * Se deja pasar un solo caso: que ya ocupe <b>ese mismo</b> puesto, porque
     * ahí el mensaje útil es otro. Todo lo demás se rechaza nombrando el cargo
     * que ya tiene, para que quien lo intenta sepa qué liberar primero.
     */
    private void verificarSinOtroCargo(Productor productor, Ambito ambito, TipoCargo cargo,
                                       Long id) {
        Optional<Cargo> ocupado = cargoRepository
                .findByProductorIdAndVigenteIsTrue(productor.getId());
        if (ocupado.isEmpty()) {
            return;
        }
        Cargo otro = ocupado.get();

        if (otro.getAmbito() == ambito && otro.getCargo() == cargo
                && otro.getDuenoId().equals(id)) {
            throw new ReglaNegocioException(String.format(
                    "%s ya es %s de %s desde el %s.",
                    productor.getNombreCompleto(), cargo.getEtiqueta().toLowerCase(),
                    otro.getDuenoNombre(), otro.getDesde()));
        }

        throw new ReglaNegocioException(String.format(
                "%s ya es %s de %s (%s) desde el %s. Nadie puede ocupar dos cargos a la vez: "
                + "terminá ese período antes de asignarle este.",
                productor.getNombreCompleto(),
                otro.getCargo().getEtiqueta().toLowerCase(),
                otro.getDuenoNombre(),
                otro.getAmbito().getEtiqueta().toLowerCase(),
                otro.getDesde()));
    }

    // ------------------------------------------------------- por cada nivel

    private String nombreDe(Ambito ambito, Long id) {
        return switch (ambito) {
            case SINDICATO -> sindicatoService.buscar(id).getNombre();
            case CENTRAL -> centralService.buscar(id).getNombre();
            case FEDERACION -> federacionService.buscar(id).getNombre();
        };
    }

    private String selloUrlDe(Ambito ambito, Long id) {
        String clave = switch (ambito) {
            case SINDICATO -> sindicatoService.buscar(id).getSelloClave();
            case CENTRAL -> centralService.buscar(id).getSelloClave();
            case FEDERACION -> federacionService.buscar(id).getSelloClave();
        };
        return clave == null ? null : almacen.urlPublica(clave);
    }

    private List<Cargo> vigentesDe(Ambito ambito, Long id) {
        return switch (ambito) {
            case SINDICATO -> cargoRepository.findBySindicatoIdAndVigenteIsTrue(id);
            case CENTRAL -> cargoRepository.findByCentralIdAndVigenteIsTrue(id);
            case FEDERACION -> cargoRepository.findByFederacionIdAndVigenteIsTrue(id);
        };
    }

    private Optional<Cargo> vigenteDe(Ambito ambito, Long id, TipoCargo cargo) {
        return switch (ambito) {
            case SINDICATO ->
                    cargoRepository.findBySindicatoIdAndCargoAndVigenteIsTrue(id, cargo);
            case CENTRAL ->
                    cargoRepository.findByCentralIdAndCargoAndVigenteIsTrue(id, cargo);
            case FEDERACION ->
                    cargoRepository.findByFederacionIdAndCargoAndVigenteIsTrue(id, cargo);
        };
    }

    private void colgar(Cargo nuevo, Ambito ambito, Long id) {
        switch (ambito) {
            case SINDICATO -> {
                Sindicato s = sindicatoService.buscar(id);
                nuevo.colgarDe(s, null, null);
            }
            case CENTRAL -> {
                Central c = centralService.buscar(id);
                nuevo.colgarDe(null, c, null);
            }
            case FEDERACION -> {
                Federacion f = federacionService.buscar(id);
                nuevo.colgarDe(null, null, f);
            }
        }
    }

    // ----------------------------------------------------------- auxiliares

    /**
     * Arma las respuestas resolviendo las URL de firma en <b>una sola
     * consulta</b>.
     * <p>
     * Recorrer {@code cargo.getImagenes()} por fila dispararía un SELECT por
     * período, y el historial de un sindicato viejo puede tener decenas.
     */
    private List<CargoResponse> conImagenes(List<Cargo> cargos) {
        if (cargos.isEmpty()) {
            return List.of();
        }
        List<Long> ids = cargos.stream().map(Cargo::getId).toList();

        Map<Long, Map<TipoImagenCargo, String>> porCargo = new HashMap<>();
        for (Object[] fila : imagenRepository.findClavesPorCargos(ids)) {
            porCargo
                    .computeIfAbsent((Long) fila[0], k -> new EnumMap<>(TipoImagenCargo.class))
                    .put((TipoImagenCargo) fila[1], almacen.urlPublica((String) fila[2]));
        }

        return cargos.stream()
                .map(c -> CargoResponse.desde(c, porCargo.getOrDefault(c.getId(), Map.of())))
                .toList();
    }
}
