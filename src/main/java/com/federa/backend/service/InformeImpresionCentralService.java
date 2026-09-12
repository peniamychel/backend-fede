package com.federa.backend.service;

import com.federa.backend.dto.InformeImpresionCentral;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Central;
import com.federa.backend.model.FaseImpresionCarnet;
import com.federa.backend.model.Productor;
import com.federa.backend.model.ProductorFaseImpresion;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.TenenciaLote;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.FaseImpresionCarnetRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.ProductorFaseImpresionRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.repository.TenenciaLoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Consolida por central los mismos estados del panel de impresión masiva. */
@Service
@Transactional(readOnly = true)
public class InformeImpresionCentralService {

    private final CentralRepository centralRepository;
    private final SindicatoRepository sindicatoRepository;
    private final ProductorRepository productorRepository;
    private final TenenciaLoteRepository tenenciaLoteRepository;
    private final FaseImpresionCarnetRepository faseRepository;
    private final ProductorFaseImpresionRepository participanteFaseRepository;
    private final CredencialService credencialService;
    private final InformeImpresionCentralPdf generadorPdf;

    public InformeImpresionCentralService(CentralRepository centralRepository,
                                           SindicatoRepository sindicatoRepository,
                                           ProductorRepository productorRepository,
                                           TenenciaLoteRepository tenenciaLoteRepository,
                                           FaseImpresionCarnetRepository faseRepository,
                                           ProductorFaseImpresionRepository participanteFaseRepository,
                                           CredencialService credencialService,
                                           InformeImpresionCentralPdf generadorPdf) {
        this.centralRepository = centralRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.productorRepository = productorRepository;
        this.tenenciaLoteRepository = tenenciaLoteRepository;
        this.faseRepository = faseRepository;
        this.participanteFaseRepository = participanteFaseRepository;
        this.credencialService = credencialService;
        this.generadorPdf = generadorPdf;
    }

    public InformeImpresionCentral obtener(Long centralId) {
        Central central = centralRepository.findById(centralId)
                .orElseThrow(() -> new RecursoNoEncontradoException("central", centralId));
        List<Sindicato> sindicatos = sindicatoRepository.findByCentralIdOrderByNombreAsc(centralId);
        List<InformeImpresionCentral.FilaSindicato> detalle = new ArrayList<>(sindicatos.size());
        Map<Long, EstadisticasProductores> estadisticas = estadisticasProductores(centralId);
        Map<Long, CredencialService.PanelImpresionSindicato> paneles = new LinkedHashMap<>();
        Map<Long, Integer> totalesPorSindicato = new LinkedHashMap<>();
        Map<Long, Integer> impresosActualesPorSindicato = new LinkedHashMap<>();
        for (Sindicato sindicato : sindicatos) {
            CredencialService.PanelImpresionSindicato panel =
                    credencialService.panelImpresionSindicato(sindicato.getId());
            paneles.put(sindicato.getId(), panel);
            totalesPorSindicato.put(sindicato.getId(), panel.total());
            impresosActualesPorSindicato.put(sindicato.getId(), panel.impresos());
        }
        AvancesPorFase avances = avancesPorFase(
                centralId, totalesPorSindicato, impresosActualesPorSindicato);

        int total = 0;
        int impresos = 0;
        int pendientesConFoto = 0;
        int sinFoto = 0;
        int listos = 0;
        int observados = 0;
        int sistema = 0;
        int sinSistema = 0;
        int sindicatosSinSello = 0;
        for (Sindicato sindicato : sindicatos) {
            CredencialService.PanelImpresionSindicato panel = paneles.get(sindicato.getId());
            int pendientes = panel.total() - panel.impresos();
            boolean selloCargado = sindicato.getSelloClave() != null
                    && !sindicato.getSelloClave().isBlank();
            if (!selloCargado) sindicatosSinSello++;
            EstadisticasProductores cifras = estadisticas.getOrDefault(
                    sindicato.getId(), EstadisticasProductores.VACIAS);
            detalle.add(new InformeImpresionCentral.FilaSindicato(
                    sindicato.getId(), sindicato.getNombre(), selloCargado,
                    panel.total(), panel.impresos(), pendientes,
                    panel.faltantesConFoto(), panel.sinFoto(),
                    panel.listosParaImprimir(), cifras.observados(), cifras.sistema(),
                    cifras.sinSistema(), porcentaje(panel.impresos(), panel.total()),
                    avances.porSindicato().getOrDefault(sindicato.getId(), List.of())));
            total += panel.total();
            impresos += panel.impresos();
            pendientesConFoto += panel.faltantesConFoto();
            sinFoto += panel.sinFoto();
            listos += panel.listosParaImprimir();
            observados += cifras.observados();
            sistema += cifras.sistema();
            sinSistema += cifras.sinSistema();
        }

        return new InformeImpresionCentral(
                central.getId(), central.getNombre(), central.getFederacion().getNombre(),
                sindicatos.size(), sindicatosSinSello, total, impresos, total - impresos,
                pendientesConFoto, sinFoto, listos, observados, sistema, sinSistema,
                porcentaje(impresos, total),
                avances.central(),
                List.copyOf(detalle));
    }

