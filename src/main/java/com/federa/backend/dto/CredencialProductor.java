package com.federa.backend.dto;

/** Datos de una credencial de productor, ya resueltos para dibujar el PDF. */
public record CredencialProductor(
        String federacion,
        String central,
        String sindicato,
        String nombres,
        String apellidos,
        String ci,
        String lotes,
        byte[] foto,
        byte[] selloFederacion,
        byte[] selloCentral,
        byte[] selloSindicato,
        Firmante ejecutivoFederacion,
        Firmante secretarioGeneralCentral,
        Firmante secretarioGeneralSindicato,
        String emitidaEl,
        String codigo,
        String codigoPadron,
        byte[] qr) {

    /** Constructor de compatibilidad para pruebas y consumidores anteriores. */
    public CredencialProductor(String federacion, String central, String sindicato,
                               String nombres, String apellidos, String ci, String lotes,
                               byte[] foto, Firmante secretarioGeneral,
                               Firmante secretarioRelaciones, String emitidaEl,
                               String codigo, String codigoPadron, byte[] qr) {
        this(federacion, central, sindicato, nombres, apellidos, ci, lotes, foto,
                null, null, null, null, secretarioGeneral, secretarioRelaciones,
                emitidaEl, codigo, codigoPadron, qr);
    }

    /** La firma y el pie automático que la identifica. */
    public record Firmante(String nombre, String cargo, String organizacion, byte[] firma) {
        /** Constructor anterior: el tercer archivo era un pie de firma en imagen. */
        public Firmante(String nombre, byte[] firma, byte[] pieFirmaHistorico) {
            this(nombre, "SECRETARIO GENERAL", "", firma);
        }
    }
}
