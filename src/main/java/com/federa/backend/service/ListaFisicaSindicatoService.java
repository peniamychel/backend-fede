package com.federa.backend.service;

import com.federa.backend.almacen.AlmacenObjetos;
import com.federa.backend.almacen.TransaccionArchivos;
import com.federa.backend.exception.ArchivoInvalidoException;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.model.PaginaListaFisicaSindicato;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Fotografías optimizadas y PDF consolidado de la lista física de un sindicato. */
@Service
@Transactional(readOnly = true)
public class ListaFisicaSindicatoService {

    static final int PESO_MAXIMO_PAGINA = 15 * 1024 * 1024;
    static final int PESO_MAXIMO_POR_CARGA = 180 * 1024 * 1024;
    static final int MAXIMO_PAGINAS = 60;
    private static final Set<String> TIPOS = Set.of("image/jpeg", "image/png");

    private final SindicatoRepository sindicatoRepository;
    private final AlmacenObjetos almacen;
    private final ProcesadorImagenes procesador;

    public ListaFisicaSindicatoService(SindicatoRepository sindicatoRepository,
                                       AlmacenObjetos almacen,
                                       ProcesadorImagenes procesador) {
        this.sindicatoRepository = sindicatoRepository;
        this.almacen = almacen;
        this.procesador = procesador;
    }

    public record ArchivoSubido(byte[] contenido, String nombre, String tipoMime) {
    }

    public record Pagina(Long id, int orden, String nombre, String tipoMime,
                         int tamanoBytes, String url) {
    }

    public record ListaFisica(Long sindicatoId, String sindicato, int paginas,
                              String pdfUrl, LocalDateTime actualizadaEn,
                              List<Pagina> detallePaginas) {
    }

    public record Descarga(String nombreArchivo, byte[] contenido) {
    }

    @Transactional
    public ListaFisica obtener(Long sindicatoId) {
        Sindicato sindicato = buscarSindicato(sindicatoId);
        comprimirPaginasAntiguas(sindicato);
        return respuesta(sindicato);
    }

    @Transactional
    public ListaFisica agregar(Long sindicatoId, List<ArchivoSubido> archivos) {
        Sindicato sindicato = buscarSindicato(sindicatoId);
        if (archivos == null || archivos.isEmpty()) {
            throw new ArchivoInvalidoException("Elegí al menos una fotografía.");
        }
        if (sindicato.getPaginasListaFisica().size() + archivos.size() > MAXIMO_PAGINAS) {
            throw new ReglaNegocioException("La lista física admite hasta "
                    + MAXIMO_PAGINAS + " páginas.");
        }
        long pesoCarga = archivos.stream()
                .filter(a -> a != null && a.contenido() != null)
                .mapToLong(a -> a.contenido().length)
                .sum();
        if (pesoCarga > PESO_MAXIMO_POR_CARGA) {
            throw new ArchivoInvalidoException(
                    "La selección completa supera los 180 MB. Subila en dos tandas.");
        }

        List<ArchivoValidado> validados = archivos.stream().map(this::validar).toList();
        int orden = sindicato.getPaginasListaFisica().size();
        for (ArchivoValidado archivo : validados) {
            String clave = clavePagina(sindicatoId, archivo.tipoMime());
            guardarNuevo(clave, archivo.contenido());
            PaginaListaFisicaSindicato pagina = PaginaListaFisicaSindicato.builder()
                    .sindicato(sindicato)
                    .orden(++orden)
                    .clave(clave)
                    .nombre(archivo.nombre())
                    .tipoMime(archivo.tipoMime())
                    .tamanoBytes(archivo.contenido().length)
                    .build();
            sindicato.getPaginasListaFisica().add(pagina);
        }
        regenerarPdf(sindicato);
        sindicatoRepository.flush();
        return respuesta(sindicato);
    }

    @Transactional
    public ListaFisica reemplazar(Long sindicatoId, Long paginaId,
                                  ArchivoSubido archivoSubido) {
        Sindicato sindicato = buscarSindicato(sindicatoId);
        PaginaListaFisicaSindicato pagina = buscarPagina(sindicato, paginaId);
        ArchivoValidado archivo = validar(archivoSubido);
        String anterior = pagina.getClave();
        String nueva = clavePagina(sindicatoId, archivo.tipoMime());
        guardarNuevo(nueva, archivo.contenido());

        pagina.setClave(nueva);
        pagina.setNombre(archivo.nombre());
        pagina.setTipoMime(archivo.tipoMime());
        pagina.setTamanoBytes(archivo.contenido().length);
        regenerarPdf(sindicato);
        sindicatoRepository.flush();
        TransaccionArchivos.alConfirmar(() -> almacen.borrar(anterior));
        return respuesta(sindicato);
    }

