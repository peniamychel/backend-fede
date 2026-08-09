package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.dto.CredencialDirigente;
import com.federa.backend.dto.CredencialProductor;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.ImagenCargo;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoImagen;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.ImagenProductorRepository;
import com.federa.backend.repository.LoteRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Arma las credenciales y las entrega en PDF.
 * <p>
 * Las dos salidas comparten los datos: una credencial suelta, y el pliego con
 * todas las de un sindicato. En el pliego el directorio se consulta una sola
 * vez —es el mismo presidente y el mismo secretario para todos— y las fotos y
 * los lotes se traen en bloque, así imprimir cien credenciales cuesta las
 * mismas consultas que imprimir una.
 */
@Service
@Transactional(readOnly = true)
public class CredencialService {

    private static final Logger log = LoggerFactory.getLogger(CredencialService.class);

    private static final Collator ALFABETO = Collator.getInstance(Locale.forLanguageTag("es"));

    /**
     * Tope del pliego. No es una limitación técnica sino de sentido común: son
     * 4.051 productores, y un PDF con todos sería inmanejable de imprimir y de
     * abrir. Se imprime por sindicato, que es como se reparten.
     */
    private static final int MAXIMO_POR_PLIEGO = 500;

    private final ProductorRepository productorRepository;
    private final SindicatoRepository sindicatoRepository;
    private final LoteRepository loteRepository;
    private final ImagenProductorRepository imagenRepository;
    private final CargoRepository cargoRepository;
    private final AlmacenObjetos almacen;
    private final CredencialProductorPdf generador;
    private final CredencialDirigentePdf generadorDirigente;
    private final GeneradorQr generadorQr;

    public CredencialService(ProductorRepository productorRepository,
                             SindicatoRepository sindicatoRepository,
                             LoteRepository loteRepository,
                             ImagenProductorRepository imagenRepository,
                             CargoRepository cargoRepository,
                             AlmacenObjetos almacen,
                             CredencialProductorPdf generador,
                             CredencialDirigentePdf generadorDirigente,
                             GeneradorQr generadorQr) {
        this.productorRepository = productorRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.loteRepository = loteRepository;
        this.imagenRepository = imagenRepository;
        this.cargoRepository = cargoRepository;
        this.almacen = almacen;
        this.generador = generador;
        this.generadorDirigente = generadorDirigente;
        this.generadorQr = generadorQr;
    }

    /** El PDF y el nombre con el que conviene bajarlo. */
    public record Descarga(String nombreArchivo, byte[] contenido) {
    }

    // ------------------------------------------------------------- una sola

    public Descarga generar(Long productorId) {
        Productor productor = productorRepository.findById(productorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("productor", productorId));
        Sindicato sindicato = productor.getSindicato();

        Directorio directorio = directorioDe(sindicato.getId());
        String lotes = unir(loteRepository.findByProductorId(productorId).stream()
                .map(Lote::getCodigo)
                .filter(codigo -> codigo != null)
                .toList());

        CredencialProductor credencial = armar(productor, sindicato, lotes,
                fotoDe(productorId), directorio);

        String nombre = "credencial-"
                + Textos.paraNombreDeArchivo(productor.getNombreCompleto(), 40) + ".pdf";
        return new Descarga(nombre, generador.generar(credencial));
    }

    // -------------------------------------------------------- del dirigente

