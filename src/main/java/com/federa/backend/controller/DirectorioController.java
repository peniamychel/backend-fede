package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.AsignarCargoRequest;
import com.federa.backend.dto.CargoResponse;
import com.federa.backend.dto.DirectorioResponse;
import com.federa.backend.dto.ProductorResponse;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.TipoCargo;
import com.federa.backend.service.DirectorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Directorios de los tres niveles.
 * <p>
 * Las rutas cuelgan de cada recurso —{@code /sindicatos/7/directorio},
 * {@code /centrales/4/directorio}, {@code /federaciones/1/directorio}— porque
 * es donde las va a buscar quien lee la API. Por dentro todas llaman al mismo
 * servicio pasándole el ámbito, así que la lógica está escrita una sola vez.
 */
@RestController
@RequestMapping(ApiRutas.V1)
@Tag(name = "Directorio", description =
        "Quién dirige cada sindicato, cada central y la federación. Cada nivel tiene sus "
        + "cargos: el sindicato presidente y secretario; la central suma haciendas; la "
        + "federación suma además vocal. Solo puede haber uno de cada cargo a la vez y nadie "
        + "puede ocupar dos cargos simultáneos, en ningún nivel.")
public class DirectorioController {

    private final DirectorioService directorioService;

    public DirectorioController(DirectorioService directorioService) {
        this.directorioService = directorioService;
    }

    // ---------------------------------------------------------- sindicatos

    @GetMapping("/sindicatos/{id}/directorio")
    @Operation(summary = "Directorio en funciones del sindicato",
            description = "Devuelve un puesto por cargo del nivel, ocupado o vacante.")
    public DirectorioResponse deSindicato(@PathVariable Long id) {
        return directorioService.obtener(Ambito.SINDICATO, id);
    }

    @GetMapping("/sindicatos/{id}/directorio/historial")
    @Operation(summary = "Todos los períodos, del más reciente al más antiguo")
    public List<CargoResponse> historialDeSindicato(@PathVariable Long id) {
        return directorioService.historial(Ambito.SINDICATO, id);
    }

    @GetMapping("/sindicatos/{id}/directorio/candidatos")
    @Operation(summary = "Productores que pueden ocupar un cargo del sindicato",
            description = DESCRIPCION_CANDIDATOS)
    public List<ProductorResponse> candidatosDeSindicato(@PathVariable Long id) {
        return directorioService.candidatos(Ambito.SINDICATO, id);
    }

    @PutMapping("/sindicatos/{id}/directorio/{cargo}")
    @Operation(summary = "Asigna un cargo del sindicato", description = DESCRIPCION_ASIGNAR)
    public DirectorioResponse asignarEnSindicato(
            @PathVariable Long id,
            @Parameter(description = "PRESIDENTE o SECRETARIO.", example = "presidente")
            @PathVariable TipoCargo cargo,
            @Valid @RequestBody AsignarCargoRequest peticion) {
        return directorioService.asignar(Ambito.SINDICATO, id, cargo, peticion);
    }

    @DeleteMapping("/sindicatos/{id}/directorio/{cargo}")
    @Operation(summary = "Deja vacante un cargo del sindicato",
            description = DESCRIPCION_TERMINAR)
    public DirectorioResponse terminarEnSindicato(
            @PathVariable Long id,
            @PathVariable TipoCargo cargo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return directorioService.terminar(Ambito.SINDICATO, id, cargo, hasta);
    }

    // ------------------------------------------------------------ centrales

    @GetMapping("/centrales/{id}/directorio")
    @Operation(summary = "Directorio en funciones de la central",
            description = "Presidente, secretario y haciendas.")
    public DirectorioResponse deCentral(@PathVariable Long id) {
        return directorioService.obtener(Ambito.CENTRAL, id);
    }

    @GetMapping("/centrales/{id}/directorio/historial")
    @Operation(summary = "Todos los períodos, del más reciente al más antiguo")
    public List<CargoResponse> historialDeCentral(@PathVariable Long id) {
        return directorioService.historial(Ambito.CENTRAL, id);
    }

