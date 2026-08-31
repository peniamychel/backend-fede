package com.federa.backend.dto;

import com.federa.backend.model.Lote;
import com.federa.backend.model.TenenciaLote;
import com.federa.backend.model.TenenciaSistema;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.model.enums.Mercado;
import com.federa.backend.util.CodigoLote;
import com.federa.backend.util.CodigoPadron;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una parcela: dónde está, quién la tiene y qué sistema lleva.
 *
 * @param tenedor quién la tiene hoy, o null si quedó sin tenedor —pasa cuando
 *                alguien vende y todavía no se registró al comprador—.
 * @param sistema el sistema instalado hoy, o null.
 */
@Schema(description = "Parcela del padrón. Pertenece al sindicato; quién la tiene cambia.")
public record LoteResponse(

        @Schema(description = "Identificador interno.", example = "1503")
        Long id,

        @Schema(description = "Número tal como está en el padrón.", example = "74")
        String numero,

        @Schema(description = "Subdivisión, ya normalizada.", example = "A")
        ExtensionLote extension,

        @Schema(description = "Derivado: número y letra automática juntos, para mostrar.",
                example = "74 A")
        String codigo,

        @Schema(description = "Estado normalizado. DESCONOCIDO significa que la escritura de "
                + "origen no se reconoció; el texto está en `estadoOriginal`.",
                example = "CON_SISTEMA")
        EstadoLote estado,

        @Schema(description = "Texto del estado tal como vino, antes de normalizar.",
                example = "C-S")
        String estadoOriginal,

        @Schema(description = "Mercado normalizado. Puede ser null.", example = "DETALLISTA")
        Mercado mercado,

        @Schema(description = "Superficie en hectáreas. Null si todavía no se midió.",
                example = "12.5000")
        BigDecimal superficie,

        @Schema(description = "Latitud en grados decimales. Null si no se marcó.",
                example = "-16.8574000")
        BigDecimal latitud,

        @Schema(description = "Longitud en grados decimales.", example = "-64.7891000")
        BigDecimal longitud,

        @Schema(description = "Derivado: si tiene las dos coordenadas.", example = "true")
        boolean tieneUbicacion,

        @Schema(description = "Cuándo se marcó la ubicación por última vez.",
                example = "2026-08-09T12:30:00")
        LocalDateTime ubicacionActualizadaEn,

        @Schema(description = "Sindicato donde está la tierra. No cambia.", example = "17")
        Long sindicatoId,

        @Schema(description = "Su nombre.", example = "ALTO SAN SALVADOR")
        String sindicatoNombre,

        @Schema(description = "Quién lo tiene hoy. Null si está sin tenedor.")
        Tenedor tenedor,

        @Schema(description = "Sistema instalado hoy. Null si no tiene.")
        SistemaEnLote sistema
) {

    /** Quién tiene el lote y desde cuándo. */
    public record Tenedor(
            @Schema(example = "812") Long productorId,
            @Schema(example = "CANDIDO COLQUECHAMBI MAMANI") String nombre,
            @Schema(example = "2-13J-78") String codigoPadron,
            @Schema(description = "A-H cuando comparte número de lote.", example = "A")
            String letra,
            @Schema(example = "2026-03-01") LocalDate desde) {
    }

    /** El sistema que lleva el lote, si lleva. */
    public record SistemaEnLote(
            @Schema(example = "3") Long sistemaId,
            @Schema(example = "S-014") String codigo,
            @Schema(example = "2026-03-01") LocalDate desde) {
    }

    /**
     * Para un lote suelto: lee la tenencia y el sistema por la relación.
     * <p>
     * En listados no se usa esta versión sino la de abajo, que recibe los datos
     * ya resueltos: recorrer las colecciones fila por fila dispararía dos
     * consultas por lote.
     */
    public static LoteResponse desde(Lote lote) {
        TenenciaLote tenencia = lote.getTenenciaVigente();
        TenenciaSistema sistema = lote.getSistemas().stream()
                .filter(TenenciaSistema::estaVigente)
                .findFirst()
                .orElse(null);
        return desde(lote, tenencia, sistema);
    }

    public static LoteResponse desde(Lote lote, TenenciaLote tenencia,
                                     TenenciaSistema sistema) {
        return new LoteResponse(
                lote.getId(),
                lote.getNumero(),
                lote.getExtension(),
                CodigoLote.de(lote, tenencia == null
                        ? null : tenencia.getProductor().getLetraCodigo()),
                lote.getEstadoLote(),
                lote.getEstadoOriginal(),
                lote.getMercado(),
                lote.getSuperficie(),
                lote.getLatitud(),
                lote.getLongitud(),
                lote.tieneUbicacion(),
                lote.getUbicacionActualizadaEn(),
                lote.getSindicato().getId(),
                lote.getSindicato().getNombre(),
                tenencia == null ? null : new Tenedor(
                        tenencia.getProductor().getId(),
                        tenencia.getProductor().getNombreCompleto(),
                        CodigoPadron.de(tenencia.getProductor()),
                        tenencia.getProductor().getLetraCodigo(),
                        tenencia.getDesde()),
                sistema == null ? null : new SistemaEnLote(
                        sistema.getSistema().getId(),
                        sistema.getSistema().getCodigo(),
                        sistema.getDesde()));
    }
}
