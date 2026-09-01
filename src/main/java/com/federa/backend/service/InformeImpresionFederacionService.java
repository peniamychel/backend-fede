package com.federa.backend.service;

import com.federa.backend.dto.InformeImpresionCentral;
import com.federa.backend.dto.InformeImpresionFederacion;
import com.federa.backend.model.Federacion;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Consolida el avance de impresión de todas las centrales de una federación. */
@Service
@Transactional(readOnly = true)
public class InformeImpresionFederacionService {

    private final FederacionService federacionService;
    private final CentralRepository centralRepository;
    private final InformeImpresionCentralService informeCentralService;
    private final InformeImpresionFederacionPdf pdf;

    public InformeImpresionFederacionService(FederacionService federacionService,
                                              CentralRepository centralRepository,
                                              InformeImpresionCentralService informeCentralService,
                                              InformeImpresionFederacionPdf pdf) {
        this.federacionService = federacionService;
        this.centralRepository = centralRepository;
        this.informeCentralService = informeCentralService;
        this.pdf = pdf;
    }

    public InformeImpresionFederacion obtener(Long federacionId) {
        Federacion federacion = federacionService.buscar(federacionId);
        List<InformeImpresionCentral> detalle = centralRepository
                .findByFederacionIdOrderByNombreAsc(federacionId).stream()
                .map(central -> informeCentralService.obtener(central.getId()))
                .toList();

        int sindicatos = detalle.stream().mapToInt(InformeImpresionCentral::sindicatos).sum();
        int sindicatosSinSello = detalle.stream()
                .mapToInt(InformeImpresionCentral::sindicatosSinSello).sum();
        int total = detalle.stream().mapToInt(InformeImpresionCentral::total).sum();
        int impresos = detalle.stream().mapToInt(InformeImpresionCentral::impresos).sum();
        int pendientesConFoto = detalle.stream()
                .mapToInt(InformeImpresionCentral::pendientesConFoto).sum();
        int sinFoto = detalle.stream().mapToInt(InformeImpresionCentral::sinFoto).sum();
        int listos = detalle.stream()
                .mapToInt(InformeImpresionCentral::listosParaImprimir).sum();

        return new InformeImpresionFederacion(
                federacion.getId(), federacion.getNombre(), detalle.size(), sindicatos,
                sindicatosSinSello, total, impresos, total - impresos,
                pendientesConFoto, sinFoto, listos, porcentaje(impresos, total),
                List.copyOf(detalle));
    }

    public Descarga descargarPdf(Long federacionId) {
        InformeImpresionFederacion informe = obtener(federacionId);
        String nombre = "avance-general-impresion-"
                + Textos.paraNombreDeArchivo(informe.federacion(), 45) + ".pdf";
        return new Descarga(nombre, pdf.generar(informe));
    }

    public record Descarga(String nombreArchivo, byte[] contenido) {
    }

    private static double porcentaje(int impresos, int total) {
        if (total == 0) return 0d;
        return Math.round(impresos * 1000d / total) / 10d;
    }
}