    @GetMapping("/centrales/{id}/directorio/candidatos")
    @Operation(summary = "Productores que pueden ocupar un cargo de la central",
            description = "Salen de todos los sindicatos de la central. " + DESCRIPCION_CANDIDATOS)
    public List<ProductorResponse> candidatosDeCentral(@PathVariable Long id) {
        return directorioService.candidatos(Ambito.CENTRAL, id);
    }

    @PutMapping("/centrales/{id}/directorio/{cargo}")
    @Operation(summary = "Asigna un cargo de la central", description = DESCRIPCION_ASIGNAR)
    public DirectorioResponse asignarEnCentral(
            @PathVariable Long id,
            @Parameter(description = "PRESIDENTE, SECRETARIO o HACIENDAS.", example = "haciendas")
            @PathVariable TipoCargo cargo,
            @Valid @RequestBody AsignarCargoRequest peticion) {
        return directorioService.asignar(Ambito.CENTRAL, id, cargo, peticion);
    }

    @DeleteMapping("/centrales/{id}/directorio/{cargo}")
    @Operation(summary = "Deja vacante un cargo de la central",
            description = DESCRIPCION_TERMINAR)
    public DirectorioResponse terminarEnCentral(
            @PathVariable Long id,
            @PathVariable TipoCargo cargo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return directorioService.terminar(Ambito.CENTRAL, id, cargo, hasta);
    }

    // ---------------------------------------------------------- federación

    @GetMapping("/federaciones/{id}/directorio")
    @Operation(summary = "Directorio en funciones de la federación",
            description = "Presidente, secretario, haciendas y vocal.")
    public DirectorioResponse deFederacion(@PathVariable Long id) {
        return directorioService.obtener(Ambito.FEDERACION, id);
    }

    @GetMapping("/federaciones/{id}/directorio/historial")
    @Operation(summary = "Todos los períodos, del más reciente al más antiguo")
    public List<CargoResponse> historialDeFederacion(@PathVariable Long id) {
        return directorioService.historial(Ambito.FEDERACION, id);
    }

    @GetMapping("/federaciones/{id}/directorio/candidatos")
    @Operation(summary = "Productores que pueden ocupar un cargo de la federación",
            description = "Salen de toda la federación. " + DESCRIPCION_CANDIDATOS)
    public List<ProductorResponse> candidatosDeFederacion(@PathVariable Long id) {
        return directorioService.candidatos(Ambito.FEDERACION, id);
    }

    @PutMapping("/federaciones/{id}/directorio/{cargo}")
    @Operation(summary = "Asigna un cargo de la federación", description = DESCRIPCION_ASIGNAR)
    public DirectorioResponse asignarEnFederacion(
            @PathVariable Long id,
            @Parameter(description = "PRESIDENTE, SECRETARIO, HACIENDAS o VOCAL.",
                    example = "vocal")
            @PathVariable TipoCargo cargo,
            @Valid @RequestBody AsignarCargoRequest peticion) {
        return directorioService.asignar(Ambito.FEDERACION, id, cargo, peticion);
    }

    @DeleteMapping("/federaciones/{id}/directorio/{cargo}")
    @Operation(summary = "Deja vacante un cargo de la federación",
            description = DESCRIPCION_TERMINAR)
    public DirectorioResponse terminarEnFederacion(
            @PathVariable Long id,
            @PathVariable TipoCargo cargo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return directorioService.terminar(Ambito.FEDERACION, id, cargo, hasta);
    }

    // ------------------------------------------------------------- textos

    private static final String DESCRIPCION_CANDIDATOS = """
            Quedan fuera los que ya ocupan un cargo en cualquier nivel —nadie puede ocupar dos \
            a la vez— y los productores deshabilitados.""";

    private static final String DESCRIPCION_ASIGNAR = """
            El productor tiene que pertenecer al nivel y no estar ocupando otro cargo. Si ya \
            hay alguien en el puesto, su período se cierra el día previo al nuevo, de modo que \
            no haya dos personas en el mismo cargo el mismo día. Nadie se borra: el anterior \
            queda en el historial.

            Devuelve 409 si el cargo no existe en ese nivel, si el productor es de otro lado, \
            o si ya tiene un cargo.""";

    private static final String DESCRIPCION_TERMINAR = """
            Cierra el período en curso sin nombrar reemplazo. Una renuncia sin sucesor es una \
            situación real y tiene que poder registrarse sin inventar a nadie.""";
}
