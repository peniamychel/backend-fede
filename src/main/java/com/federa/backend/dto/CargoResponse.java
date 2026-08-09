package com.federa.backend.dto;

import com.federa.backend.almacen.AlmacenLocal;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.ImagenCargo;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.model.enums.TipoImagenCargo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/** Un período en el que un productor ocupó un cargo del directorio. */
@Schema(name = "CargoResponse",
        description = "Período de un productor en un cargo del directorio.")
public record CargoResponse(

        @Schema(description = "Identificador del período.", example = "7")
        Long id,

        @Schema(description = "Qué cargo.", example = "PRESIDENTE")
        TipoCargo cargo,

        @Schema(description = "Id del productor que lo ocupa u ocupó.", example = "812")
        Long productorId,

        @Schema(description = "Su nombre, para no tener que pedirlo aparte.",
                example = "CONSTANTINA HINOJOSA LAFUENTE")
        String productorNombre,

        @Schema(description = "Nivel al que pertenece el cargo.", example = "SINDICATO")
        Ambito ambito,

        @Schema(description = "Id del sindicato, central o federación al que pertenece.",
                example = "17")
        Long ambitoId,

        @Schema(description = "Nombre de ese sindicato, central o federación.",
                example = "1RO DE MAYO")
        String ambitoNombre,

        @Schema(description = "Desde cuándo ocupa el cargo.", example = "2026-03-01")
        LocalDate desde,

        @Schema(description = "Hasta cuándo lo ocupó. Null si sigue en funciones.",
                example = "2026-08-08")
        LocalDate hasta,

        @Schema(description = "Derivado: si el período sigue abierto.", example = "true")
        boolean vigente,

        @Schema(description = "URL de la firma de este período. Null si no se cargó.",
                example = "/api/v1/archivos/firmas/a1b2c3d4e5f6-juan-morales.jpg")
        String firmaUrl,

        @Schema(description = "URL del pie de firma. Null si no se cargó.",
                example = "/api/v1/archivos/pies-firma/f6e5d4c3b2a1-juan-morales.jpg")
        String pieFirmaUrl
) {

    /** Para un cargo suelto: lee sus imágenes por la relación. */
    public static CargoResponse desde(Cargo cargo) {
        Map<TipoImagenCargo, String> urls = new EnumMap<>(TipoImagenCargo.class);
        for (ImagenCargo imagen : cargo.getImagenes()) {
            urls.put(imagen.getTipo(), AlmacenLocal.RUTA_PUBLICA + imagen.getClave());
        }
        return desde(cargo, urls);
    }

    /**
     * Para listados. Las URL llegan de una consulta en bloque: recorrer la
     * relación de cada período haría una consulta por fila del historial.
     */
    public static CargoResponse desde(Cargo cargo, Map<TipoImagenCargo, String> urls) {
        return new CargoResponse(
                cargo.getId(),
                cargo.getCargo(),
                cargo.getProductor().getId(),
                cargo.getProductor().getNombreCompleto(),
                cargo.getAmbito(),
                cargo.getDuenoId(),
                cargo.getDuenoNombre(),
                cargo.getDesde(),
                cargo.getHasta(),
                cargo.estaVigente(),
                urls.get(TipoImagenCargo.FIRMA),
                urls.get(TipoImagenCargo.PIE_FIRMA));
    }
}