    @Transactional
    public ListaFisica quitar(Long sindicatoId, Long paginaId) {
        Sindicato sindicato = buscarSindicato(sindicatoId);
        PaginaListaFisicaSindicato pagina = buscarPagina(sindicato, paginaId);
        String clave = pagina.getClave();
        sindicato.getPaginasListaFisica().remove(pagina);
        for (int i = 0; i < sindicato.getPaginasListaFisica().size(); i++) {
            sindicato.getPaginasListaFisica().get(i).setOrden(i + 1);
        }
        regenerarPdf(sindicato);
        sindicatoRepository.flush();
        TransaccionArchivos.alConfirmar(() -> almacen.borrar(clave));
        return respuesta(sindicato);
    }

    public Descarga descargarPdf(Long sindicatoId) {
        Sindicato sindicato = buscarSindicato(sindicatoId);
        String clave = sindicato.getListaFisicaPdfClave();
        if (clave == null || !almacen.existe(clave)) {
            throw new RecursoNoEncontradoException(
                    "La lista física del sindicato todavía no tiene páginas.");
        }
        String nombre = "lista-fisica-"
                + Textos.paraNombreDeArchivo(sindicato.getNombre(), 45) + ".pdf";
        return new Descarga(nombre, almacen.leer(clave));
    }

    /**
     * Crea un PDF nuevo con las imágenes ya optimizadas incrustadas. Escalarlas
     * en la página no vuelve a comprimirlas ni reduce otra vez su resolución.
     */
    private void regenerarPdf(Sindicato sindicato) {
        String anterior = sindicato.getListaFisicaPdfClave();
        if (sindicato.getPaginasListaFisica().isEmpty()) {
            sindicato.setListaFisicaPdfClave(null);
            sindicato.setListaFisicaActualizadaEn(LocalDateTime.now());
            if (anterior != null) {
                TransaccionArchivos.alConfirmar(() -> almacen.borrar(anterior));
            }
            return;
        }

        List<byte[]> paginas = sindicato.getPaginasListaFisica().stream()
                .sorted(Comparator.comparingInt(PaginaListaFisicaSindicato::getOrden))
                .map(p -> almacen.leer(p.getClave()))
                .toList();
        byte[] pdf = generarPdf(paginas);
        String nueva = "listas-fisicas/" + sindicato.getId() + "/consolidado-"
                + UUID.randomUUID().toString().substring(0, 12) + ".pdf";
        guardarNuevo(nueva, pdf);
        sindicato.setListaFisicaPdfClave(nueva);
        sindicato.setListaFisicaActualizadaEn(LocalDateTime.now());
        if (anterior != null) {
            TransaccionArchivos.alConfirmar(() -> almacen.borrar(anterior));
        }
    }

    byte[] generarPdf(List<byte[]> paginas) {
        try {
            List<Image> imagenes = new ArrayList<>();
            for (byte[] pagina : paginas) imagenes.add(Image.getInstance(pagina));
            Rectangle inicial = formato(imagenes.get(0));
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document documento = new Document(inicial, 0, 0, 0, 0);
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            documento.open();
            PdfContentByte lienzo = escritor.getDirectContent();
            for (int i = 0; i < imagenes.size(); i++) {
                Image imagen = imagenes.get(i);
                Rectangle pagina = formato(imagen);
                if (i > 0) {
                    documento.setPageSize(pagina);
                    documento.newPage();
                }
                imagen.scaleToFit(pagina.getWidth(), pagina.getHeight());
                imagen.setAbsolutePosition(
                        (pagina.getWidth() - imagen.getScaledWidth()) / 2f,
                        (pagina.getHeight() - imagen.getScaledHeight()) / 2f);
                lienzo.addImage(imagen);
                escritor.setPageEmpty(false);
            }
            documento.close();
            return salida.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new ArchivoInvalidoException(
                    "No se pudo consolidar una de las fotografías en el PDF.", e);
        }
    }

    private Rectangle formato(Image imagen) {
        return imagen.getWidth() > imagen.getHeight() ? PageSize.A4.rotate() : PageSize.A4;
    }

    private record ArchivoValidado(byte[] contenido, String nombre, String tipoMime) {
    }

