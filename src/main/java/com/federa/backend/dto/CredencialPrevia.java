package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Vista previa de una credencial y de los requisitos que aún faltan. */
@Schema(description = "Vista previa de una credencial, con lo que falta para emitirla.")
public record CredencialPrevia(
        Long productorId,
        String nombreCompleto,
        String federacion,
        String central,
        String sindicato,
        String nombres,
        String apellidos,
        String ci,
        String lotes,
        String codigoPadron,
        String codigoQr,
        String fotoUrl,
        String selloFederacionUrl,
        String selloCentralUrl,
        String selloSindicatoUrl,
        Firmante ejecutivoFederacion,
        Firmante secretarioGeneralCentral,
        Firmante secretarioGeneralSindicato,
        boolean firmaSindicatoObligatoria,
        List<Faltante> faltantes,
        Bloqueo bloqueo,
        boolean completa
) {
    public record Bloqueo(String titulo, String motivo, String reunion,
                          java.time.LocalDate desde, String comoSeLevanta) {
    }

    /** Firma y pie automático que se muestran exactamente como en el PDF. */
    public record Firmante(String nombre, String cargo, String organizacion,
                           String firmaUrl, String pieFirmaUrl) {
    }

    public record Faltante(String campo, String detalle, String donde) {
    }
}
