package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import static com.federa.backend.almacen.TransaccionArchivos.alConfirmar;
import static com.federa.backend.almacen.TransaccionArchivos.alDeshacer;

import com.federa.backend.dto.ImagenResponse;
import com.federa.backend.dto.ImagenSubidaResponse;
import com.federa.backend.dto.RecorteRequest;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.ImagenProductor;
import com.federa.backend.model.Productor;
import com.federa.backend.model.enums.TipoImagen;
import com.federa.backend.repository.ImagenProductorRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fotos de los productores.
 * <p>
 * Se sube <b>una sola</b> imagen, del tamaño que sea, y el servicio deriva las
 * dos variantes: la de consulta y la miniatura. Los archivos van al almacén de
 * objetos del servidor; en la base queda solo la clave con la que encontrarlos.
 * <p>
 * <b>El disco no participa de la transacción de la base</b>, y de ahí sale la
 * parte delicada de esta clase: si se escribiera el archivo y la transacción
 * fallara quedaría basura, y si se borrara el archivo viejo antes de confirmar
 * se perdería la foto ante cualquier error posterior. Por eso los borrados se
 * enganchan al final de la transacción en vez de hacerse en el momento.
 */
@Service
@Transactional(readOnly = true)
public class ImagenProductorService {

    private final ImagenProductorRepository imagenRepository;
    private final ProductorService productorService;
    private final ProcesadorImagenes procesador;
    private final AlmacenObjetos almacen;

    public ImagenProductorService(ImagenProductorRepository imagenRepository,
                                  ProductorService productorService,
                                  ProcesadorImagenes procesador,
                                  AlmacenObjetos almacen) {
        this.imagenRepository = imagenRepository;
        this.productorService = productorService;
        this.procesador = procesador;
        this.almacen = almacen;
    }

    public List<ImagenResponse> listar(Long productorId) {
        productorService.buscar(productorId);
        return imagenRepository.findMetadataPorProductor(productorId).stream()
                .map(f -> new ImagenResponse(
                        (TipoImagen) f[0],
                        almacen.urlPublica((String) f[6]),
                        (String) f[1],
                        (int) f[2],
                        (int) f[3],
                        (int) f[4],
                        (String) f[5],
                        (LocalDateTime) f[7]))
                .toList();
    }

