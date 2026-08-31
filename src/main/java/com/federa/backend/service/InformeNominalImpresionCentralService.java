package com.federa.backend.service;

import com.federa.backend.dto.InformeNominalImpresionCentral;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Central;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Arma el informe nominal para toda una central o sindicatos seleccionados. */
@Service
@Transactional(readOnly = true)
public class InformeNominalImpresionCentralService {

    /**
     * El nominal es una lista de personas, no un diagnóstico del directorio.
     * La lista blanca evita que un requisito institucional presente o futuro
     * termine repetido en cada productor del sindicato.
     */
    private static final Set<String> DATOS_DEL_PRODUCTOR = Set.of(
            "Apellidos", "Cédula", "Fotografía", "Número de lote", "Número en la central");

    private final CentralRepository centralRepository;
    private final SindicatoRepository sindicatoRepository;
    private final CredencialService credencialService;
    private final InformeNominalImpresionCentralPdf generadorPdf;

    public InformeNominalImpresionCentralService(
            CentralRepository centralRepository,
            SindicatoRepository sindicatoRepository,
            CredencialService credencialService,
            InformeNominalImpresionCentralPdf generadorPdf) {
        this.centralRepository = centralRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.credencialService = credencialService;
        this.generadorPdf = generadorPdf;
    }

    public InformeNominalImpresionCentral obtener(Long centralId, List<Long> sindicatoIds) {
        Central central = centralRepository.findById(centralId)
                .orElseThrow(() -> new RecursoNoEncontradoException("central", centralId));
        List<Sindicato> disponibles = sindicatoRepository
                .findByCentralIdOrderByNombreAsc(centralId);
        List<Sindicato> seleccionados = seleccionar(disponibles, sindicatoIds);

        List<InformeNominalImpresionCentral.SeccionSindicato> secciones = seleccionados.stream()
                .map(this::seccion)
                .toList();
        int impresos = secciones.stream().mapToInt(s -> s.impresos().size()).sum();
        int faltantes = secciones.stream().mapToInt(s -> s.faltantesDatos().size()).sum();
        return new InformeNominalImpresionCentral(
                central.getId(), central.getNombre(), central.getFederacion().getNombre(),
                impresos, faltantes, secciones);
    }

    public Descarga descargarPdf(Long centralId, List<Long> sindicatoIds) {
        InformeNominalImpresionCentral informe = obtener(centralId, sindicatoIds);
        String archivo = "informe-nominal-carnet-productor-"
                + Textos.paraNombreDeArchivo(informe.central(), 50) + ".pdf";
        return new Descarga(archivo, generadorPdf.generar(informe));
    }

    private List<Sindicato> seleccionar(List<Sindicato> disponibles, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return disponibles;
        Set<Long> seleccion = new HashSet<>(ids);
        List<Sindicato> resultado = disponibles.stream()
                .filter(s -> seleccion.contains(s.getId()))
                .toList();
        if (resultado.size() != seleccion.size()) {
            throw new ReglaNegocioException(
                    "La selección contiene un sindicato que no pertenece a la central");
        }
        return resultado;
    }

    private InformeNominalImpresionCentral.SeccionSindicato seccion(Sindicato sindicato) {
        CredencialService.EstadoNominalSindicato estado =
                credencialService.estadoNominalSindicato(sindicato.getId());
        List<InformeNominalImpresionCentral.Fila> faltantesDatos = estado.faltantesDatos()
                .stream()
                .map(this::fila)
                .filter(fila -> !fila.datosFaltantes().isEmpty())
                .toList();
        return new InformeNominalImpresionCentral.SeccionSindicato(
                estado.sindicatoId(), estado.sindicato(),
                estado.impresos().stream().map(this::fila).toList(),
                faltantesDatos);
    }

    private InformeNominalImpresionCentral.Fila fila(
            CredencialService.FilaInformeImpresion fila) {
        List<String> datosFaltantesVisibles = fila.datosFaltantes().stream()
                .filter(this::esDatoFaltanteNominal)
                .toList();
        return new InformeNominalImpresionCentral.Fila(
                fila.productorId(), fila.nombres(), fila.apellidos(), fila.ci(), fila.lotes(),
                fila.codigoPadron(), fila.impresiones(), fila.ultimaImpresion(),
                datosFaltantesVisibles);
    }

    private boolean esDatoFaltanteNominal(String dato) {
        if (dato == null) return false;
        return DATOS_DEL_PRODUCTOR.stream().anyMatch(valor -> valor.equalsIgnoreCase(dato.trim()));
    }

    public record Descarga(String nombreArchivo, byte[] contenido) {
    }
}
