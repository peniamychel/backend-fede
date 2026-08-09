package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import static com.federa.backend.almacen.TransaccionArchivos.alConfirmar;
import static com.federa.backend.almacen.TransaccionArchivos.alDeshacer;

import com.federa.backend.dto.CargoResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.ImagenCargo;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.repository.CargoRepository;
import com.federa.backend.repository.ImagenCargoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

/**
 * Firma y pie de firma de cada período del directorio.
 * <p>
 * Se reducen a 200 píxeles de lado mayor al guardarlas. No se rechaza nada por
 * tamaño: quien saca una foto de la firma con el teléfono no tiene por qué
 * abrir un editor antes de subirla.
 */
@Service
@Transactional(readOnly = true)
public class ImagenCargoService {

    private final ImagenCargoRepository imagenRepository;
    private final CargoRepository cargoRepository;
    private final ProcesadorImagenes procesador;
    private final AlmacenObjetos almacen;

    public ImagenCargoService(ImagenCargoRepository imagenRepository,
                              CargoRepository cargoRepository,
                              ProcesadorImagenes procesador,
                              AlmacenObjetos almacen) {
        this.imagenRepository = imagenRepository;
        this.cargoRepository = cargoRepository;
        this.procesador = procesador;
        this.almacen = almacen;
    }

    /** Sube o reemplaza una de las dos imágenes de un período. */
    @Transactional
    public CargoResponse guardar(Long cargoId, TipoImagenCargo tipo, byte[] subido,
                                 String nombreArchivo) {
        Cargo cargo = buscar(cargoId);
        verificarQuePuedeFirmar(cargo);

        BufferedImage origen = procesador.leer(subido);
        ProcesadorImagenes.Variante variante = procesador.generar(
                origen, TipoImagenCargo.LADO_MAXIMO, TipoImagenCargo.PESO_MAXIMO);

        // Se trabaja sobre la colección del cargo y no sobre el repositorio.
        // La relación tiene cascade y orphanRemoval: manipular las filas por
        // fuera mientras siguen en la colección hace que Hibernate las vuelva a
        // insertar al confirmar, y además deja la respuesta con datos viejos.
        ImagenCargo imagen = buscarEn(cargo, tipo);
        if (imagen == null) {
            imagen = new ImagenCargo();
            imagen.setCargo(cargo);
            imagen.setTipo(tipo);
            cargo.getImagenes().add(imagen);
        }

        String claveAnterior = imagen.getClave();
        String claveNueva = nuevaClave(cargo, tipo);

        almacen.guardar(claveNueva, variante.contenido());
        alDeshacer(() -> almacen.borrar(claveNueva));

        imagen.setClave(claveNueva);
        imagen.setTipoMime(variante.tipoMime());
        imagen.setTamanoBytes(variante.contenido().length);
        imagen.setAncho(variante.ancho());
        imagen.setAlto(variante.alto());
        imagen.setNombreOriginal(recortar(nombreArchivo));
        imagenRepository.flush();

        if (claveAnterior != null) {
            alConfirmar(() -> almacen.borrar(claveAnterior));
        }

        return CargoResponse.desde(cargo);
    }

    @Transactional
    public CargoResponse eliminar(Long cargoId, TipoImagenCargo tipo) {
        Cargo cargo = buscar(cargoId);

        ImagenCargo imagen = buscarEn(cargo, tipo);
        if (imagen == null) {
            throw new RecursoNoEncontradoException(
                    "Este período no tiene " + tipo.getEtiqueta().toLowerCase() + " cargada.");
        }

        String clave = imagen.getClave();
        // Sacarla de la colección es lo que dispara el borrado, gracias a
        // orphanRemoval. El flush lo aplica ya, para que la respuesta refleje
        // el estado nuevo y no el anterior.
        cargo.getImagenes().remove(imagen);
        imagenRepository.flush();

        alConfirmar(() -> almacen.borrar(clave));

        return CargoResponse.desde(cargo);
    }

    private ImagenCargo buscarEn(Cargo cargo, TipoImagenCargo tipo) {
        return cargo.getImagenes().stream()
                .filter(i -> i.getTipo() == tipo)
                .findFirst()
                .orElse(null);
    }

    /**
     * Borra los archivos de firma de todos los cargos de un productor.
     * <p>
     * Lo llama {@code ProductorService} antes de eliminarlo: el cascade se
     * lleva las filas, pero los archivos del almacén no los conoce JPA.
     */
    List<String> clavesDeProductor(Long productorId) {
        return imagenRepository.findClavesPorProductor(productorId);
    }

    private Cargo buscar(Long cargoId) {
        return cargoRepository.findById(cargoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("cargo", cargoId));
    }

    /**
     * Solo el presidente y el secretario tienen firma y pie de firma, en los
     * tres niveles.
     * <p>
     * Son los que firman lo que emite la organización; al resto de los cargos
     * nadie le pide la firma, así que cargarla sería guardar archivos que no
     * usa ningún documento. La pantalla tampoco la ofrece, pero la regla se
     * comprueba acá porque la API se puede llamar directo.
     */
    private void verificarQuePuedeFirmar(Cargo cargo) {
        if (!cargo.getCargo().puedeFirmar()) {
            throw new ReglaNegocioException(String.format(
                    "El cargo de %s no lleva firma ni pie de firma. Solo el presidente y el "
                    + "secretario firman.", cargo.getCargo().getEtiqueta().toLowerCase()));
        }
    }

    /**
     * Clave del archivo: {@code firmas/a1b2c3d4e5f6-juan-morales.jpg}.
     * <p>
     * Mismo criterio que las fotos de productores: aleatorio adelante para
     * garantizar unicidad e invalidar la caché, nombre atrás para poder
     * reconocer el archivo mirando la carpeta.
     */
    private String nuevaClave(Cargo cargo, TipoImagenCargo tipo) {
        String aleatorio = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String nombre = Textos.paraNombreDeArchivo(
                cargo.getProductor().getNombreCompleto(), 40);
        return tipo.getDirectorio() + "/" + aleatorio + "-" + nombre + ".jpg";
    }

    private String recortar(String nombre) {
        if (nombre == null) {
            return null;
        }
        return nombre.length() > 160 ? nombre.substring(nombre.length() - 160) : nombre;
    }
}