    private ArchivoValidado validar(ArchivoSubido archivo) {
        if (archivo == null || archivo.contenido() == null
                || archivo.contenido().length == 0) {
            throw new ArchivoInvalidoException("Una de las fotografías llegó vacía.");
        }
        if (archivo.contenido().length > PESO_MAXIMO_PAGINA) {
            throw new ArchivoInvalidoException(String.format(Locale.ROOT,
                    "La fotografía %s pesa %.1f MB y el máximo son %d MB.",
                    nombre(archivo.nombre()), archivo.contenido().length / 1024d / 1024d,
                    PESO_MAXIMO_PAGINA / 1024 / 1024));
        }
        String tipo = tipoDe(archivo.tipoMime(), archivo.nombre());
        if (!TIPOS.contains(tipo)) {
            throw new ArchivoInvalidoException(
                    "Las páginas deben ser imágenes JPG o PNG. Llegó: " + tipo);
        }
        ProcesadorImagenes.Variante comprimida =
                procesador.prepararDocumento(archivo.contenido());
        if (comprimida.contenido().length > ProcesadorImagenes.PESO_DOCUMENTO) {
            throw new ArchivoInvalidoException(
                    "No se pudo reducir la fotografía " + nombre(archivo.nombre())
                            + " por debajo de 300 KB.");
        }
        return new ArchivoValidado(comprimida.contenido(),
                nombre(archivo.nombre()), comprimida.tipoMime());
    }

    /** Comprime también las páginas subidas antes de que existiera este límite. */
    private void comprimirPaginasAntiguas(Sindicato sindicato) {
        boolean cambio = false;
        for (PaginaListaFisicaSindicato pagina : sindicato.getPaginasListaFisica()) {
            if (pagina.getTamanoBytes() <= ProcesadorImagenes.PESO_DOCUMENTO) continue;
            String anterior = pagina.getClave();
            ArchivoValidado archivo = validar(new ArchivoSubido(
                    almacen.leer(anterior), pagina.getNombre(), pagina.getTipoMime()));
            String nueva = clavePagina(sindicato.getId(), archivo.tipoMime());
            guardarNuevo(nueva, archivo.contenido());
            pagina.setClave(nueva);
            pagina.setTipoMime(archivo.tipoMime());
            pagina.setTamanoBytes(archivo.contenido().length);
            TransaccionArchivos.alConfirmar(() -> almacen.borrar(anterior));
            cambio = true;
        }
        if (cambio) {
            regenerarPdf(sindicato);
            sindicatoRepository.flush();
        }
    }

    private void guardarNuevo(String clave, byte[] contenido) {
        almacen.guardar(clave, contenido);
        TransaccionArchivos.alDeshacer(() -> almacen.borrar(clave));
    }

    private Sindicato buscarSindicato(Long id) {
        return sindicatoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("sindicato", id));
    }

    private PaginaListaFisicaSindicato buscarPagina(Sindicato sindicato, Long paginaId) {
        return sindicato.getPaginasListaFisica().stream()
                .filter(p -> p.getId().equals(paginaId))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "página de la lista física", paginaId));
    }

    private ListaFisica respuesta(Sindicato sindicato) {
        List<Pagina> paginas = sindicato.getPaginasListaFisica().stream()
                .sorted(Comparator.comparingInt(PaginaListaFisicaSindicato::getOrden))
                .map(p -> new Pagina(p.getId(), p.getOrden(), p.getNombre(),
                        p.getTipoMime(), p.getTamanoBytes(), almacen.urlPublica(p.getClave())))
                .toList();
        String pdfUrl = sindicato.getListaFisicaPdfClave() == null
                ? null
                : "/api/v1/sindicatos/" + sindicato.getId() + "/lista-fisica.pdf";
        return new ListaFisica(sindicato.getId(), sindicato.getNombre(), paginas.size(),
                pdfUrl, sindicato.getListaFisicaActualizadaEn(), paginas);
    }

    private String clavePagina(Long sindicatoId, String tipoMime) {
        String extension = "image/png".equals(tipoMime) ? ".png" : ".jpg";
        return "listas-fisicas/" + sindicatoId + "/pagina-"
                + UUID.randomUUID().toString().substring(0, 12) + extension;
    }

    private String tipoDe(String declarado, String archivo) {
        String tipo = declarado == null ? "" : declarado.trim().toLowerCase(Locale.ROOT);
        if (TIPOS.contains(tipo)) return tipo;
        String nombre = archivo == null ? "" : archivo.toLowerCase(Locale.ROOT);
        if (nombre.endsWith(".png")) return "image/png";
        if (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) return "image/jpeg";
        return tipo.isBlank() ? "desconocido" : tipo;
    }

    private String nombre(String nombre) {
        String limpio = Textos.limpiar(nombre);
        if (limpio == null) return "pagina";
        return limpio.length() <= 180 ? limpio : limpio.substring(0, 180);
    }
}
