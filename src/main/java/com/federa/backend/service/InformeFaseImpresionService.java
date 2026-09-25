package com.federa.backend.service;

import com.federa.backend.dto.InformeFaseImpresion;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.FaseImpresionCarnet;
import com.federa.backend.model.ProductorFaseImpresion;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.FaseImpresionCarnetRepository;
import com.federa.backend.repository.ProductorFaseImpresionRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Informe histórico propio de cada fase de impresión. */
@Service
@Transactional(readOnly = true)
public class InformeFaseImpresionService {

    private final FaseImpresionCarnetRepository faseRepository;
    private final ProductorFaseImpresionRepository participanteRepository;
    private final SindicatoRepository sindicatoRepository;
    private final CredencialService credencialService;
    private final InformeFaseImpresionPdf generadorPdf;

    public InformeFaseImpresionService(
            FaseImpresionCarnetRepository faseRepository,
            ProductorFaseImpresionRepository participanteRepository,
            SindicatoRepository sindicatoRepository,
            CredencialService credencialService,
            InformeFaseImpresionPdf generadorPdf) {
        this.faseRepository = faseRepository;
        this.participanteRepository = participanteRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.credencialService = credencialService;
        this.generadorPdf = generadorPdf;
    }

    public InformeFaseImpresion obtener(Long centralId, Long faseId) {
        return obtener(centralId, faseId, null);
    }

    public InformeFaseImpresion obtenerSindicato(Long sindicatoId, Long faseId) {
        Sindicato sindicato = sindicatoAutorizado(sindicatoId);
        return obtener(sindicato.getCentral().getId(), faseId, sindicato);
    }

    private InformeFaseImpresion obtener(Long centralId, Long faseId,
                                         Sindicato sindicatoSeleccionado) {
        FaseImpresionCarnet fase = faseRepository.findById(faseId)
                .orElseThrow(() -> new RecursoNoEncontradoException("fase de impresión", faseId));
        if (!centralId.equals(fase.getCentral().getId())) {
            throw new ReglaNegocioException("La fase no pertenece a esta central");
        }
        List<ProductorFaseImpresion> participantes = sindicatoSeleccionado == null
                ? participanteRepository.findImpresionesHastaFase(centralId, fase.getNumero())
                : participanteRepository.findImpresionesHastaFaseSindicato(
                        centralId, fase.getNumero(), sindicatoSeleccionado.getId());
        Map<Long, LinkedHashSet<Integer>> fasesImpresas = new HashMap<>();
        Map<Long, ProductorFaseImpresion> participantesActuales = new HashMap<>();
        for (ProductorFaseImpresion participante : participantes) {
            Long productorId = participante.getProductor().getId();
            if (participante.getImpresionesEnFase() > 0) {
                fasesImpresas.computeIfAbsent(productorId, ignorado -> new LinkedHashSet<>())
                        .add(participante.getFase().getNumero());
            }
            if (participante.getFase().getId().equals(faseId)) {
                participantesActuales.put(productorId, participante);
            }
        }
        List<InformeFaseImpresion.SeccionSindicato> secciones = new ArrayList<>();
        List<Sindicato> sindicatos = sindicatoSeleccionado == null
                ? sindicatoRepository.findByCentralIdOrderByNombreAsc(centralId)
                : List.of(sindicatoSeleccionado);
        for (Sindicato sindicato : sindicatos) {
            List<InformeFaseImpresion.Fila> filas = new ArrayList<>();
            int impresosEnFase = 0;
            int impresosAcumulados = 0;
            for (CredencialService.FilaRevisionDatos dato : credencialService
                    .estadoRevisionDatosSindicato(sindicato.getId()).productores()) {
                List<Integer> numeros = List.copyOf(fasesImpresas.getOrDefault(
                        dato.productorId(), new LinkedHashSet<>()));
                ProductorFaseImpresion actual = participantesActuales.get(dato.productorId());
                if (numeros.contains(fase.getNumero())) impresosEnFase++;
                if (!numeros.isEmpty()) impresosAcumulados++;
                filas.add(fila(dato, numeros, actual));
            }
            secciones.add(new InformeFaseImpresion.SeccionSindicato(
                    sindicato.getId(), sindicato.getNombre(), List.copyOf(filas),
                    impresosEnFase, impresosAcumulados));
        }
        int totalImpresos = secciones.stream().mapToInt(
                InformeFaseImpresion.SeccionSindicato::impresosEnFase).sum();
        int totalPendientes = secciones.stream().mapToInt(
                s -> s.productores().size() - s.impresosAcumulados()).sum();
        return new InformeFaseImpresion(
                fase.getCentral().getId(), fase.getCentral().getNombre(),
                fase.getCentral().getFederacion().getNombre(), fase.getId(), fase.getNumero(),
                fase.getAbiertaEn(), fase.getCerradaEn(), totalImpresos, totalPendientes,
                List.copyOf(secciones));
    }

    public Descarga descargarPdf(Long centralId, Long faseId) {
        InformeFaseImpresion informe = obtener(centralId, faseId);
        String archivo = "informe-fase-" + informe.numeroFase() + "-"
                + Textos.paraNombreDeArchivo(informe.central(), 45) + ".pdf";
        return new Descarga(archivo, generadorPdf.generar(informe));
    }

    public List<FaseDisponible> fasesDisponiblesSindicato(Long sindicatoId) {
        Sindicato sindicato = sindicatoAutorizado(sindicatoId);
        return faseRepository.findByCentralIdOrderByNumeroDesc(
                        sindicato.getCentral().getId()).stream()
                .map(fase -> new FaseDisponible(
                        fase.getId(), fase.getNumero(), fase.estaAbierta()))
                .toList();
    }

    public Descarga descargarSindicatoPdf(Long sindicatoId, Long faseId) {
        InformeFaseImpresion informe = obtenerSindicato(sindicatoId, faseId);
        String archivo = "informe-fase-" + informe.numeroFase() + "-"
                + Textos.paraNombreDeArchivo(informe.central(), 30) + "-"
                + Textos.paraNombreDeArchivo(informe.sindicatos().get(0).sindicato(), 40)
                + ".pdf";
        return new Descarga(archivo, generadorPdf.generar(informe));
    }

    private Sindicato sindicatoAutorizado(Long sindicatoId) {
        Sindicato sindicato = sindicatoRepository.findById(sindicatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", sindicatoId));
        com.federa.backend.seguridad.AlcanceCentral.verificarSindicato(sindicatoId);
        com.federa.backend.seguridad.AlcanceCentral.limitar(
                sindicato.getCentral().getId());
        return sindicato;
    }

    private InformeFaseImpresion.Fila fila(
            CredencialService.FilaRevisionDatos dato,
            List<Integer> fasesImpresas,
            ProductorFaseImpresion participanteActual) {
        return new InformeFaseImpresion.Fila(
                dato.productorId(), dato.nombres(), dato.apellidos(), dato.ci(), dato.lotes(),
                dato.observado(), dato.datosFaltantes(), fasesImpresas,
                participanteActual != null && participanteActual.isReimpresion()
                        && participanteActual.getImpresionesEnFase() > 0);
    }

    public record Descarga(String nombreArchivo, byte[] contenido) {
    }

    public record FaseDisponible(Long id, int numero, boolean abierta) {
    }
}
