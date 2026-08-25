package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.CargoResponse;
import com.federa.backend.dto.CredencialPrevia;
import com.federa.backend.dto.EstadoRequest;
import com.federa.backend.dto.ProductorDetalleResponse;
import com.federa.backend.dto.ProductorRequest;
import com.federa.backend.dto.ProductorResponse;
import com.federa.backend.service.CaraCredencial;
import com.federa.backend.service.CredencialService;
import com.federa.backend.service.DirectorioService;
import com.federa.backend.service.ProductorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(ApiRutas.V1 + "/productores")
@Tag(name = "Productores", description =
        "Productor afiliado: una fila del padrón. Solo los nombres y el sindicato son "
        + "obligatorios; cédula y apellidos pueden faltar porque faltan en el padrón real.")
public class ProductorController {

    private final ProductorService productorService;
    private final DirectorioService directorioService;
    private final CredencialService credencialService;

    public ProductorController(ProductorService productorService,
                               DirectorioService directorioService,
                               CredencialService credencialService) {
        this.productorService = productorService;
        this.directorioService = directorioService;
        this.credencialService = credencialService;
    }

    @GetMapping
    @Operation(summary = "Listado paginado del padrón",
            description = "Los tres filtros son opcionales y combinables. El orden siempre "
                    + "termina desempatando por id, para que una misma fila no pueda aparecer "
                    + "en dos páginas distintas.")
    public PagedModel<ProductorResponse> listar(
            @Parameter(description = "Acota a un sindicato") @RequestParam(required = false) Long sindicatoId,
            @Parameter(description = "Acota a una central") @RequestParam(required = false) Long centralId,
            @Parameter(description = "Busca en nombres, apellidos y cédula")
            @RequestParam(required = false) String texto,
            @PageableDefault(size = 25, sort = {"apellidos", "nombres"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        return new PagedModel<>(productorService.listar(sindicatoId, centralId, texto, pageable));
    }

    @GetMapping("/sin-foto")
    @Operation(summary = "Productores sin fotografía cargada",
            description = "En el padrón original son 3.113 de 4.051.")
    public PagedModel<ProductorResponse> sinFoto(
            @PageableDefault(size = 25, sort = {"apellidos", "nombres"}) Pageable pageable) {
        return new PagedModel<>(productorService.sinFoto(pageable));
    }

    @GetMapping("/duplicados/cedulas")
    @Operation(summary = "Cédulas asignadas a más de un productor",
            description = "Devuelve las cédulas, no los productores. Para ver quiénes las "
                    + "comparten, usar /por-cedula/{ci}.")
    public List<String> cedulasDuplicadas() {
        return productorService.cedulasDuplicadas();
    }

    @GetMapping("/por-cedula/{ci}")
    @Operation(summary = "Productores que comparten una cédula")
    public List<ProductorResponse> porCedula(@PathVariable String ci) {
        return productorService.porCedula(ci);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ficha completa: datos del productor, sus lotes y sus imágenes")
    public ProductorDetalleResponse obtener(@PathVariable Long id) {
        return productorService.obtener(id);
    }

    @GetMapping(value = "/{id}/credencial.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarga la credencial del productor",
            description = """
                    Dos páginas del tamaño exacto de una cédula (85,6 × 54 mm, apaisada): el \
                    anverso con la foto y los datos, y el reverso con las firmas del Secretario \
                    General y del Secretario Relaciones del sindicato.

                    Sirve para una impresora de tarjetas y también para imprimir a doble cara \
                    en tamaño real y recortar por el contorno.

                    Exige que los datos estén completos. Si falta la foto, la cédula, la sigla \
                    de la central o la firma de alguno de los dos cargos que firman, responde \
                    409 diciendo qué falta en vez de emitir una tarjeta a medias: una credencial \
                    sale plastificada y se reparte, y rehacerla cuesta más que completarla \
                    antes. Consultá `/credencial/previa` para verlo con detalle.""")
    public ResponseEntity<byte[]> credencial(
            @PathVariable Long id,
            @RequestParam(defaultValue = "COMPLETA") CaraCredencial cara) {
        return comoAdjunto(credencialService.generar(id, cara));
    }

    @GetMapping("/{id}/credencial/previa")
    @Operation(summary = "Vista previa de la credencial, con lo que falta para emitirla",
            description = """
                    Los mismos datos que van a salir impresos, leídos igual que los lee el \
                    generador, más la lista de lo que falta.

                    Mientras `completa` sea false, la descarga del PDF va a devolver 409. Cada \
                    faltante dice qué es y en qué pantalla se carga.""")
    public CredencialPrevia previaDeCredencial(@PathVariable Long id) {
        return credencialService.previa(id);
    }

    static ResponseEntity<byte[]> comoAdjunto(CredencialService.Descarga descarga) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + descarga.nombreArchivo() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(descarga.contenido());
    }

    @GetMapping("/{id}/cargos")
    @Operation(summary = "Cargos del directorio que ocupó este productor",
            description = "Del período más reciente al más antiguo, incluidos los de sindicatos "
                    + "anteriores: el padrón mueve gente entre bases y su historial no se "
                    + "pierde al cambiar de sindicato.")
    public List<CargoResponse> cargos(@PathVariable Long id) {
        return directorioService.historialDeProductor(id);
    }

    @PostMapping
    @Operation(summary = "Registra un productor",
            description = "Nombres y apellidos se guardan normalizados en mayúsculas y sin tildes, "
                    + "siguiendo la convención de la planilla.")
    public ResponseEntity<ProductorResponse> crear(@Valid @RequestBody ProductorRequest request) {
        ProductorResponse creado = productorService.crear(request);
        return ResponseEntity.created(URI.create(ApiRutas.V1 + "/productores/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza un productor o lo mueve a otro sindicato")
    public ProductorResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductorRequest request) {
        return productorService.actualizar(id, request);
    }

    @PatchMapping("/{id}/confirmar-correccion-nombre")
    @Operation(summary = "Confirma la corrección de nombre propuesta en la revisión",
            description = "Promueve los campos 'Nombre x' y 'Apellido x' de la planilla a los "
                    + "campos definitivos y limpia la propuesta.")
    public ProductorResponse confirmarCorreccionNombre(@PathVariable Long id) {
        return productorService.confirmarCorreccionNombre(id);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Habilita o deshabilita un productor",
            description = "Deshabilitar no borra nada: la fila queda con todas sus "
                    + "relaciones y se puede volver a habilitar cuando haga falta. Es la "
                    + "alternativa para lo que no se deja eliminar por tener registros "
                    + "dependientes.")
    public ProductorResponse cambiarEstado(@PathVariable Long id,
                                        @Valid @RequestBody EstadoRequest request) {
        return productorService.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un productor",
            description = "Arrastra en cascada sus imágenes y sus períodos de tenencia. Sus "
                    + "lotes no: la tierra pertenece al sindicato y se queda ahí.")
    public void eliminar(@PathVariable Long id) {
        productorService.eliminar(id);
    }
}
