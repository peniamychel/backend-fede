package com.federa.backend.dto;

import com.federa.backend.almacen.AlmacenLocal;
import com.federa.backend.model.ImagenProductor;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Lote;
import com.federa.backend.model.TenenciaLote;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.EstadoRevisionSieProductor;
import com.federa.backend.model.enums.TipoImagen;
import com.federa.backend.util.CodigoPadron;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;

@Schema(description = "Productor afiliado, con su sindicato y central resueltos.")
public record ProductorResponse(

        @Schema(description = "Identificador interno.", example = "812")
        Long id,

        @Schema(description = "Nombres en mayúsculas, conservando Ñ y tildes.", example = "JOSÉ")
        String nombres,

        @Schema(description = "Apellidos. Puede ser null.", example = "HINOJOSA LA FUENTE")
        String apellidos,

        @Schema(description = "Nombre y apellido ya armados, tomando la corrección si existe.",
                example = "CONSTANTINA HINOJOSA LAFUENTE")
        String nombreCompleto,

        @Schema(description = "Cédula de identidad. Puede ser null y puede repetirse.",
                example = "913516")
        String ci,

        @Schema(description = "Corrección de nombre pendiente de confirmar. Null si no hay.",
                example = "CONSTANTINA")
        String nombresCorregidos,

        @Schema(description = "Corrección de apellido pendiente de confirmar.",
                example = "HINOJOSA LAFUENTE")
        String apellidosCorregidos,

        @Schema(description = "Rótulo de la fotografía archivada.",
                example = "Constantina Hinojosa, 1ro de Mayo")
        String fotoDescripcion,

        @Schema(description = "Derivado: si hay rótulo de foto cargado.", example = "true")
        boolean tieneFoto,

        @Schema(description = "Marca manual de seguimiento.", example = "false")
        boolean marcado,

        @Schema(description = "Id del sindicato.", example = "17")
        Long sindicatoId,

        @Schema(description = "Nombre del sindicato.", example = "1RO DE MAYO")
        String sindicatoNombre,

        @Schema(description = "Id de la central.", example = "4")
        Long centralId,

        @Schema(description = "Nombre de la central.", example = "13 DE JUNIO")
        String centralNombre,

        @Schema(description = "URL de la miniatura, para mostrarla en el listado. Null si el "
                + "productor no tiene foto cargada. Ojo: es distinto de `tieneFoto`, que "
                + "indica si la planilla trae el rótulo de la fotografía archivada en papel.",
                example = "/api/v1/archivos/productores/15/MINIATURA-a1b2c3d4.jpg")
        String miniaturaUrl,

        @Schema(description = "URL de la fotografía completa. Null si no tiene.",
                example = "/api/v1/archivos/productores/15/ORIGINAL-e5f6g7h8.jpg")
        String fotoUrl,

        @Schema(description = "Código de su credencial: lo que dice el QR y lo que se "
                + "escribe a mano cuando la cámara no lee.", example = "AB12CD34EF")
        String codigo,

        @Schema(description = "Código en el padrón: número de la federación, sigla de la "
                + "central y correlativo. La letra A-H, si corresponde, se muestra junto "
                + "al número de lote. Null mientras falte alguna parte base.",
                example = "2-13J-78")
        String codigoPadron,

        @Schema(description = "Si al abrir la ficha se debe hacer su única revisión SIE.",
                example = "true")
        boolean revisionSiePendiente,

        @Schema(description = "Resultado persistente de la última revisión SIE.")
        EstadoRevisionSieProductor revisionSieEstado,

        @Schema(description = "Mensaje persistente que explica el resultado de SIE.")
        String revisionSieMensaje,

        @Schema(description = "Nombres sugeridos por SIE, pendientes de aceptación.")
        String sieNombresSugeridos,

        @Schema(description = "Apellidos sugeridos por SIE, pendientes de aceptación.")
        String sieApellidosSugeridos,

        @Schema(description = "Si el estado naranja de SIE impide imprimir la credencial.")
        boolean revisionSieBloqueaImpresion,

        @Schema(description = "Si una revision manual lo dejo observado y excluido de impresion.")
        boolean observado,

        @Schema(description = "Motivo de la observacion manual. Null cuando no esta observado.")
        String observacion,

        @Schema(description = "Cantidad de veces que se confirmó la impresión del anverso.")
        int credencialImpresiones,

        @Schema(description = "Fecha y hora de la última impresión confirmada.")
        LocalDateTime credencialUltimaImpresion,

        @Schema(description = "Si tiene foto, lote numerado y los datos personales mínimos para imprimir.")
        boolean credencialLista,

        @Schema(description = "Clasificación del lote vigente o la declarada mientras falta asignarlo.")
        EstadoLote clasificacion,

        @Schema(description = "Falta una parcela vigente con número. Aplica también a registros históricos.")
        boolean revisionLotePendiente,

        Auditoria auditoria
) {

    /**
     * Para respuestas de un solo productor. Lee sus imágenes por la relación,
     * que en un registro suelto no cuesta nada.
     */
    public static ProductorResponse desde(Productor p) {
        Map<TipoImagen, String> imagenes = new EnumMap<>(TipoImagen.class);
        for (ImagenProductor imagen : p.getImagenes()) {
            imagenes.put(imagen.getTipo(), AlmacenLocal.RUTA_PUBLICA + imagen.getClave());
        }
        return desde(p, imagenes);
    }

    /**
     * Para listados. Las URL llegan de una consulta en bloque, no de la
     * relación: recorrerla por fila haría una consulta por productor y con
     * 4.051 registros eso es inviable.
     */
    public static ProductorResponse desde(Productor p, Map<TipoImagen, String> imagenes) {
        return desde(p, imagenes, p.getTenencias());
    }

    /** Las tenencias de listados se cargan en bloque, no una consulta por fila. */
    public static ProductorResponse desde(Productor p, Map<TipoImagen, String> imagenes,
                                          List<TenenciaLote> tenencias) {
        List<Lote> vigentes = tenencias.stream().filter(TenenciaLote::estaVigente)
                .map(TenenciaLote::getLote).toList();
        boolean faltaNumero = vigentes.stream().noneMatch(l ->
                l.getNumero() != null && !l.getNumero().isBlank());
        EstadoLote clasificacion = vigentes.isEmpty() ? p.getClasificacionPendiente()
                : vigentes.get(0).getEstadoLote();
        return new ProductorResponse(
                p.getId(),
                p.getNombres(),
                p.getApellidos(),
                p.getNombreCompleto(),
                p.getCi(),
                p.getNombresCorregidos(),
                p.getApellidosCorregidos(),
                p.getFotoDescripcion(),
                p.isTieneFoto(),
                p.isMarcado(),
                p.getSindicato().getId(),
                p.getSindicato().getNombre(),
                p.getSindicato().getCentral().getId(),
                p.getSindicato().getCentral().getNombre(),
                imagenes.get(TipoImagen.MINIATURA),
                imagenes.get(TipoImagen.ORIGINAL),
                p.getCodigo(),
                CodigoPadron.de(p),
                p.isRevisionSiePendiente(),
                p.getRevisionSieEstado(),
                p.getRevisionSieMensaje(),
                p.getSieNombresSugeridos(),
                p.getSieApellidosSugeridos(),
                p.isRevisionSieBloqueaImpresion(),
                p.isObservado(),
                p.getObservacionManual(),
                p.getCredencialImpresiones(),
                p.getCredencialUltimaImpresion(),
                !faltaNumero && credencialLista(p, imagenes),
                clasificacion,
                faltaNumero,
                Auditoria.desde(p));
    }

    private static boolean credencialLista(Productor p, Map<TipoImagen, String> imagenes) {
        String apellidos = p.getApellidosCorregidos() != null
                ? p.getApellidosCorregidos() : p.getApellidos();
        return p.isEstado()
                && !p.isObservado()
                && !p.isRevisionSieBloqueaImpresion()
                && apellidos != null && !apellidos.isBlank()
                && p.getCi() != null && !p.getCi().isBlank()
                && p.getCorrelativo() != null
                && CodigoPadron.de(p) != null
                && imagenes.get(TipoImagen.MINIATURA) != null;
    }
}