    /**
     * Calcula el avance acumulado. Una reimpresión no aumenta el numerador:
     * cada productor cuenta una sola vez desde la primera fase en que se imprimió.
     */
    private AvancesPorFase avancesPorFase(
            Long centralId, Map<Long, Integer> totalesPorSindicato,
            Map<Long, Integer> impresosActualesPorSindicato) {
        List<FaseImpresionCarnet> fases = faseRepository
                .findByCentralIdOrderByNumeroAsc(centralId);
        if (fases.isEmpty()) return new AvancesPorFase(List.of(), Map.of());

        Set<Long> impresosCentral = new HashSet<>();
        Map<Long, Set<Long>> impresosPorSindicato = new HashMap<>();
        List<InformeImpresionCentral.AvanceFase> central = new ArrayList<>(fases.size());
        Map<Long, List<InformeImpresionCentral.AvanceFase>> porSindicato =
                new LinkedHashMap<>();
        totalesPorSindicato.keySet().forEach(id -> porSindicato.put(id, new ArrayList<>()));
        int totalCentral = totalesPorSindicato.values().stream().mapToInt(Integer::intValue).sum();
        int impresosActualesCentral = impresosActualesPorSindicato.values().stream()
                .mapToInt(Integer::intValue).sum();

        for (int indiceFase = 0; indiceFase < fases.size(); indiceFase++) {
            FaseImpresionCarnet fase = fases.get(indiceFase);
            for (ProductorFaseImpresion participante
                    : participanteFaseRepository.findTodosDeFase(fase.getId())) {
                if (participante.getImpresionesEnFase() < 1) continue;
                Long productorId = participante.getProductor().getId();
                Long sindicatoId = participante.getProductor().getSindicato().getId();
                impresosCentral.add(productorId);
                impresosPorSindicato.computeIfAbsent(sindicatoId, id -> new HashSet<>())
                        .add(productorId);
            }
            boolean ultimaFase = indiceFase == fases.size() - 1;
            central.add(new InformeImpresionCentral.AvanceFase(
                    fase.getNumero(), porcentaje(
                    ultimaFase ? impresosActualesCentral : impresosCentral.size(),
                    totalCentral)));
            for (Map.Entry<Long, Integer> total : totalesPorSindicato.entrySet()) {
                int impresos = impresosPorSindicato
                        .getOrDefault(total.getKey(), Set.of()).size();
                if (ultimaFase) {
                    impresos = impresosActualesPorSindicato.getOrDefault(total.getKey(), 0);
                }
                porSindicato.get(total.getKey()).add(
                        new InformeImpresionCentral.AvanceFase(
                                fase.getNumero(), porcentaje(impresos, total.getValue())));
            }
        }
        Map<Long, List<InformeImpresionCentral.AvanceFase>> inmutables = new LinkedHashMap<>();
        porSindicato.forEach((id, valores) -> inmutables.put(id, List.copyOf(valores)));
        return new AvancesPorFase(List.copyOf(central), Map.copyOf(inmutables));
    }

    /**
     * Clasifica cada productor una sola vez aunque tenga varias parcelas.
     * Una parcela vigente con sistema tiene prioridad; la clasificación
     * pendiente solo se usa mientras el productor todavía no tiene parcela.
     */
    private Map<Long, EstadisticasProductores> estadisticasProductores(Long centralId) {
        List<Productor> productores = productorRepository
                .findBySindicatoCentralIdOrderByApellidosAscNombresAsc(centralId);
        if (productores.isEmpty()) return Map.of();

        List<Long> ids = productores.stream().map(Productor::getId).toList();
        List<TenenciaLote> tenencias = tenenciaLoteRepository.findVigentesDeProductores(ids);
        Set<Long> conParcela = tenencias.stream()
                .map(tenencia -> tenencia.getProductor().getId())
                .collect(Collectors.toSet());
        Set<Long> conSistema = tenencias.stream()
                .filter(tenencia -> tenencia.getLote().getEstadoLote() == EstadoLote.CON_SISTEMA)
                .map(tenencia -> tenencia.getProductor().getId())
                .collect(Collectors.toSet());

        Map<Long, int[]> acumulado = new HashMap<>();
        for (Productor productor : productores) {
            int[] cifras = acumulado.computeIfAbsent(
                    productor.getSindicato().getId(), id -> new int[3]);
            if (productor.isObservado()) cifras[0]++;
            boolean esSistema = conSistema.contains(productor.getId())
                    || (!conParcela.contains(productor.getId())
                    && productor.getClasificacionPendiente() == EstadoLote.CON_SISTEMA);
            cifras[esSistema ? 1 : 2]++;
        }

        Map<Long, EstadisticasProductores> resultado = new HashMap<>();
        acumulado.forEach((sindicatoId, cifras) -> resultado.put(sindicatoId,
                new EstadisticasProductores(cifras[0], cifras[1], cifras[2])));
        return resultado;
    }

    public Descarga descargarPdf(Long centralId) {
        InformeImpresionCentral informe = obtener(centralId);
        String nombre = "avance-credenciales-" + nombreSeguro(informe.central()) + ".pdf";
        return new Descarga(nombre, generadorPdf.generar(informe));
    }

    private static double porcentaje(int impresos, int total) {
        if (total == 0) return 0d;
        return Math.round(impresos * 1000d / total) / 10d;
    }

    private static String nombreSeguro(String texto) {
        return texto.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9áéíóúñ]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    public record Descarga(String nombreArchivo, byte[] contenido) {
    }

    private record EstadisticasProductores(int observados, int sistema, int sinSistema) {
        private static final EstadisticasProductores VACIAS =
                new EstadisticasProductores(0, 0, 0);
    }

    private record AvancesPorFase(
            List<InformeImpresionCentral.AvanceFase> central,
            Map<Long, List<InformeImpresionCentral.AvanceFase>> porSindicato) {
    }
}