    /** Bytes de una variante, leídos del almacén. */
    public byte[] contenido(Long productorId, TipoImagen tipo) {
        ImagenProductor imagen = imagenRepository
                .findByProductorIdAndTipo(productorId, tipo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "imagen " + tipo.name().toLowerCase() + " del productor", productorId));
        return almacen.leer(imagen.getClave());
    }

    /**
     * Guarda la foto de un productor a partir de un único archivo.
     * <p>
     * Se acepta cualquier tamaño: la imagen se escala y se optimiza hacia el
     * peso objetivo de cada variante. Si un PNG detallado alcanza el lado
     * mínimo antes del peso, se conserva esa mejor aproximación. Si el
     * productor ya tenía foto, se reemplazan las dos.
     */
    /**
     * @param recorte región a conservar, o null para usar la imagen entera
     */
    @Transactional
    public ImagenSubidaResponse guardar(Long productorId, byte[] subido, String nombreArchivo,
                                        RecorteRequest recorte) {
        Productor productor = productorService.buscar(productorId);

        // Se decodifica una sola vez para las dos variantes: releer el archivo
        // por cada una duplicaría el trabajo más caro.
        BufferedImage subida = procesador.leer(subido);

        // El recorte va antes de generar las variantes, no después: así las dos
        // salen del mismo encuadre y el escalado gasta sus píxeles en la parte
        // que se quiere conservar.
        BufferedImage origen = recorte == null
                ? subida
                : procesador.recortar(subida, recorte);

        ImagenResponse original = guardarVariante(
                productor, TipoImagen.ORIGINAL, origen, nombreArchivo);
        ImagenResponse miniatura = guardarVariante(
                productor, TipoImagen.MINIATURA, origen, nombreArchivo);

        // Se informa el tamaño de lo que subió el usuario, no el del recorte:
        // es lo que le permite entender cuánto se ahorró respecto de su archivo.
        return new ImagenSubidaResponse(
                subido.length, subida.getWidth(), subida.getHeight(), original, miniatura);
    }

    private ImagenResponse guardarVariante(Productor productor, TipoImagen tipo,
                                           BufferedImage origen, String nombreArchivo) {
        // Las fotos nuevas llegan como PNG cuadrado desde la vista previa de la
        // app. Se conserva su canal alfa para poder colocar luego otro fondo
        // al emitir la credencial.
        ProcesadorImagenes.Variante variante = procesador.generarPng(origen, tipo);

        ImagenProductor imagen = imagenRepository
                .findByProductorIdAndTipo(productor.getId(), tipo)
                .orElseGet(() -> {
                    ImagenProductor nueva = new ImagenProductor();
                    nueva.setProductor(productor);
                    nueva.setTipo(tipo);
                    return nueva;
                });

        String claveAnterior = imagen.getClave();
        String claveNueva = nuevaClave(productor, tipo, variante.tipoMime());

        almacen.guardar(claveNueva, variante.contenido());
        // Si la transacción no llega a confirmar, este archivo no le sirve a
        // nadie: se borra para no dejar basura en el disco.
        alDeshacer(() -> almacen.borrar(claveNueva));

        imagen.setClave(claveNueva);
        imagen.setTipoMime(variante.tipoMime());
        imagen.setTamanoBytes(variante.contenido().length);
        imagen.setAncho(variante.ancho());
        imagen.setAlto(variante.alto());
        imagen.setNombreOriginal(recortar(nombreArchivo));

        ImagenProductor guardada = imagenRepository.save(imagen);

        // El archivo anterior recién se borra cuando la transacción confirmó.
        // Borrarlo antes dejaría al productor sin foto si algo fallara después.
        if (claveAnterior != null) {
            alConfirmar(() -> almacen.borrar(claveAnterior));
        }

        return ImagenResponse.desde(guardada);
    }

    /**
     * Borra la foto del productor: las dos variantes juntas.
     * <p>
     * No se pueden borrar por separado porque tampoco se suben por separado:
     * una miniatura sin su original sería un resto sin sentido.
     */
    @Transactional
    public void eliminar(Long productorId) {
        productorService.buscar(productorId);
        List<ImagenProductor> imagenes = imagenRepository.findByProductorId(productorId);
        if (imagenes.isEmpty()) {
            throw new RecursoNoEncontradoException("foto del productor", productorId);
        }

        List<String> claves = imagenes.stream().map(ImagenProductor::getClave).toList();
        imagenRepository.deleteAll(imagenes);
        alConfirmar(() -> claves.forEach(almacen::borrar));
    }

    /**
     * Clave única por subida:
     * {@code originales/2ab3fb23fb23-juan-morales.png}.
     * <p>
     * Dos partes:
     * <ul>
     *   <li><b>identificador aleatorio</b>: es lo que garantiza que no haya dos
     *       archivos con el mismo nombre, incluso entre homónimos. Además hace
     *       que al reemplazar una foto cambie la URL, con lo que la caché del
     *       navegador se invalida sola.</li>
     *   <li><b>nombre del productor</b>: hace el archivo reconocible al mirar
     *       la carpeta. Es una comodidad, no un identificador — si el productor
     *       se renombra, el archivo conserva el nombre viejo hasta la próxima
     *       subida.</li>
     * </ul>
     * Va primero el aleatorio para que la parte que garantiza unicidad no
     * quede nunca recortada por el tope de longitud del nombre.
     */
    private String nuevaClave(Productor productor, TipoImagen tipo, String tipoMime) {
        String aleatorio = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String nombre = Textos.paraNombreDeArchivo(productor.getNombreCompleto(), 40);
        String extension = "image/png".equals(tipoMime) ? ".png" : ".jpg";
        return tipo.getDirectorio() + "/" + aleatorio + "-" + nombre + extension;
    }

    private String recortar(String nombre) {
        if (nombre == null) {
            return null;
        }
        return nombre.length() > 160 ? nombre.substring(nombre.length() - 160) : nombre;
    }
}
