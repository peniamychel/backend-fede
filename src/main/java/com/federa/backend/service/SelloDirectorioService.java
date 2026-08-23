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
        DatosSello actual = datosDe(ambito, id);
        BufferedImage origen = procesador.leer(contenido);
        ProcesadorImagenes.Variante variante = procesador.generarPng(
                origen, LADO_MAXIMO, PESO_MAXIMO);

        String aleatorio = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String claveNueva = "sellos/" + ambito.name().toLowerCase() + "-" + id + "-"
                + aleatorio + "-" + Textos.paraNombreDeArchivo(actual.nombre(), 35) + ".png";

        almacen.guardar(claveNueva, variante.contenido());
        alDeshacer(() -> almacen.borrar(claveNueva));

        asignarClave(ambito, id, claveNueva);
        if (actual.clave() != null) {
            alConfirmar(() -> almacen.borrar(actual.clave()));
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
        asignarClave(ambito, id, null);
        alConfirmar(() -> almacen.borrar(actual.clave()));
        return directorioService.obtener(ambito, id);
    }

    private DatosSello datosDe(Ambito ambito, Long id) {
        return switch (ambito) {
            case SINDICATO -> {
                Sindicato entidad = sindicatoService.buscar(id);
                yield new DatosSello(entidad.getNombre(), entidad.getSelloClave());
            }
            case CENTRAL -> {
                Central entidad = centralService.buscar(id);
                yield new DatosSello(entidad.getNombre(), entidad.getSelloClave());
            }
            case FEDERACION -> {
                Federacion entidad = federacionService.buscar(id);
                yield new DatosSello(entidad.getNombre(), entidad.getSelloClave());
            }
        };
    }

    private void asignarClave(Ambito ambito, Long id, String clave) {
        switch (ambito) {
            case SINDICATO -> {
                sindicatoService.buscar(id).setSelloClave(clave);
                sindicatoRepository.flush();
            }
            case CENTRAL -> {
                centralService.buscar(id).setSelloClave(clave);
                centralRepository.flush();
            }
            case FEDERACION -> {
                federacionService.buscar(id).setSelloClave(clave);
                federacionRepository.flush();
            }
        }
    }

    private record DatosSello(String nombre, String clave) {
    }
}
