package com.federa.backend.dto;

import com.federa.backend.model.Productor;
import com.federa.backend.model.TenenciaLote;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Ficha completa del productor: sus datos, sus lotes y sus observaciones. */
@Schema(description = "Ficha completa del productor, con sus lotes y observaciones en una "
        + "sola respuesta para no tener que encadenar llamadas.")
public record ProductorDetalleResponse(

        @Schema(description = "Datos del productor.")
        ProductorResponse productor,

        @Schema(description = "Lotes que tiene asignados. Lista vacía si no tiene ninguno.")
        List<LoteResponse> lotes,

        @Schema(description = "Observaciones anotadas sobre él, resueltas y pendientes.")
        List<ObservacionResponse> observaciones,

        @Schema(description = "Imágenes cargadas, solo su metadata. Los bytes se piden por "
                + "separado a /imagenes/{tipo}/contenido.")
        List<ImagenResponse> imagenes
) {

    public static ProductorDetalleResponse desde(Productor p) {
        return new ProductorDetalleResponse(
                ProductorResponse.desde(p),
                // Los lotes que tiene hoy, no los que tuvo alguna vez: el que
                // vendió ya no es suyo. El historial completo está en
                // /productores/{id}/lotes/historial.
                p.getTenencias().stream()
                        .filter(TenenciaLote::estaVigente)
                        .map(t -> LoteResponse.desde(t.getLote(), t, null))
                        .toList(),
                p.getObservaciones().stream().map(ObservacionResponse::desde).toList(),
                // El contenido de cada imagen es LAZY, así que armar esta lista
                // trae la metadata sin arrastrar los binarios.
                p.getImagenes().stream().map(ImagenResponse::desde).toList());
    }
}
