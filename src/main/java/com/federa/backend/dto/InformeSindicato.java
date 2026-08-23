package com.federa.backend.dto;

import java.util.List;

/**
 * Todo lo que necesita el informe de un sindicato, ya resuelto.
 * <p>
 * Existe para separar las dos mitades del trabajo: el servicio arma esto
 * consultando la base, y el generador lo dibuja. Así el PDF se puede probar
 * con datos inventados, sin levantar Spring ni MariaDB.
 * <p>
 * No sale por la API: es un intermediario entre dos clases del servidor.
 *
 * @param federacion  encabezado de la primera línea, ya en mayúsculas.
 * @param central     nombre de la central.
 * @param sindicato   nombre del sindicato; se repite en el pie de cada página.
 * @param filas       productores, en el orden en que se imprimen.
 * @param dirigente   presidente vigente, si lo hay, para el bloque de firmas.
 * @param anio        año que se imprime en el acta de entrega.
 */
public record InformeSindicato(
        String federacion,
        String central,
        String sindicato,
        List<Fila> filas,
        Dirigente dirigente,
        int anio) {

    /**
     * Una línea de la tabla. Todos los campos son texto ya formateado: el
     * generador no decide nada sobre el contenido, solo lo ubica.
     *
     * @param numero       correlativo dentro del sindicato, arrancando en 1.
     * @param lotes        números de lote unidos por coma, "" si no tiene.
     * @param codigoPadron el código con el que se lo nombra, "" si todavía no
     *                     se puede armar.
     */
    public record Fila(
            int numero,
            String nombres,
            String apellidos,
            String ci,
            String lotes,
            String codigoPadron) {
    }

    /**
     * Presidente del sindicato para el pie del acta.
     * <p>
     * Las dos imágenes son opcionales por separado: puede haber presidente sin
     * firma cargada, y en ese caso el acta deja el espacio en blanco para que
     * firme a mano, igual que la planilla que se usaba antes.
     *
     * @param nombre     nombre completo, para imprimirlo bajo la línea.
     * @param firma      JPEG de la firma, o null.
     * @param pieDeFirma texto que se imprime debajo de la firma, o null.
     */
    public record Dirigente(String nombre, byte[] firma, String pieDeFirma) {
    }
}