    /**
     * Credencial de quien ocupa un cargo del directorio.
     * <p>
     * Sale en vertical, al revés que la del productor, para que se distinga de
     * un vistazo cuál es cuál sin tener que leerlas.
     * <p>
     * Se emite también para períodos ya cerrados: sirve como constancia de que
     * alguien ocupó el cargo, y el reverso dice entre qué fechas.
     */
    public Descarga generarDeCargo(Long cargoId) {
        Cargo cargo = cargoRepository.findById(cargoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("cargo", cargoId));
        Productor productor = cargo.getProductor();
        Sindicato sindicato = productor.getSindicato();

        CredencialDirigente credencial = new CredencialDirigente(
                tituloDeFederacion(sindicato.getCentral().getFederacion().getNombre()),
                cargo.getCargo().getEtiqueta(),
                cargo.getAmbito().getEtiqueta(),
                cargo.getDuenoNombre(),
                centralDe(cargo),
                nombresDe(productor),
                apellidosDe(productor),
                texto(productor.getCi()),
                periodoDe(cargo),
                fotoDe(productor.getId()),
                bytesDe(cargo, TipoImagenCargo.FIRMA),
                bytesDe(cargo, TipoImagenCargo.PIE_FIRMA),
                LocalDate.now().format(FECHA),
                productor.getCodigo(),
                generadorQr.generar(productor.getCodigo()));

        String nombre = "credencial-" + cargo.getCargo().name().toLowerCase() + "-"
                + Textos.paraNombreDeArchivo(productor.getNombreCompleto(), 40) + ".pdf";
        return new Descarga(nombre, generadorDirigente.generar(credencial));
    }

    /**
     * Central a la que pertenece el cargo, o null si no aplica.
     * <p>
     * Un cargo de la federación no tiene una central por encima, y repetir el
     * nombre de la federación en el pie de la tarjeta sería decir dos veces lo
     * mismo.
     */
    private String centralDe(Cargo cargo) {
        return switch (cargo.getAmbito()) {
            case SINDICATO -> cargo.getSindicato().getCentral().getNombre();
            case CENTRAL -> null;
            case FEDERACION -> null;
        };
    }

    private String periodoDe(Cargo cargo) {
        String desde = cargo.getDesde().format(FECHA);
        return cargo.getHasta() == null
                ? "desde el " + desde
                : desde + " — " + cargo.getHasta().format(FECHA);
    }

    // ------------------------------------------------------------- el pliego

