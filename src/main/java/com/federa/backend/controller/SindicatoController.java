package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.EstadoRequest;
import com.federa.backend.dto.SindicatoRequest;
import com.federa.backend.dto.SindicatoResponse;
import com.federa.backend.dto.UbicacionRequest;
import com.federa.backend.service.CredencialService;
import com.federa.backend.service.InformeSindicatoService;
import com.federa.backend.service.LoteService;
import com.federa.backend.service.SindicatoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(ApiRutas.V1 + "/sindicatos")
@Tag(name = "Sindicatos", description =
        "Unidad de base, pertenece a una central. El padrón tiene 107. "
        + "El nombre solo es único dentro de su central: hay homónimos en centrales distintas.")
public class SindicatoController {

    private final SindicatoService sindicatoService;
    private final LoteService loteService;
    private final InformeSindicatoService informeService;
    private final CredencialService credencialService;

    public SindicatoController(SindicatoService sindicatoService,
                               LoteService loteService,
                               InformeSindicatoService informeService,
                               CredencialService credencialService) {
        this.sindicatoService = sindicatoService;
        this.loteService = loteService;
        this.informeService = informeService;
        this.credencialService = credencialService;
    }

    @GetMapping
    @Operation(summary = "Lista sindicatos, opcionalmente filtrados por central")
    public List<SindicatoResponse> listar(
            @Parameter(description = "Si se omite, devuelve los sindicatos de todas las centrales")
            @RequestParam(required = false) Long centralId) {
        return sindicatoService.listar(centralId);
    }

