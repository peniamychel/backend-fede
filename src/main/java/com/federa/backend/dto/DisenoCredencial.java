package com.federa.backend.dto;

import java.util.List;

/**
 * Distribución editable de los elementos que se imprimen sobre las dos
 * plantillas de la credencial. Todas las medidas están en puntos PDF y el eje
 * Y nace en el borde inferior, igual que OpenPDF.
 */
public record DisenoCredencial(float ancho, float alto, List<Elemento> elementos) {

    public enum Cara { CARA, REVERSO }
    public enum Tipo { TEXTO, IMAGEN, PIE_FIRMA }
    public enum Alineacion { IZQUIERDA, CENTRO, DERECHA }

    public record Elemento(
            String id,
            Cara cara,
            Tipo tipo,
            String campo,
            String etiqueta,
            float x,
            float y,
            float ancho,
            float alto,
            float tamanoFuente,
            boolean negrita,
            Alineacion alineacion,
            String color,
            String texto) {
    }

    public record CampoDisponible(String campo, String etiqueta, Tipo tipo) {
    }

    public record Editor(DisenoCredencial diseno, List<CampoDisponible> camposDisponibles) {
    }

    public static DisenoCredencial porDefecto() {
        float margen = 8f;
        float bloque = (242.65f - 4 * margen) / 3f;
        float xFederacion = margen;
        float xCentral = 2 * margen + bloque;
        float xSindicato = 3 * margen + 2 * bloque;

        return new DisenoCredencial(242.65f, 153.01f, List.of(
                texto("numero-padron", Cara.CARA, "CODIGO_PADRON", "N° de padrón",
                        31f, 89.5f, 45f, 10f, 10f, true, "#5A0F0A"),
                texto("nombre-completo", Cara.CARA, "NOMBRE_COMPLETO", "Nombre completo",
                        65f, 75.7f, 102f, 8f, 8f, true, "#000000"),
                texto("sindicato", Cara.CARA, "SINDICATO", "Sindicato",
                        65f, 63.8f, 102f, 8f, 8f, true, "#000000"),
                texto("central", Cara.CARA, "CENTRAL", "Central",
                        65f, 52.2f, 102f, 8f, 8f, true, "#000000"),
                texto("federacion", Cara.CARA, "FEDERACION", "Federación",
                        65f, 40.2f, 102f, 8f, 8f, true, "#000000"),
                texto("lotes", Cara.CARA, "LOTES", "N° de lote",
                        65f, 28.1f, 102f, 8f, 8f, true, "#000000"),
                imagen("foto", Cara.CARA, "FOTO", "Fotografía",
                        172f, 18.1f, 57.6f, 57f),

                imagen("sello-federacion", Cara.REVERSO, "SELLO_FEDERACION", "Sello federación",
                        xFederacion + 7f, 35f, bloque - 14f, 27f),
                imagen("firma-federacion", Cara.REVERSO, "FIRMA_FEDERACION", "Firma ejecutivo",
                        xFederacion + 3f, 22f, bloque - 6f, 18f),
                pie("pie-federacion", "PIE_FEDERACION", "Pie federación", xFederacion, bloque),

                imagen("sello-central", Cara.REVERSO, "SELLO_CENTRAL", "Sello central",
                        xCentral + 7f, 35f, bloque - 14f, 27f),
                imagen("firma-central", Cara.REVERSO, "FIRMA_CENTRAL", "Firma secretario central",
                        xCentral + 3f, 22f, bloque - 6f, 18f),
                pie("pie-central", "PIE_CENTRAL", "Pie central", xCentral, bloque),

                imagen("sello-sindicato", Cara.REVERSO, "SELLO_SINDICATO", "Sello sindicato",
                        xSindicato + 7f, 35f, bloque - 14f, 27f),
                imagen("firma-sindicato", Cara.REVERSO, "FIRMA_SINDICATO", "Firma secretario sindicato",
                        xSindicato + 3f, 22f, bloque - 6f, 18f),
                pie("pie-sindicato", "PIE_SINDICATO", "Pie sindicato", xSindicato, bloque)
        ));
    }

    public static List<CampoDisponible> catalogo() {
        return List.of(
                campo("CODIGO_PADRON", "N° de padrón", Tipo.TEXTO),
                campo("NOMBRE_COMPLETO", "Nombre completo", Tipo.TEXTO),
                campo("NOMBRES", "Nombres", Tipo.TEXTO),
                campo("APELLIDOS", "Apellidos", Tipo.TEXTO),
                campo("CI", "Cédula de identidad", Tipo.TEXTO),
                campo("SINDICATO", "Sindicato", Tipo.TEXTO),
                campo("CENTRAL", "Central", Tipo.TEXTO),
                campo("FEDERACION", "Federación", Tipo.TEXTO),
                campo("LOTES", "N° de lote", Tipo.TEXTO),
                campo("FECHA_EMISION", "Fecha de emisión", Tipo.TEXTO),
                campo("CODIGO_CREDENCIAL", "Código de credencial", Tipo.TEXTO),
                campo("TEXTO_FIJO", "Texto libre", Tipo.TEXTO),
                campo("FOTO", "Fotografía", Tipo.IMAGEN),
                campo("QR", "Código QR", Tipo.IMAGEN),
                campo("SELLO_FEDERACION", "Sello federación", Tipo.IMAGEN),
                campo("SELLO_CENTRAL", "Sello central", Tipo.IMAGEN),
                campo("SELLO_SINDICATO", "Sello sindicato", Tipo.IMAGEN),
                campo("FIRMA_FEDERACION", "Firma ejecutivo federación", Tipo.IMAGEN),
                campo("FIRMA_CENTRAL", "Firma secretario central", Tipo.IMAGEN),
                campo("FIRMA_SINDICATO", "Firma secretario sindicato", Tipo.IMAGEN),
                campo("PIE_FEDERACION", "Pie de firma federación", Tipo.PIE_FIRMA),
                campo("PIE_CENTRAL", "Pie de firma central", Tipo.PIE_FIRMA),
                campo("PIE_SINDICATO", "Pie de firma sindicato", Tipo.PIE_FIRMA)
        );
    }

    private static Elemento texto(String id, Cara cara, String campo, String etiqueta,
                                  float x, float y, float ancho, float alto,
                                  float fuente, boolean negrita, String color) {
        return new Elemento(id, cara, Tipo.TEXTO, campo, etiqueta, x, y, ancho, alto,
                fuente, negrita, Alineacion.IZQUIERDA, color, "");
    }

    private static Elemento imagen(String id, Cara cara, String campo, String etiqueta,
                                   float x, float y, float ancho, float alto) {
        return new Elemento(id, cara, Tipo.IMAGEN, campo, etiqueta, x, y, ancho, alto,
                5.5f, false, Alineacion.CENTRO, "#000000", "");
    }

    private static Elemento pie(String id, String campo, String etiqueta,
                                float x, float ancho) {
        return new Elemento(id, Cara.REVERSO, Tipo.PIE_FIRMA, campo, etiqueta,
                x, 0f, ancho, 21f, 4.2f, true, Alineacion.CENTRO, "#000000", "");
    }

    private static CampoDisponible campo(String nombre, String etiqueta, Tipo tipo) {
        return new CampoDisponible(nombre, etiqueta, tipo);
    }
}
