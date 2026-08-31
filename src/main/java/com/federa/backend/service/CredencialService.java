package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenLocal;
import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.dto.CredencialDirigente;
import com.federa.backend.dto.CredencialPrevia;
import com.federa.backend.dto.CredencialProductor;
import com.federa.backend.dto.ProductorResponse;
import com.federa.backend.dto.VetoResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.ImagenCargo;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.Veto;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoImagen;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.ImagenProductorRepository;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arma las credenciales y las entrega en PDF.
 * <p>
 * Las dos salidas comparten los datos: una credencial suelta, y el pliego con
 * todas las de un sindicato. En el pliego la jerarquía se consulta una sola
 * vez —son los mismos tres firmantes para todos— y las fotos y
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
    private final RequisitosCredencial requisitos;
    private final VetoService vetoService;
    private final DisenoCredencialService disenoCredencialService;

    public CredencialService(ProductorRepository productorRepository,
                             SindicatoRepository sindicatoRepository,
                             LoteRepository loteRepository,
                             ImagenProductorRepository imagenRepository,
                             CargoRepository cargoRepository,
                             AlmacenObjetos almacen,
                             CredencialProductorPdf generador,
                             CredencialDirigentePdf generadorDirigente,
                             GeneradorQr generadorQr,
                             RequisitosCredencial requisitos,
                             VetoService vetoService,
                             DisenoCredencialService disenoCredencialService) {
        this.productorRepository = productorRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.loteRepository = loteRepository;
        this.imagenRepository = imagenRepository;
        this.cargoRepository = cargoRepository;
        this.almacen = almacen;
        this.generador = generador;
        this.generadorDirigente = generadorDirigente;
        this.generadorQr = generadorQr;
        this.requisitos = requisitos;
        this.vetoService = vetoService;
        this.disenoCredencialService = disenoCredencialService;
    }

    /** El PDF y el nombre con el que conviene bajarlo. */
    public record Descarga(String nombreArchivo, byte[] contenido) {
    }

    // ------------------------------------------------------------- una sola

    public Descarga generar(Long productorId) {
        return generar(productorId, CaraCredencial.COMPLETA);
    }

    public Descarga generar(Long productorId, CaraCredencial cara) {
        Productor productor = productorRepository.findById(productorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("productor", productorId));
        Sindicato sindicato = productor.getSindicato();

        // Primero el veto: si la asamblea lo observó, no importa que los datos
        // estén completos. Es una decisión, no un dato que falte.
        exigirSinVeto(productor);

        List<String> codigosLote = loteRepository.findVigentesDeProductor(productorId).stream()
                .map(lote -> CodigoLote.de(lote, productor.getLetraCodigo()))
                .filter(codigo -> codigo != null)
                .toList();

        // Se revisa antes de dibujar nada. Una credencial sale plastificada y se
        // reparte: emitirla incompleta es papel que hay que volver a imprimir.
        exigirCompleta(faltantesDe(productor, sindicato, !codigosLote.isEmpty()),
                productor.getNombreCompleto());

        Directorio directorio = directorioDe(sindicato);
        String lotes = unir(codigosLote);

        CredencialProductor credencial = armar(productor, sindicato, lotes,
                fotoDe(productorId), directorio);

        String nombre = "credencial-"
                + Textos.paraNombreDeArchivo(productor.getNombreCompleto(), 40)
                + cara.sufijo() + ".pdf";
        return new Descarga(nombre, generador.generar(credencial,
                disenoCredencialService.actual(), cara));
    }

    // ------------------------------------------------------- la vista previa

    /**
     * Lo que va a salir impreso, antes de imprimirlo, y lo que falta para poder
     * hacerlo.
     * <p>
     * Devuelve los mismos datos que usa el generador, leídos de la misma forma,
     * para que lo que se ve en pantalla sea lo que sale en la tarjeta.
     */
    public CredencialPrevia previa(Long productorId) {
        Productor productor = productorRepository.findById(productorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("productor", productorId));
        Sindicato sindicato = productor.getSindicato();

        Jerarquia jerarquia = jerarquiaDe(sindicato);

        List<String> codigosLote = loteRepository.findVigentesDeProductor(productorId).stream()
                .map(lote -> CodigoLote.de(lote, productor.getLetraCodigo()))
                .filter(codigo -> codigo != null)
                .toList();

        List<CredencialPrevia.Faltante> faltantes = new ArrayList<>(
                requisitos.deJerarquia(sindicato, jerarquia.ejecutivoFederacion(),
                        jerarquia.secretarioCentral(), jerarquia.secretarioSindicato()));
        faltantes.addAll(requisitos.delProductor(
                productor, fotoUrlDe(productorId) != null, !codigosLote.isEmpty()));

        String lotes = unir(codigosLote);

        CredencialPrevia.Bloqueo bloqueo = bloqueoDe(productor);

        return armarPrevia(productor, sindicato, jerarquia, lotes,
                fotoUrlDe(productorId), faltantes, bloqueo);
    }

    private CredencialPrevia armarPrevia(Productor productor, Sindicato sindicato,
                                         Jerarquia jerarquia, String lotes,
                                         String fotoUrl,
                                         List<CredencialPrevia.Faltante> faltantes,
                                         CredencialPrevia.Bloqueo bloqueo) {
        return new CredencialPrevia(
                productor.getId(),
                productor.getNombreCompleto(),
                tituloDeFederacion(sindicato.getCentral().getFederacion().getNombre()),
                sindicato.getCentral().getNombre(),
                sindicato.getNombre(),
                nombresDe(productor),
                apellidosDe(productor),
                texto(productor.getCi()),
                lotes,
                CodigoPadron.de(productor),
                productor.getCodigo(),
                fotoUrl,
                urlDe(sindicato.getCentral().getFederacion().getSelloClave()),
                urlDe(sindicato.getCentral().getSelloClave()),
                urlDe(sindicato.getSelloClave()),
                firmantePrevio(jerarquia.ejecutivoFederacion()),
                firmantePrevio(jerarquia.secretarioCentral()),
                firmantePrevio(jerarquia.secretarioSindicato()),
                requisitos.firmaObligatoria(Ambito.SINDICATO),
                faltantes,
                bloqueo,
                // Completa es las dos cosas: que no falte nada y que la
                // asamblea no lo tenga observado.
                faltantes.isEmpty() && bloqueo == null);
    }

    /**
     * La previa del pliego: cuántas salen y a quiénes les falta algo.
     * <p>
     * Lo del sindicato se revisa una sola vez y se antepone, porque si falta el
     * un firmante no le falta a un productor sino a los cien.
     */
    public PliegoPrevio previaDeSindicato(Long sindicatoId) {
        Sindicato sindicato = sindicatoRepository.findById(sindicatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", sindicatoId));

        List<Productor> productores = new ArrayList<>(
                productorRepository.findBySindicatoId(sindicatoId));
        productores.sort(Comparator
                .comparing(CredencialService::apellidosDe, ALFABETO::compare)
                .thenComparing(CredencialService::nombresDe, ALFABETO::compare));

        Jerarquia jerarquia = jerarquiaDe(sindicato);
        List<CredencialPrevia.Faltante> delSindicato = requisitos.deJerarquia(
                sindicato, jerarquia.ejecutivoFederacion(), jerarquia.secretarioCentral(),
                jerarquia.secretarioSindicato());

        Map<Long, String> fotos = fotoUrlesDe(productores.stream().map(Productor::getId).toList());
        Map<Long, List<String>> lotes = lotesPorProductor(sindicatoId);

        List<ProductorIncompleto> incompletos = new ArrayList<>();
        for (Productor productor : productores) {
            List<CredencialPrevia.Faltante> suyos = requisitos.delProductor(
                    productor, fotos.get(productor.getId()) != null,
                    tieneNumeroLote(lotes, productor.getId()));
            if (!suyos.isEmpty()) {
                incompletos.add(new ProductorIncompleto(
                        productor.getId(), productor.getNombreCompleto(), suyos));
            }
        }

        return new PliegoPrevio(sindicato.getId(), sindicato.getNombre(), productores.size(),
                delSindicato, incompletos,
                delSindicato.isEmpty() && incompletos.isEmpty());
    }

    /** Vista previa del pliego de un sindicato. */
    public record PliegoPrevio(Long sindicatoId, String sindicato, int productores,
                               List<CredencialPrevia.Faltante> faltantesDelSindicato,
                               List<ProductorIncompleto> incompletos,
                               boolean completa) {
    }

    /** Un productor al que le falta algo, con qué le falta. */
    public record ProductorIncompleto(Long productorId, String nombreCompleto,
                                      List<CredencialPrevia.Faltante> faltantes) {
    }

    /** Estado de la cola de impresión de un sindicato. */
    public record PanelImpresionSindicato(
            Long sindicatoId,
            String sindicato,
            int total,
            int impresos,
            int faltantesConFoto,
            int sinFoto,
            int listosParaImprimir,
            List<CredencialPrevia.Faltante> faltantesDelSindicato,
            List<CandidatoImpresion> candidatos) {
    }

    /** Un anverso todavía no impreso que tiene fotografía. */
    public record CandidatoImpresion(CredencialPrevia credencial,
                                     int impresiones,
                                     LocalDateTime ultimaImpresion,
                                     boolean seleccionable) {
    }

    /** Listas nominales usadas por el informe de entrega y datos faltantes. */
    public record EstadoNominalSindicato(
            Long sindicatoId,
            String sindicato,
            List<FilaInformeImpresion> impresos,
            List<FilaInformeImpresion> faltantesDatos) {
    }

    /** Una persona identificada en el informe nominal de impresión. */
    public record FilaInformeImpresion(
            Long productorId,
            String nombres,
            String apellidos,
            String ci,
            String lotes,
            String codigoPadron,
            int impresiones,
            LocalDateTime ultimaImpresion,
            List<String> datosFaltantes) {
    }

    /** Identificadores seleccionados en la vista previa de impresión. */
    public record SeleccionImpresion(List<Long> productorIds) {
    }

    /**
     * Resumen y candidatos del nuevo flujo masivo. Las cuatro cifras son
     * categorías excluyentes: una persona ya impresa no vuelve a contarse
     * entre quienes hoy no tienen foto.
     */
    public PanelImpresionSindicato panelImpresionSindicato(Long sindicatoId) {
        Sindicato sindicato = sindicatoRepository.findById(sindicatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", sindicatoId));
        List<Productor> productores = productoresOrdenados(sindicatoId);
        Jerarquia jerarquia = jerarquiaDe(sindicato);
        List<CredencialPrevia.Faltante> faltantesJerarquia = requisitos.deJerarquia(
                sindicato, jerarquia.ejecutivoFederacion(), jerarquia.secretarioCentral(),
                jerarquia.secretarioSindicato());
        Map<Long, String> fotos = fotoUrlesDe(productores.stream().map(Productor::getId).toList());
        Map<Long, List<String>> lotes = lotesPorProductor(sindicatoId);
        Map<Long, VetoResponse> vetos = new HashMap<>();
        for (VetoResponse veto : vetoService.buscar(null, sindicatoId, true)) {
            vetos.put(veto.productorId(), veto);
        }

        int impresos = 0;
        int faltantesConFoto = 0;
        int sinFoto = 0;
        int listos = 0;
        List<CandidatoImpresion> candidatos = new ArrayList<>();
        for (Productor productor : productores) {
            if (productor.getCredencialImpresiones() > 0) {
                impresos++;
                continue;
            }
            String fotoUrl = fotos.get(productor.getId());
            if (fotoUrl == null) {
                sinFoto++;
                continue;
            }
            faltantesConFoto++;
            List<CredencialPrevia.Faltante> faltantes = new ArrayList<>(faltantesJerarquia);
            faltantes.addAll(requisitos.delProductor(
                    productor, true, tieneNumeroLote(lotes, productor.getId())));
            CredencialPrevia.Bloqueo bloqueo = bloqueoDe(vetos.get(productor.getId()));
            boolean seleccionable = faltantes.isEmpty() && bloqueo == null;
            if (seleccionable) listos++;
            CredencialPrevia previa = armarPrevia(productor, sindicato, jerarquia,
                    unir(lotes.get(productor.getId())), fotoUrl, faltantes, bloqueo);
            candidatos.add(new CandidatoImpresion(previa,
                    productor.getCredencialImpresiones(),
                    productor.getCredencialUltimaImpresion(), seleccionable));
        }
        return new PanelImpresionSindicato(sindicato.getId(), sindicato.getNombre(),
                productores.size(), impresos, faltantesConFoto, sinFoto, listos,
                List.copyOf(faltantesJerarquia), List.copyOf(candidatos));
    }

    /**
     * Separa a quienes ya tienen al menos una impresión confirmada de quienes
     * no pudieron imprimirse porque todavía les falta algún requisito.
     * Quien está completo pero simplemente sigue en cola no aparece como dato
     * faltante: esa distinción evita convertir un pendiente normal en error.
     */
    public EstadoNominalSindicato estadoNominalSindicato(Long sindicatoId) {
        Sindicato sindicato = sindicatoRepository.findById(sindicatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", sindicatoId));
        List<Productor> productores = productoresOrdenados(sindicatoId);
        Map<Long, String> fotos = fotoUrlesDe(productores.stream().map(Productor::getId).toList());
        Map<Long, List<String>> lotes = lotesPorProductor(sindicatoId);
        List<FilaInformeImpresion> impresos = new ArrayList<>();
        List<FilaInformeImpresion> faltantesDatos = new ArrayList<>();

        for (Productor productor : productores) {
            List<String> faltantes = new ArrayList<>();
            if (productor.getCredencialImpresiones() == 0) {
                faltantes.addAll(requisitos.delProductor(
                                productor, fotos.containsKey(productor.getId()),
                                tieneNumeroLote(lotes, productor.getId())).stream()
                        .map(CredencialPrevia.Faltante::campo).toList());
            }
            FilaInformeImpresion fila = new FilaInformeImpresion(
                    productor.getId(), nombresDe(productor), apellidosDe(productor),
                    texto(productor.getCi()), unir(lotes.get(productor.getId())),
                    texto(CodigoPadron.de(productor)), productor.getCredencialImpresiones(),
                    productor.getCredencialUltimaImpresion(), List.copyOf(faltantes));
            if (productor.getCredencialImpresiones() > 0) {
                impresos.add(fila);
            } else if (!faltantes.isEmpty()) {
                faltantesDatos.add(fila);
            }
        }
        return new EstadoNominalSindicato(sindicato.getId(), sindicato.getNombre(),
                List.copyOf(impresos), List.copyOf(faltantesDatos));
    }

    /** Genera únicamente los anversos seleccionados, uno por página CR80. */
    public Descarga generarAnversosSindicato(Long sindicatoId, SeleccionImpresion seleccion) {
        Sindicato sindicato = sindicatoRepository.findById(sindicatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", sindicatoId));
        List<Productor> productores = seleccionarProductores(sindicatoId, seleccion);
        Jerarquia jerarquia = jerarquiaDe(sindicato);
        exigirCompleta(requisitos.deJerarquia(sindicato, jerarquia.ejecutivoFederacion(),
                jerarquia.secretarioCentral(), jerarquia.secretarioSindicato()),
                sindicato.getNombre());

        List<Long> ids = productores.stream().map(Productor::getId).toList();
        Map<Long, byte[]> fotos = fotosDe(ids);
        Map<Long, List<String>> lotes = lotesPorProductor(sindicatoId);
        Directorio directorio = directorioDe(sindicato);
        List<CredencialProductor> credenciales = new ArrayList<>();
        for (Productor productor : productores) {
            if (productor.getCredencialImpresiones() > 0) {
                throw new ReglaNegocioException(productor.getNombreCompleto()
                        + " ya figura como impreso; recargá la cola antes de continuar");
            }
            exigirSinVeto(productor);
            byte[] foto = fotos.get(productor.getId());
            exigirCompleta(requisitos.delProductor(
                            productor, foto != null, tieneNumeroLote(lotes, productor.getId())),
                    productor.getNombreCompleto());
            credenciales.add(armar(productor, sindicato,
                    unir(lotes.get(productor.getId())), foto, directorio));
        }
        String nombre = "credenciales-faltantes-"
                + Textos.paraNombreDeArchivo(sindicato.getNombre(), 35)
                + "-anversos.pdf";
        return new Descarga(nombre, generador.generarTarjetas(credenciales,
                disenoCredencialService.actual(), CaraCredencial.ANVERSO));
    }

    /**
     * Confirma un trabajo aceptado por Windows. Cada nueva confirmación suma
     * una impresión; nunca se llama al imprimir reversos.
     */
    @Transactional
    public PanelImpresionSindicato confirmarAnversosImpresos(
            Long sindicatoId, SeleccionImpresion seleccion) {
        List<Productor> productores = seleccionarProductores(sindicatoId, seleccion);
        LocalDateTime ahora = LocalDateTime.now();
        for (Productor productor : productores) {
            productor.setCredencialImpresiones(productor.getCredencialImpresiones() + 1);
            productor.setCredencialUltimaImpresion(ahora);
        }
        productorRepository.flush();
        return panelImpresionSindicato(sindicatoId);
    }

    /** Registra el anverso enviado desde la impresión manual de un productor. */
    @Transactional
    public ProductorResponse confirmarAnversoImpreso(Long productorId) {
        Productor productor = productorRepository.findById(productorId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "productor", productorId));
        productor.setCredencialImpresiones(productor.getCredencialImpresiones() + 1);
        productor.setCredencialUltimaImpresion(LocalDateTime.now());
        productorRepository.flush();
        return ProductorResponse.desde(productor);
    }

    /** Genera tantos reversos idénticos como tarjetas se hayan volteado. */
    public Descarga generarReversosSindicato(Long sindicatoId, int cantidad) {
        if (cantidad < 1 || cantidad > MAXIMO_POR_PLIEGO) {
            throw new ReglaNegocioException("La cantidad de reversos debe estar entre 1 y "
                    + MAXIMO_POR_PLIEGO);
        }
        Sindicato sindicato = sindicatoRepository.findById(sindicatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", sindicatoId));
        List<Productor> productores = productoresOrdenados(sindicatoId);
        if (productores.isEmpty()) {
            throw new ReglaNegocioException("El sindicato no tiene productores");
        }
        Jerarquia jerarquia = jerarquiaDe(sindicato);
        exigirCompleta(requisitos.deJerarquia(sindicato, jerarquia.ejecutivoFederacion(),
                jerarquia.secretarioCentral(), jerarquia.secretarioSindicato()),
                sindicato.getNombre());
        Productor muestra = productores.get(0);
        CredencialProductor reverso = armar(muestra, sindicato, "", null,
                directorioDe(sindicato));
        String nombre = "credenciales-"
                + Textos.paraNombreDeArchivo(sindicato.getNombre(), 35)
                + "-reversos-" + cantidad + ".pdf";
        return new Descarga(nombre, generador.generarTarjetas(
                Collections.nCopies(cantidad, reverso), disenoCredencialService.actual(),
                CaraCredencial.REVERSO));
    }

    private List<Productor> seleccionarProductores(Long sindicatoId,
                                                    SeleccionImpresion seleccion) {
        if (seleccion == null || seleccion.productorIds() == null) {
            throw new ReglaNegocioException("Seleccioná al menos un productor");
        }
        Set<Long> ids = new LinkedHashSet<>(seleccion.productorIds());
        ids.remove(null);
        if (ids.isEmpty() || ids.size() > MAXIMO_POR_PLIEGO) {
            throw new ReglaNegocioException("Seleccioná entre 1 y " + MAXIMO_POR_PLIEGO
                    + " productores");
        }
        Map<Long, Productor> encontrados = productorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Productor::getId, p -> p));
        if (encontrados.size() != ids.size()) {
            throw new ReglaNegocioException("La selección contiene un productor inexistente");
        }
        List<Productor> resultado = new ArrayList<>();
        for (Long id : ids) {
            Productor productor = encontrados.get(id);
            if (!sindicatoId.equals(productor.getSindicato().getId())) {
                throw new ReglaNegocioException(productor.getNombreCompleto()
                        + " no pertenece a este sindicato");
            }
            resultado.add(productor);
        }
        return resultado;
    }

    private List<Productor> productoresOrdenados(Long sindicatoId) {
        List<Productor> productores = new ArrayList<>(
                productorRepository.findBySindicatoId(sindicatoId));
        productores.sort(Comparator
                .comparing(CredencialService::apellidosDe, ALFABETO::compare)
                .thenComparing(CredencialService::nombresDe, ALFABETO::compare));
        return productores;
    }

    private List<CredencialPrevia.Faltante> faltantesDe(Productor productor, Sindicato sindicato,
                                                        boolean tieneNumeroLote) {
        Jerarquia jerarquia = jerarquiaDe(sindicato);
        List<CredencialPrevia.Faltante> faltantes = new ArrayList<>(requisitos.deJerarquia(
                sindicato, jerarquia.ejecutivoFederacion(), jerarquia.secretarioCentral(),
                jerarquia.secretarioSindicato()));
        faltantes.addAll(requisitos.delProductor(
                productor, fotoDe(productor.getId()) != null, tieneNumeroLote));
        return faltantes;
    }

    /**
     * Corta la emisión si falta algo, diciendo qué.
     * <p>
     * El mensaje lista los faltantes en vez de decir "faltan datos": quien
     * aprieta imprimir tiene que poder ir a arreglarlo sin adivinar.
     */
    private void exigirCompleta(List<CredencialPrevia.Faltante> faltantes, String quien) {
        if (faltantes.isEmpty()) {
            return;
        }
        String detalle = faltantes.stream()
                .map(f -> f.campo().toLowerCase())
                .collect(Collectors.joining(", "));
        throw new ReglaNegocioException("No se puede emitir la credencial de " + quien
                + ": falta " + detalle);
    }

    /**
     * Corta el pliego si algo falta.
     * <p>
     * Se nombra a los primeros y se dice cuántos más hay: una lista de ochenta
     * nombres dentro de un mensaje de error no la lee nadie, y para eso está la
     * vista previa, que los muestra todos con lo que le falta a cada uno.
     */
    private void exigirPliegoCompleto(PliegoPrevio previo) {
        if (previo.completa()) {
            return;
        }
        if (!previo.faltantesDelSindicato().isEmpty()) {
            String detalle = previo.faltantesDelSindicato().stream()
                    .map(f -> f.campo().toLowerCase())
                    .collect(Collectors.joining(", "));
            throw new ReglaNegocioException("No se pueden emitir las credenciales de "
                    + previo.sindicato() + ": falta " + detalle
                    + ", y le falta a todas por igual");
        }
        int cuantos = previo.incompletos().size();
        String nombres = previo.incompletos().stream()
                .limit(3)
                .map(ProductorIncompleto::nombreCompleto)
                .collect(Collectors.joining(", "));
        throw new ReglaNegocioException("No se pueden emitir las credenciales de "
                + previo.sindicato() + ": " + cuantos
                + (cuantos == 1 ? " productor tiene" : " productores tienen")
                + " datos incompletos (" + nombres
                + (cuantos > 3 ? ", y " + (cuantos - 3) + " más" : "")
                + "). La vista previa dice qué le falta a cada uno");
    }

    /**
     * Corta la emisión si la asamblea lo tiene observado.
     * <p>
     * El mensaje dice el motivo y cómo se destraba, porque quien aprieta
     * imprimir no tiene por qué saber que hay que convocar otra reunión.
     */
    private void exigirSinVeto(Productor productor) {
        Veto veto = vetoService.vigenteDe(productor.getId());
        if (veto == null) {
            return;
        }
        throw new ReglaNegocioException(String.format(
                "%s está observado por decisión de asamblea desde el %s y su credencial no se "
                + "emite. Motivo: %s. Para destrabarlo, otra reunión tiene que decidir sacarlo "
                + "de la lista de vetados.",
                productor.getNombreCompleto(), veto.getDesde(), veto.getMotivo()));
    }

    /** El bloqueo tal como lo muestra la vista previa, o null si no lo hay. */
    private CredencialPrevia.Bloqueo bloqueoDe(Productor productor) {
        Veto veto = vetoService.vigenteDe(productor.getId());
        if (veto == null) {
            return null;
        }
        return new CredencialPrevia.Bloqueo(
                "Observado por la asamblea",
                veto.getMotivo(),
                veto.getReunion().getTitulo(),
                veto.getDesde(),
                "Se destraba cuando otra reunión decida sacarlo de la lista de vetados, y se "
                + "suba el acta de esa reunión.");
    }

    private CredencialPrevia.Bloqueo bloqueoDe(VetoResponse veto) {
        if (veto == null) return null;
        return new CredencialPrevia.Bloqueo(
                "Observado por la asamblea",
                veto.motivo(),
                veto.reunion() == null ? "" : veto.reunion().titulo(),
                veto.desde(),
                "Se destraba cuando otra reunión decida sacarlo de la lista de vetados, y se "
                        + "suba el acta de esa reunión.");
    }

    private CredencialPrevia.Firmante firmantePrevio(Cargo cargo) {
        if (cargo == null) {
            return null;
        }
        return new CredencialPrevia.Firmante(
                cargo.getProductor().getNombreCompleto(),
                cargo.getCargo().getEtiqueta().toUpperCase(Locale.ROOT),
                cargo.getDuenoNombre(),
                urlDe(claveDe(cargo, TipoImagenCargo.FIRMA)),
                urlDe(claveDe(cargo, TipoImagenCargo.PIE_FIRMA)));
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
        return generarDeCargo(cargoId, CaraCredencial.COMPLETA);
    }

    public Descarga generarDeCargo(Long cargoId, CaraCredencial cara) {
        Cargo cargo = cargoRepository.findById(cargoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("cargo", cargoId));
        Productor productor = cargo.getProductor();
        Sindicato sindicato = productor.getSindicato();

        // Igual que la del productor: si la asamblea lo observó, no se emite.
        // Vale también para los períodos ya cerrados —esta credencial sirve de
        // constancia—, porque el papel dice que representa a la organización y
        // eso es justamente lo que el veto le quitó.
        exigirSinVeto(productor);

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
                + Textos.paraNombreDeArchivo(productor.getNombreCompleto(), 40)
                + cara.sufijo() + ".pdf";
        return new Descarga(nombre, generadorDirigente.generar(credencial, cara));
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

        // El pliego es todo o nada: se imprime a doble cara y se recorta, así
        // que una tarjeta incompleta en el medio obliga a rehacer la hoja.
        exigirPliegoCompleto(previaDeSindicato(sindicatoId));

        Directorio directorio = directorioDe(sindicato);
        Map<Long, List<String>> lotes = lotesPorProductor(sindicatoId);
        Map<Long, byte[]> fotos = fotosDe(productores.stream().map(Productor::getId).toList());

        List<CredencialProductor> credenciales = productores.stream()
                .map(p -> armar(p, sindicato, unir(lotes.get(p.getId())),
                        fotos.get(p.getId()), directorio))
                .toList();

        String nombre = "credenciales-"
                + Textos.paraNombreDeArchivo(sindicato.getNombre(), 40) + ".pdf";
        return new Descarga(nombre, generador.generarPliego(credenciales,
                disenoCredencialService.actual()));
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
                lotes,
                foto,
                directorio.selloFederacion(),
                directorio.selloCentral(),
                directorio.selloSindicato(),
                directorio.ejecutivoFederacion(),
                directorio.secretarioGeneralCentral(),
                directorio.secretarioGeneralSindicato(),
                LocalDate.now().format(FECHA),
                productor.getCodigo(),
                CodigoPadron.de(productor),
                generadorQr.generar(productor.getCodigo()));
    }

    /** Los tres niveles institucionales que componen el reverso. */
    private record Directorio(byte[] selloFederacion, byte[] selloCentral,
                              byte[] selloSindicato,
                              CredencialProductor.Firmante ejecutivoFederacion,
                              CredencialProductor.Firmante secretarioGeneralCentral,
                              CredencialProductor.Firmante secretarioGeneralSindicato) {
    }

    private Directorio directorioDe(Sindicato sindicato) {
        Jerarquia jerarquia = jerarquiaDe(sindicato);
        return new Directorio(
                leerSiExiste(sindicato.getCentral().getFederacion().getSelloClave()),
                leerSiExiste(sindicato.getCentral().getSelloClave()),
                leerSiExiste(sindicato.getSelloClave()),
                firmante(jerarquia.ejecutivoFederacion()),
                firmante(jerarquia.secretarioCentral()),
                firmante(jerarquia.secretarioSindicato()));
    }

    private CredencialProductor.Firmante firmante(Cargo cargo) {
        if (cargo == null) {
            return null;
        }
        return new CredencialProductor.Firmante(
                cargo.getProductor().getNombreCompleto(),
                cargo.getCargo().getEtiqueta().toUpperCase(Locale.ROOT),
                cargo.getDuenoNombre(),
                bytesDe(cargo, TipoImagenCargo.FIRMA),
                bytesDe(cargo, TipoImagenCargo.PIE_FIRMA));
    }

    private record Jerarquia(Cargo ejecutivoFederacion, Cargo secretarioCentral,
                             Cargo secretarioSindicato) {
    }

    private Jerarquia jerarquiaDe(Sindicato sindicato) {
        Long centralId = sindicato.getCentral().getId();
        Long federacionId = sindicato.getCentral().getFederacion().getId();
        return new Jerarquia(
                cargoRepository.findByFederacionIdAndCargoAndVigenteIsTrue(
                        federacionId, TipoCargo.EJECUTIVO).orElse(null),
                cargoRepository.findByCentralIdAndCargoAndVigenteIsTrue(
                        centralId, TipoCargo.SECRETARIO_GENERAL).orElse(null),
                cargoRepository.findBySindicatoIdAndCargoAndVigenteIsTrue(
                        sindicato.getId(), TipoCargo.SECRETARIO_GENERAL).orElse(null));
    }

    private String claveDe(Cargo cargo, TipoImagenCargo tipo) {
        for (ImagenCargo imagen : cargo.getImagenes()) {
            if (imagen.getTipo() == tipo) return imagen.getClave();
        }
        return null;
    }

    private String urlDe(String clave) {
        return clave == null || clave.isBlank() ? null : AlmacenLocal.RUTA_PUBLICA + clave;
    }

    private byte[] leerSiExiste(String clave) {
        return clave == null || clave.isBlank() ? null : leer(clave);
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

    /**
     * La dirección de la foto, sin traer los bytes.
     * <p>
     * La vista previa la pide como cualquier otra imagen de la app, así que
     * mandar el binario dentro del JSON sería cargarlo dos veces.
     */
    private String fotoUrlDe(Long productorId) {
        return imagenRepository.findByProductorIdAndTipo(productorId, TipoImagen.MINIATURA)
                .map(imagen -> AlmacenLocal.RUTA_PUBLICA + imagen.getClave())
                .orElse(null);
    }

    /** Las direcciones de las fotos de varios, en una sola consulta. */
    private Map<Long, String> fotoUrlesDe(List<Long> ids) {
        Map<Long, String> urles = new HashMap<>();
        for (Object[] fila : imagenRepository.findClavesPorProductores(ids)) {
            if (fila[1] == TipoImagen.MINIATURA) {
                urles.put((Long) fila[0], AlmacenLocal.RUTA_PUBLICA + fila[2]);
            }
        }
        return urles;
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
            if (numero == null || numero.isBlank()) {
                continue;
            }
            ExtensionLote extension = (ExtensionLote) fila[2];
            String letraCompartida = (String) fila[3];
            String codigo = CodigoLote.de(numero, extension, letraCompartida);
            if (codigo == null) {
                continue;
            }
            porProductor
                    .computeIfAbsent((Long) fila[0], k -> new ArrayList<>())
                    .add(codigo);
        }
        return porProductor;
    }

    private boolean tieneNumeroLote(Map<Long, List<String>> lotes, Long productorId) {
        List<String> codigos = lotes.get(productorId);
        return codigos != null && !codigos.isEmpty();
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