    @GetMapping("/con-ubicacion")
    @Operation(summary = "Sindicatos que ya tienen la sede marcada",
            description = "Pensado para dibujarlos todos juntos en un mapa. Deja fuera a los "
                    + "que todavía no tienen coordenadas.")
    public List<SindicatoResponse> conUbicacion(
            @Parameter(description = "Acota a una central. Si se omite, trae los de todas.")
            @RequestParam(required = false) Long centralId) {
        return sindicatoService.listarConUbicacion(centralId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un sindicato por id")
    public SindicatoResponse obtener(@PathVariable Long id) {
        return sindicatoService.obtener(id);
    }

    @GetMapping(value = "/{id}/informe.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga la nómina del sindicato en PDF",
            description = """
                    Reproduce la planilla que la federación venía imprimiendo desde Excel: \
                    encabezado con la federación, la central y el sindicato, una fila por \
                    productor ordenada por apellido, y el acta de entrega con los tres bloques \
                    de firma al final.

                    Los productores salen con su C.I., sus números de lote y su carnet de \
                    productor. La columna de observaciones se imprime en blanco, para anotar \
                    a mano sobre el papel.

                    Si el sindicato tiene Secretario General vigente con la firma cargada, se estampa \
                    en el bloque DIRIGENTE/ENTREGUE. Si no, ese espacio queda en blanco para \
                    firmar a mano.

                    Los campos N° FEDERACIÓN y N° CENTRAL van como línea vacía, porque todavía \
                    no son parte del sindicato.""")
    public ResponseEntity<byte[]> informe(@PathVariable Long id) {
        InformeSindicatoService.Descarga descarga = informeService.generar(id);

        return ResponseEntity.ok()
                // attachment: se baja como archivo en vez de abrirse dentro de
                // la pestaña, que es lo que se quiere para llevarlo a imprimir.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + descarga.nombreArchivo() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(descarga.contenido());
    }

    @GetMapping(value = "/{id}/credenciales.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga las credenciales de todo el sindicato",
            description = """
                    Las credenciales de todos sus productores, ordenadas por apellido, en hojas \
                    carta de diez tarjetas cada una.

                    Las hojas van intercaladas: una de anversos y a continuación la de sus \
                    reversos, con las columnas invertidas para que al imprimir a doble cara y \
                    voltear el papel por el lado largo cada reverso caiga detrás de su anverso. \
                    Si se imprime a una sola cara, las hojas pares se descartan.

                    Devuelve 409 si el sindicato no tiene productores, si tiene más de los que \
                    admite un pliego, o si a alguna credencial le faltan datos: el pliego se \
                    imprime a doble cara y se recorta, así que una tarjeta incompleta en el \
                    medio obliga a rehacer la hoja entera. Consultá `/credenciales/previa` para \
                    ver a quiénes les falta qué.""")
    public ResponseEntity<byte[]> credenciales(@PathVariable Long id) {
        return ProductorController.comoAdjunto(credencialService.generarDeSindicato(id));
    }

    @GetMapping("/{id}/credenciales/previa")
    @Operation(summary = "Vista previa del pliego: cuántas salen y a quiénes les falta algo",
            description = """
                    Separa lo que le falta al sindicato —que le falta a todas las credenciales \
                    por igual, como la sigla de la central o la firma de un dirigente— de lo que \
                    le falta a cada productor.

                    Mientras `completa` sea false, la descarga del pliego va a devolver 409.""")
    public CredencialService.PliegoPrevio previaDeCredenciales(@PathVariable Long id) {
        return credencialService.previaDeSindicato(id);
    }

    @GetMapping("/{id}/credenciales/impresion")
    @Operation(summary = "Panel de impresión masiva de credenciales del sindicato")
    public CredencialService.PanelImpresionSindicato panelImpresion(@PathVariable Long id) {
        return credencialService.panelImpresionSindicato(id);
    }

    @PostMapping(value = "/{id}/credenciales/impresion/anversos.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Genera los anversos seleccionados como páginas CR80")
    public ResponseEntity<byte[]> anversosSeleccionados(
            @PathVariable Long id,
            @RequestBody CredencialService.SeleccionImpresion seleccion) {
        return ProductorController.comoAdjunto(
                credencialService.generarAnversosSindicato(id, seleccion));
    }

    @PostMapping("/{id}/credenciales/impresion/confirmar")
    @Operation(summary = "Registra los anversos aceptados por la impresora de Windows")
    public CredencialService.PanelImpresionSindicato confirmarAnversos(
            @PathVariable Long id,
            @RequestBody CredencialService.SeleccionImpresion seleccion) {
        return credencialService.confirmarAnversosImpresos(id, seleccion);
    }

    @PatchMapping("/{id}/credenciales/impresion/ultimo-grupo")
    @Operation(summary = "Corrige cuáles tarjetas salieron en la última tanda masiva")
    public CredencialService.PanelImpresionSindicato revisarUltimoGrupo(
            @PathVariable Long id,
            @RequestBody CredencialService.RevisionGrupoImpresion revision) {
        return credencialService.revisarUltimoGrupo(id, revision);
    }

    @GetMapping(value = "/{id}/credenciales/impresion/reversos.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Genera una cantidad de reversos idénticos en páginas CR80")
    public ResponseEntity<byte[]> reversos(
            @PathVariable Long id,
            @RequestParam int cantidad) {
        return ProductorController.comoAdjunto(
                credencialService.generarReversosSindicato(id, cantidad));
    }

    @PutMapping("/{id}/ubicacion")
    @Operation(summary = "Marca o mueve la sede del sindicato",
            description = "Recibe las coordenadas en grados decimales, tal como las entrega el "
                    + "mapa. Las dos son obligatorias: media coordenada no ubica nada.")
    public SindicatoResponse marcarUbicacion(@PathVariable Long id,
                                             @Valid @RequestBody UbicacionRequest request) {
        return sindicatoService.marcarUbicacion(id, request);
    }

    @DeleteMapping("/{id}/ubicacion")
    @Operation(summary = "Quita la ubicación del sindicato",
            description = "El sindicato sigue existiendo; solo deja de tener sede marcada. Por "
                    + "eso devuelve el sindicato y no un 204.")
    public SindicatoResponse borrarUbicacion(@PathVariable Long id) {
        return sindicatoService.borrarUbicacion(id);
    }

    @GetMapping("/{id}/lotes-duplicados")
    @Operation(summary = "Números de lote repetidos dentro del sindicato",
            description = "El hallazgo más frecuente de la revisión del padrón: 425 casos.")
    public List<String> lotesDuplicados(@PathVariable Long id) {
        sindicatoService.obtener(id);
        return loteService.numerosDuplicados(id);
    }

    @PostMapping
    @Operation(summary = "Crea un sindicato",
            description = "Devuelve 409 si la central ya tiene otro sindicato con ese nombre.")
    public ResponseEntity<SindicatoResponse> crear(@Valid @RequestBody SindicatoRequest request) {
        SindicatoResponse creado = sindicatoService.crear(request);
        return ResponseEntity.created(URI.create(ApiRutas.V1 + "/sindicatos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Renombra un sindicato o lo mueve a otra central")
    public SindicatoResponse actualizar(@PathVariable Long id, @Valid @RequestBody SindicatoRequest request) {
        return sindicatoService.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Habilita o deshabilita un sindicato",
            description = "Deshabilitar no borra nada: la fila queda con todas sus "
                    + "relaciones y se puede volver a habilitar cuando haga falta. Es la "
                    + "alternativa para lo que no se deja eliminar por tener registros "
                    + "dependientes.")
    public SindicatoResponse cambiarEstado(@PathVariable Long id,
                                        @Valid @RequestBody EstadoRequest request) {
        return sindicatoService.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un sindicato",
            description = "Devuelve 409 si todavía tiene productores.")
    public void eliminar(@PathVariable Long id) {
        sindicatoService.eliminar(id);
    }
}
