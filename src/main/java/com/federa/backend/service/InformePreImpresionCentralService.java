package com.federa.backend.service;

import com.federa.backend.dto.InformePreImpresionCentral;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Central;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Construye el padrón nominal único para revisar datos antes de imprimir. */
@Service
@Transactional(readOnly = true)
public class InformePreImpresionCentralService {

    private final CentralRepository centralRepository;
    private final SindicatoRepository sindicatoRepository;
    private final CredencialService credencialService;
    private final InformePreImpresionCentralPdf generadorPdf;
    private final InformeRevisionPadronCentralPdf generadorRevisionPadronPdf;

    public InformePreImpresionCentralService(
            CentralRepository centralRepository,
            SindicatoRepository sindicatoRepository,
            CredencialService credencialService,
            InformePreImpresionCentralPdf generadorPdf,
            InformeRevisionPadronCentralPdf generadorRevisionPadronPdf) {
        this.centralRepository = centralRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.credencialService = credencialService;
        this.generadorPdf = generadorPdf;
        this.generadorRevisionPadronPdf = generadorRevisionPadronPdf;
    }

    public InformePreImpresionCentral obtener(Long centralId) {
        Central central = centralRepository.findById(centralId)
                .orElseThrow(() -> new RecursoNoEncontradoException("central", centralId));
        List<InformePreImpresionCentral.SeccionSindicato> secciones = sindicatoRepository
                .findByCentralIdOrderByNombreAsc(centralId).stream()
                .map(this::seccion)
                .toList();
        int total = secciones.stream().mapToInt(s -> s.productores().size()).sum();
        return new InformePreImpresionCentral(
                central.getId(), central.getNombre(), central.getFederacion().getNombre(),
                total, secciones);
    }

    public Descarga descargarPdf(Long centralId) {
        InformePreImpresionCentral informe = obtener(centralId);
        String archivo = "informe-pre-impresion-"
                + Textos.paraNombreDeArchivo(informe.central(), 50) + ".pdf";
        return new Descarga(archivo, generadorPdf.generar(informe));
    }

    public Descarga descargarRevisionPadronPdf(Long centralId) {
        InformePreImpresionCentral informe = obtener(centralId);
        String archivo = "revision-padron-"
                + Textos.paraNombreDeArchivo(informe.central(), 50) + ".pdf";
        return new Descarga(archivo, generadorRevisionPadronPdf.generar(informe));
    }

    private InformePreImpresionCentral.SeccionSindicato seccion(Sindicato sindicato) {
        CredencialService.EstadoRevisionDatosSindicato estado = credencialService
                .estadoRevisionDatosSindicato(sindicato.getId());
        List<InformePreImpresionCentral.Fila> filas = estado.productores().stream()
                .map(fila -> new InformePreImpresionCentral.Fila(
                        fila.productorId(), fila.nombres(), fila.apellidos(), fila.ci(),
                        fila.lotes(), fila.observado(),
                        fila.datosFaltantes()))
                .toList();
        return new InformePreImpresionCentral.SeccionSindicato(
                estado.sindicatoId(), estado.sindicato(), filas);
    }

    public record Descarga(String nombreArchivo, byte[] contenido) {
    }
}