    public Descarga generarDeSindicato(Long sindicatoId) {
        Sindicato sindicato = sindicatoRepository.findById(sindicatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", sindicatoId));

        List<Productor> productores = new ArrayList<>(
                productorRepository.findBySindicatoId(sindicatoId));
        if (productores.isEmpty()) {
            throw new ReglaNegocioException("El sindicato " + sindicato.getNombre()
                    + " no tiene productores a los que emitir credencial");
        }
        if (productores.size() > MAXIMO_POR_PLIEGO) {
            throw new ReglaNegocioException("El sindicato " + sindicato.getNombre() + " tiene "
                    + productores.size() + " productores, y el pliego admite hasta "
                    + MAXIMO_POR_PLIEGO);
        }
        productores.sort(Comparator
                .comparing(CredencialService::apellidosDe, ALFABETO::compare)
                .thenComparing(CredencialService::nombresDe, ALFABETO::compare));

        Directorio directorio = directorioDe(sindicatoId);
        Map<Long, List<String>> lotes = lotesPorProductor(sindicatoId);
        Map<Long, byte[]> fotos = fotosDe(productores.stream().map(Productor::getId).toList());

        List<CredencialProductor> credenciales = productores.stream()
                .map(p -> armar(p, sindicato, unir(lotes.get(p.getId())),
                        fotos.get(p.getId()), directorio))
                .toList();

        String nombre = "credenciales-"
                + Textos.paraNombreDeArchivo(sindicato.getNombre(), 40) + ".pdf";
        return new Descarga(nombre, generador.generarPliego(credenciales));
    }

    // ----------------------------------------------------------- armado

    /** Fecha de emisión tal como se imprime: día, mes y año. */
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CredencialProductor armar(Productor productor, Sindicato sindicato,
                                      String lotes, byte[] foto, Directorio directorio) {
        return new CredencialProductor(
                tituloDeFederacion(sindicato.getCentral().getFederacion().getNombre()),
                sindicato.getCentral().getNombre(),
                sindicato.getNombre(),
                nombresDe(productor),
                apellidosDe(productor),
                texto(productor.getCi()),
                texto(productor.getCarnetProductor()),
                lotes,
                foto,
                directorio.presidente(),
                directorio.secretario(),
                LocalDate.now().format(FECHA),
                productor.getCodigo(),
                generadorQr.generar(productor.getCodigo()));
    }

    /** Presidente y secretario en funciones, con sus imágenes ya leídas. */
    private record Directorio(CredencialProductor.Firmante presidente,
                              CredencialProductor.Firmante secretario) {
    }

    private Directorio directorioDe(Long sindicatoId) {
        return new Directorio(firmante(sindicatoId, TipoCargo.PRESIDENTE),
                firmante(sindicatoId, TipoCargo.SECRETARIO));
    }

    private CredencialProductor.Firmante firmante(Long sindicatoId, TipoCargo tipo) {
        Optional<Cargo> vigente =
                cargoRepository.findBySindicatoIdAndCargoAndVigenteIsTrue(sindicatoId, tipo);
        if (vigente.isEmpty()) {
            return null;
        }
        Cargo cargo = vigente.get();
        return new CredencialProductor.Firmante(
                cargo.getProductor().getNombreCompleto(),
                bytesDe(cargo, TipoImagenCargo.FIRMA),
                bytesDe(cargo, TipoImagenCargo.PIE_FIRMA));
    }

    private byte[] bytesDe(Cargo cargo, TipoImagenCargo tipo) {
        for (ImagenCargo imagen : cargo.getImagenes()) {
            if (imagen.getTipo() == tipo) {
                return leer(imagen.getClave());
            }
        }
        return null;
    }

    private byte[] fotoDe(Long productorId) {
        return imagenRepository.findByProductorIdAndTipo(productorId, TipoImagen.MINIATURA)
                .map(imagen -> leer(imagen.getClave()))
                .orElse(null);
    }

    private Map<Long, byte[]> fotosDe(List<Long> ids) {
        Map<Long, byte[]> fotos = new HashMap<>();
        for (Object[] fila : imagenRepository.findClavesPorProductores(ids)) {
            if (fila[1] != TipoImagen.MINIATURA) {
                continue;
            }
            byte[] contenido = leer((String) fila[2]);
            if (contenido != null) {
                fotos.put((Long) fila[0], contenido);
            }
        }
        return fotos;
    }

    /**
     * Lee un archivo del almacén, o null si no se pudo.
     * <p>
     * No se propaga el error: que falte una foto o una firma no puede impedir
     * emitir la credencial. Queda anotado en el log para que se note.
     */
    private byte[] leer(String clave) {
        try {
            return almacen.leer(clave);
        } catch (RuntimeException e) {
            log.warn("La credencial sale sin la imagen {}: no se pudo leer.", clave, e);
            return null;
        }
    }

    private Map<Long, List<String>> lotesPorProductor(Long sindicatoId) {
        Map<Long, List<String>> porProductor = new HashMap<>();
        for (Object[] fila : loteRepository.findIdentificacionesPorSindicato(sindicatoId)) {
            String numero = (String) fila[1];
            if (numero == null) {
                continue;
            }
            ExtensionLote extension = (ExtensionLote) fila[2];
            porProductor
                    .computeIfAbsent((Long) fila[0], k -> new ArrayList<>())
                    .add(extension != null ? numero + "-" + extension.name() : numero);
        }
        return porProductor;
    }

    // ------------------------------------------------------------ auxiliares

    /** Mismo criterio que el informe: se antepone "FEDERACIÓN" solo si falta. */
    private String tituloDeFederacion(String nombre) {
        String normalizado = Textos.normalizar(nombre);
        if (normalizado == null) {
            return "FEDERACIÓN";
        }
        return normalizado.startsWith("FEDERACION") ? nombre.trim() : "FEDERACIÓN " + nombre.trim();
    }

    private static String nombresDe(Productor productor) {
        return texto(productor.getNombresCorregidos() != null
                ? productor.getNombresCorregidos() : productor.getNombres());
    }

    private static String apellidosDe(Productor productor) {
        return texto(productor.getApellidosCorregidos() != null
                ? productor.getApellidosCorregidos() : productor.getApellidos());
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String unir(List<String> valores) {
        return valores == null ? "" : String.join(", ", valores);
    }
}
