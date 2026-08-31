package com.federa.backend.service;

import com.federa.backend.dto.PlanillaRecoleccionDirectorio;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.Central;
import com.federa.backend.model.ImagenCargo;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Prepara la planilla para recolectar las imágenes institucionales de una central. */
@Service
@Transactional(readOnly = true)
public class PlanillaRecoleccionDirectorioService {

    private final CentralRepository centralRepository;
    private final SindicatoRepository sindicatoRepository;
    private final CargoRepository cargoRepository;
    private final PlanillaRecoleccionDirectorioPdf generadorPdf;

    public PlanillaRecoleccionDirectorioService(
            CentralRepository centralRepository,
            SindicatoRepository sindicatoRepository,
            CargoRepository cargoRepository,
            PlanillaRecoleccionDirectorioPdf generadorPdf) {
        this.centralRepository = centralRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.cargoRepository = cargoRepository;
        this.generadorPdf = generadorPdf;
    }

    public PlanillaRecoleccionDirectorio obtener(Long centralId) {
        Central central = centralRepository.findById(centralId)
                .orElseThrow(() -> new RecursoNoEncontradoException("central", centralId));
        Optional<Cargo> secretario = cargoRepository
                .findByCentralIdAndCargoAndVigenteIsTrue(
                        centralId, TipoCargo.SECRETARIO_GENERAL);
        List<PlanillaRecoleccionDirectorio.FilaSindicato> sindicatos =
                sindicatoRepository.findByCentralIdOrderByNombreAsc(centralId).stream()
                        .map(sindicato -> new PlanillaRecoleccionDirectorio.FilaSindicato(
                                sindicato.getId(), sindicato.getNombre(),
                                tieneTexto(sindicato.getSelloClave())))
                        .toList();

        return new PlanillaRecoleccionDirectorio(
                central.getId(), central.getFederacion().getNombre(), central.getNombre(),
                secretario.map(cargo -> cargo.getProductor().getNombreCompleto())
                        .orElse("SIN SECRETARIO GENERAL DESIGNADO"),
                tieneTexto(central.getSelloClave()),
                secretario.filter(cargo -> tieneImagen(cargo, TipoImagenCargo.FIRMA)).isPresent(),
                secretario.filter(cargo -> tieneImagen(cargo, TipoImagenCargo.PIE_FIRMA)).isPresent(),
                List.copyOf(sindicatos));
    }

    public Descarga descargarPdf(Long centralId) {
        PlanillaRecoleccionDirectorio planilla = obtener(centralId);
        String archivo = "planilla-recoleccion-directorio-"
                + Textos.paraNombreDeArchivo(planilla.central(), 50) + ".pdf";
        return new Descarga(archivo, generadorPdf.generar(planilla));
    }

    private static boolean tieneImagen(Cargo cargo, TipoImagenCargo tipo) {
        if (cargo.getImagenes() == null) return false;
        for (ImagenCargo imagen : cargo.getImagenes()) {
            if (imagen.getTipo() == tipo) return true;
        }
        return false;
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    public record Descarga(String nombreArchivo, byte[] contenido) {
    }
}
