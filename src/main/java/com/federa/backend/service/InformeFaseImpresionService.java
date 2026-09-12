package com.federa.backend.service;

import com.federa.backend.dto.InformeFaseImpresion;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.FaseImpresionCarnet;
import com.federa.backend.model.ProductorFaseImpresion;
import com.federa.backend.repository.FaseImpresionCarnetRepository;
import com.federa.backend.repository.ProductorFaseImpresionRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Informe histórico propio de cada fase de impresión. */
@Service
@Transactional(readOnly = true)
public class InformeFaseImpresionService {

    private final FaseImpresionCarnetRepository faseRepository;
    private final ProductorFaseImpresionRepository participanteRepository;
    private final CredencialService credencialService;
    private final InformeFaseImpresionPdf generadorPdf;

    public InformeFaseImpresionService(
            FaseImpresionCarnetRepository faseRepository,
            ProductorFaseImpresionRepository participanteRepository,
            CredencialService credencialService,
            InformeFaseImpresionPdf generadorPdf) {
        this.faseRepository = faseRepository;
        this.participanteRepository = participanteRepository;
        this.credencialService = credencialService;
        this.generadorPdf = generadorPdf;
    }

    public InformeFaseImpresion obtener(Long centralId, Long faseId) {
        FaseImpresionCarnet fase = faseRepository.findById(faseId)
                .orElseThrow(() -> new RecursoNoEncontradoException("fase de impresión", faseId));
        if (!centralId.equals(fase.getCentral().getId())) {
            throw new ReglaNegocioException("La fase no pertenece a esta central");
        }
        List<ProductorFaseImpresion> participantes = participanteRepository
                .findTodosDeFase(faseId);
        Map<Long, List<ProductorFaseImpresion>> porSindicato = participantes.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getProductor().getSindicato().getId(),
                        LinkedHashMap::new, Collectors.toList()));
        List<InformeFaseImpresion.SeccionSindicato> secciones = new ArrayList<>();
        for (List<ProductorFaseImpresion> grupo : porSindicato.values()) {
            Long sindicatoId = grupo.get(0).getProductor().getSindicato().getId();
            Map<Long, CredencialService.FilaRevisionDatos> datos = credencialService
                    .estadoRevisionDatosSindicato(sindicatoId).productores().stream()
                    .collect(Collectors.toMap(
                            CredencialService.FilaRevisionDatos::productorId,
                            Function.identity()));
            List<InformeFaseImpresion.Fila> impresos = new ArrayList<>();
            List<InformeFaseImpresion.Fila> pendientes = new ArrayList<>();
            for (ProductorFaseImpresion participante : grupo) {
                CredencialService.FilaRevisionDatos dato = datos.get(
                        participante.getProductor().getId());
                if (dato == null) continue;
                InformeFaseImpresion.Fila fila = fila(dato, participante);
                if (participante.isPendiente()) pendientes.add(fila);
                else if (participante.getImpresionesEnFase() > 0) impresos.add(fila);
            }
            secciones.add(new InformeFaseImpresion.SeccionSindicato(
                    sindicatoId, grupo.get(0).getProductor().getSindicato().getNombre(),
                    List.copyOf(impresos), List.copyOf(pendientes)));
        }
        int totalImpresos = secciones.stream().mapToInt(s -> s.impresos().size()).sum();
        int totalPendientes = secciones.stream().mapToInt(s -> s.pendientes().size()).sum();
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

    private InformeFaseImpresion.Fila fila(
            CredencialService.FilaRevisionDatos dato,
            ProductorFaseImpresion participante) {
        List<String> observaciones = new ArrayList<>();
        if (dato.observado()) {
            observaciones.add("OBSERVADO"
                    + (dato.observacion().isBlank() ? "" : ": " + dato.observacion()));
        }
        observaciones.addAll(dato.datosFaltantes().stream()
                .filter(valor -> !"Observado".equalsIgnoreCase(valor))
                .toList());
        if (participante.isPendiente() && observaciones.isEmpty()) {
            observaciones.add(participante.isReimpresion()
                    ? "REIMPRESIÓN PENDIENTE" : "PENDIENTE DE IMPRESIÓN");
        }
        return new InformeFaseImpresion.Fila(
                dato.productorId(), dato.nombres(), dato.apellidos(), dato.ci(), dato.lotes(),
                List.copyOf(observaciones),
                participante.isReimpresion() && participante.getImpresionesEnFase() > 0);
    }

    public record Descarga(String nombreArchivo, byte[] contenido) {
    }
}
