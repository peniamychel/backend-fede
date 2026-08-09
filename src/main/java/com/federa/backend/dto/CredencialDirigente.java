package com.federa.backend.dto;

/**
 * Datos de la credencial de un dirigente, ya resueltos.
 * <p>
 * Acredita a alguien en un cargo del directorio, y por eso lleva su propia
 * firma y su sello: quien recibe un documento firmado por el presidente de un
 * sindicato puede contrastar la firma contra la credencial.
 *
 * @param cargo      cómo se escribe el cargo: "Presidente", "Secretario".
 * @param nivel      de qué nivel es: "Sindicato", "Central", "Federación".
 * @param lugar      nombre del sindicato, la central o la federación.
 * @param central    central a la que pertenece, o null si el nivel ya es la
 *                   federación y no hay una por encima.
 * @param periodo    "desde el 01/03/2026" o "01/03/2026 — 08/08/2026".
 * @param foto       miniatura del productor, o null.
 * @param firma      su firma, o null si no se cargó.
 * @param sello      su pie de firma, o null.
 * @param emitidaEl  fecha de emisión ya formateada.
 */
public record CredencialDirigente(
        String federacion,
        String cargo,
        String nivel,
        String lugar,
        String central,
        String nombres,
        String apellidos,
        String ci,
        String periodo,
        byte[] foto,
        byte[] firma,
        byte[] sello,
        String emitidaEl,
        String codigo,
        byte[] qr) {
}
