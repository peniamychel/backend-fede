package com.federa.backend.service;

import com.federa.backend.dto.InformeImpresionCentral;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Central;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.SindicatoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Consolida por central los mismos estados del panel de impresión masiva. */
@Service
@Transactional(readOnly = true)
public class InformeImpresionCentralService {

    private final CentralRepository centralRepository;
    private final SindicatoRepository sindicatoRepository;
    private final CredencialService credencialService;
    private final InformeImpresionCentralPdf generadorPdf;

    public InformeImpresionCentralService(CentralRepository centralRepository,
                                           SindicatoRepository sindicatoRepository,
                                           CredencialService credencialService,
                                           InformeImpresionCentralPdf generadorPdf) {
        this.centralRepository = centralRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.credencialService = credencialService;
        this.generadorPdf = generadorPdf;
    }

    public InformeImpresionCentral obtener(Long centralId) {
        Central central = centralRepository.findById(centralId)
                .orElseThrow(() -> new RecursoNoEncontradoException("central", centralId));
        List<Sindicato> sindicatos = sindicatoRepository.findByCentralIdOrderByNombreAsc(centralId);
        List<InformeImpresionCentral.FilaSindicato> detalle = new ArrayList<>(sindicatos.size());

        int total = 0;
        int impresos = 0;
        int pendientesConFoto = 0;
        int sinFoto = 0;
        int listos = 0;
        int sindicatosSinSello = 0;
        for (Sindicato sindicato : sindicatos) {
            CredencialService.PanelImpresionSindicato panel =
                    credencialService.panelImpresionSindicato(sindicato.getId());
            int pendientes = panel.total() - panel.impresos();
            boolean selloCargado = sindicato.getSelloClave() != null
                    && !sindicato.getSelloClave().isBlank();
            if (!selloCargado) sindicatosSinSello++;
            detalle.add(new InformeImpresionCentral.FilaSindicato(
                    sindicato.getId(), sindicato.getNombre(), selloCargado,
                    panel.total(), panel.impresos(), pendientes,
                    panel.faltantesConFoto(), panel.sinFoto(),
                    panel.listosParaImprimir(), porcentaje(panel.impresos(), panel.total())));
            total += panel.total();
            impresos += panel.impresos();
            pendientesConFoto += panel.faltantesConFoto();
            sinFoto += panel.sinFoto();
            listos += panel.listosParaImprimir();
        }

        return new InformeImpresionCentral(
                central.getId(), central.getNombre(), central.getFederacion().getNombre(),
                sindicatos.size(), sindicatosSinSello, total, impresos, total - impresos,
                pendientesConFoto, sinFoto, listos, porcentaje(impresos, total),
                List.copyOf(detalle));
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
}
