package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.dto.InformeSindicato;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.ImagenCargo;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.LoteRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.CodigoLote;
import com.federa.backend.util.CodigoPadron;
import com.federa.backend.util.Textos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Arma el informe de un sindicato y lo entrega ya convertido en PDF.
 * <p>
 * La parte cara es juntar los datos sin caer en el problema de siempre: un
 * sindicato puede tener cientos de productores, y pedirle a cada uno sus lotes
 * serían cientos de consultas. Acá son tres en total, pase lo que pase:
 * productores, lotes y el secretario general.
 */
@Service
@Transactional(readOnly = true)
public class InformeSindicatoService {

    private static final Logger log = LoggerFactory.getLogger(InformeSindicatoService.class);

    /** Ordena respetando el español: la Ñ va después de la N, y Á junto a A. */
    private static final Collator ALFABETO = Collator.getInstance(Locale.forLanguageTag("es"));

    private final SindicatoRepository sindicatoRepository;
    private final ProductorRepository productorRepository;
    private final LoteRepository loteRepository;
    private final CargoRepository cargoRepository;
    private final AlmacenObjetos almacen;
    private final InformeSindicatoPdf generador;

    public InformeSindicatoService(SindicatoRepository sindicatoRepository,
                                   ProductorRepository productorRepository,
                                   LoteRepository loteRepository,
                                   CargoRepository cargoRepository,
                                   AlmacenObjetos almacen,
                                   InformeSindicatoPdf generador) {
        this.sindicatoRepository = sindicatoRepository;
        this.productorRepository = productorRepository;
        this.loteRepository = loteRepository;
        this.cargoRepository = cargoRepository;
        this.almacen = almacen;
        this.generador = generador;
    }

    /** El PDF y el nombre con el que conviene bajarlo. */
    public record Descarga(String nombreArchivo, byte[] contenido) {
    }

    public Descarga generar(Long sindicatoId) {
        Sindicato sindicato = sindicatoRepository.findById(sindicatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el sindicato " + sindicatoId + "."));

        InformeSindicato informe = new InformeSindicato(
                tituloDeFederacion(sindicato.getCentral().getFederacion().getNombre()),
                sindicato.getCentral().getNombre(),
                sindicato.getNombre(),
                filas(sindicatoId),
                dirigente(sindicatoId),
                LocalDate.now().getYear());

        return new Descarga(nombreArchivo(sindicato), generador.generar(informe));
    }

    // ---------------------------------------------------------------- filas

    private List<InformeSindicato.Fila> filas(Long sindicatoId) {
        Map<Long, List<String>> lotes = agrupar(
                loteRepository.findIdentificacionesPorSindicato(sindicatoId), this::codigoDeLote);
        List<Productor> productores = new ArrayList<>(
                productorRepository.findBySindicatoId(sindicatoId));
        productores.sort(Comparator
                .comparing(InformeSindicatoService::apellidosDe, ALFABETO::compare)
                .thenComparing(InformeSindicatoService::nombresDe, ALFABETO::compare));

        List<InformeSindicato.Fila> filas = new ArrayList<>(productores.size());
        int numero = 1;
        for (Productor productor : productores) {
            filas.add(new InformeSindicato.Fila(
                    numero++,
                    nombresDe(productor),
                    apellidosDe(productor),
                    texto(productor.getCi()),
                    unir(lotes.get(productor.getId())),
                    texto(CodigoPadron.de(productor))));
        }
        return filas;
    }

    /**
     * Vuelca una proyección {productorId, ...} en un mapa por productor,
     * conservando el orden en que vino de la base.
     */
    private Map<Long, List<String>> agrupar(List<Object[]> proyeccion,
                                            java.util.function.Function<Object[], String> valor) {
        Map<Long, List<String>> porProductor = new HashMap<>();
        for (Object[] fila : proyeccion) {
            String v = valor.apply(fila);
            if (v != null && !v.isBlank()) {
                porProductor.computeIfAbsent((Long) fila[0], k -> new ArrayList<>()).add(v.trim());
            }
        }
        return porProductor;
    }

    /** Identifica el lote y coloca la letra compartida junto al número: "66 A". */
    private String codigoDeLote(Object[] fila) {
        String numero = (String) fila[1];
        if (numero == null) {
            return null;
        }
        ExtensionLote extension = (ExtensionLote) fila[2];
        String letraCompartida = (String) fila[3];
        return CodigoLote.de(numero, extension, letraCompartida);
    }

    // ------------------------------------------------------------ dirigente

    /**
     * Secretario General vigente con su firma, para el pie del acta.
     * <p>
     * Si no hay dirigente, o si el archivo de la firma no está donde dice la
     * base, se devuelve lo que se pueda: el acta se imprime igual con el
     * espacio en blanco.
     */
    private InformeSindicato.Dirigente dirigente(Long sindicatoId) {
        Optional<Cargo> presidencia = cargoRepository
                .findBySindicatoIdAndCargoAndVigenteIsTrue(
                        sindicatoId, TipoCargo.SECRETARIO_GENERAL);
        if (presidencia.isEmpty()) {
            return null;
        }
        Cargo cargo = presidencia.get();
        return new InformeSindicato.Dirigente(
                cargo.getProductor().getNombreCompleto(),
                bytesDe(cargo, TipoImagenCargo.FIRMA),
                cargo.construirPieFirma());
    }

    private byte[] bytesDe(Cargo cargo, TipoImagenCargo tipo) {
        for (ImagenCargo imagen : cargo.getImagenes()) {
            if (imagen.getTipo() != tipo) {
                continue;
            }
            try {
                return almacen.leer(imagen.getClave());
            } catch (RuntimeException e) {
                log.warn("El informe del sindicato {} sigue sin la {}: no se pudo leer {}.",
                        cargo.getSindicato().getId(), tipo.getEtiqueta(), imagen.getClave(), e);
                return null;
            }
        }
        return null;
    }

    // ----------------------------------------------------------- auxiliares

    /**
     * Título de la primera línea.
     * <p>
     * Se imprime el nombre guardado tal cual, y solo se le antepone
     * "FEDERACIÓN" cuando no lo trae: así una federación registrada como
     * "CARRASCO" sale como "FEDERACIÓN CARRASCO", y si mañana se la renombra
     * con su denominación completa, el informe la respeta sin tocar código.
     */
    private String tituloDeFederacion(String nombre) {
        String normalizado = Textos.normalizar(nombre);
        if (normalizado == null) {
            return "FEDERACIÓN";
        }
        return normalizado.startsWith("FEDERACION") ? nombre.trim() : "FEDERACIÓN " + nombre.trim();
    }

    private String nombreArchivo(Sindicato sindicato) {
        return "padron-"
                + Textos.paraNombreDeArchivo(sindicato.getCentral().getNombre(), 30) + "-"
                + Textos.paraNombreDeArchivo(sindicato.getNombre(), 40) + ".pdf";
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
