package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.dto.DirectorioResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.FederacionRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.util.UUID;

import static com.federa.backend.almacen.TransaccionArchivos.alConfirmar;
import static com.federa.backend.almacen.TransaccionArchivos.alDeshacer;

/** Sello institucional propio de cada sindicato, central o federación. */
@Service
@Transactional(readOnly = true)
public class SelloDirectorioService {

    private static final int LADO_MAXIMO = 600;
    private static final int PESO_MAXIMO = 500 * 1024;

    private final SindicatoService sindicatoService;
    private final CentralService centralService;
    private final FederacionService federacionService;
    private final SindicatoRepository sindicatoRepository;
    private final CentralRepository centralRepository;
    private final FederacionRepository federacionRepository;
    private final ProcesadorImagenes procesador;
    private final AlmacenObjetos almacen;
    private final DirectorioService directorioService;

    public SelloDirectorioService(SindicatoService sindicatoService,
                                  CentralService centralService,
                                  FederacionService federacionService,
                                  SindicatoRepository sindicatoRepository,
                                  CentralRepository centralRepository,
                                  FederacionRepository federacionRepository,
                                  ProcesadorImagenes procesador,
                                  AlmacenObjetos almacen,
                                  DirectorioService directorioService) {
        this.sindicatoService = sindicatoService;
        this.centralService = centralService;
        this.federacionService = federacionService;
        this.sindicatoRepository = sindicatoRepository;
        this.centralRepository = centralRepository;
        this.federacionRepository = federacionRepository;
        this.procesador = procesador;
        this.almacen = almacen;
        this.directorioService = directorioService;
    }

    @Transactional
    public DirectorioResponse guardar(Ambito ambito, Long id, byte[] contenido) {
        return guardar(ambito, id, contenido, null);
    }

    @Transactional
    public DirectorioResponse guardar(Ambito ambito, Long id, byte[] contenido,
                                      byte[] originalSubido) {
        DatosSello actual = datosDe(ambito, id);
        BufferedImage origen = procesador.leer(contenido);
        ProcesadorImagenes.Variante variante = procesador.generarPng(
                origen, LADO_MAXIMO, PESO_MAXIMO);
        ProcesadorImagenes.Variante original = procesador.prepararOriginalEditable(
                originalSubido == null ? contenido : originalSubido);

        String aleatorio = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String claveNueva = "sellos/" + ambito.name().toLowerCase() + "-" + id + "-"
                + aleatorio + "-" + Textos.paraNombreDeArchivo(actual.nombre(), 35) + ".png";
        String originalNueva = "originales-directorio/sellos/"
                + ambito.name().toLowerCase() + "-" + id + "-" + aleatorio + "-"
                + Textos.paraNombreDeArchivo(actual.nombre(), 35) + ".jpg";

        almacen.guardar(claveNueva, variante.contenido());
        alDeshacer(() -> almacen.borrar(claveNueva));
        almacen.guardar(originalNueva, original.contenido());
        alDeshacer(() -> almacen.borrar(originalNueva));

        asignarClaves(ambito, id, claveNueva, originalNueva);
        if (actual.clave() != null) {
            alConfirmar(() -> almacen.borrar(actual.clave()));
        }
        if (actual.originalClave() != null) {
            alConfirmar(() -> almacen.borrar(actual.originalClave()));
        }
        return directorioService.obtener(ambito, id);
    }

    @Transactional
    public DirectorioResponse eliminar(Ambito ambito, Long id) {
        DatosSello actual = datosDe(ambito, id);
        if (actual.clave() == null) {
            throw new RecursoNoEncontradoException(
                    actual.nombre() + " no tiene un sello cargado.");
        }
        asignarClaves(ambito, id, null, null);
        alConfirmar(() -> almacen.borrar(actual.clave()));
        if (actual.originalClave() != null) {
            alConfirmar(() -> almacen.borrar(actual.originalClave()));
        }
        return directorioService.obtener(ambito, id);
    }

    public ArchivoEditable original(Ambito ambito, Long id) {
        DatosSello datos = datosDe(ambito, id);
        if (datos.clave() == null) {
            throw new RecursoNoEncontradoException(
                    datos.nombre() + " no tiene un sello cargado.");
        }
        String clave = datos.originalClave() != null ? datos.originalClave() : datos.clave();
        String mime = datos.originalClave() != null || !clave.toLowerCase().endsWith(".png")
                ? "image/jpeg"
                : "image/png";
        return new ArchivoEditable(
                almacen.leer(clave),
                mime,
                "sello-original" + ("image/png".equals(mime) ? ".png" : ".jpg"));
    }

    private DatosSello datosDe(Ambito ambito, Long id) {
        return switch (ambito) {
            case SINDICATO -> {
                Sindicato entidad = sindicatoService.buscar(id);
                yield new DatosSello(entidad.getNombre(), entidad.getSelloClave(),
                        entidad.getSelloOriginalClave());
            }
            case CENTRAL -> {
                Central entidad = centralService.buscar(id);
                yield new DatosSello(entidad.getNombre(), entidad.getSelloClave(),
                        entidad.getSelloOriginalClave());
            }
            case FEDERACION -> {
                Federacion entidad = federacionService.buscar(id);
                yield new DatosSello(entidad.getNombre(), entidad.getSelloClave(),
                        entidad.getSelloOriginalClave());
            }
        };
    }

    private void asignarClaves(Ambito ambito, Long id, String clave, String originalClave) {
        switch (ambito) {
            case SINDICATO -> {
                Sindicato entidad = sindicatoService.buscar(id);
                entidad.setSelloClave(clave);
                entidad.setSelloOriginalClave(originalClave);
                sindicatoRepository.flush();
            }
            case CENTRAL -> {
                Central entidad = centralService.buscar(id);
                entidad.setSelloClave(clave);
                entidad.setSelloOriginalClave(originalClave);
                centralRepository.flush();
            }
            case FEDERACION -> {
                Federacion entidad = federacionService.buscar(id);
                entidad.setSelloClave(clave);
                entidad.setSelloOriginalClave(originalClave);
                federacionRepository.flush();
            }
        }
    }

    private record DatosSello(String nombre, String clave, String originalClave) {
    }

    public record ArchivoEditable(byte[] contenido, String tipoMime, String nombreArchivo) {
    }
}
