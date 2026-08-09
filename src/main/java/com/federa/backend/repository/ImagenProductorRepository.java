package com.federa.backend.repository;

import com.federa.backend.model.ImagenProductor;
import com.federa.backend.model.enums.TipoImagen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImagenProductorRepository extends JpaRepository<ImagenProductor, Long> {

    Optional<ImagenProductor> findByProductorIdAndTipo(Long productorId, TipoImagen tipo);

    /** Las dos variantes de un productor, para borrarlas juntas. */
    List<ImagenProductor> findByProductorId(Long productorId);

    /**
     * Claves de las imágenes de un productor.
     * <p>
     * Se usa al borrar el productor: el cascade se lleva las filas, pero los
     * archivos del almacén hay que borrarlos a mano, y para eso hace falta
     * saber sus claves antes de que las filas desaparezcan.
     */
    @Query("select i.clave from ImagenProductor i where i.productor.id = :productorId")
    List<String> findClavesPorProductor(@Param("productorId") Long productorId);

    /**
     * Qué imágenes tiene cada productor de un conjunto, con su clave.
     * <p>
     * La clave viaja hasta el cliente convertida en URL. Como incluye un
     * identificador aleatorio que cambia al reemplazar la foto, la dirección
     * cambia sola y no hace falta ningún parámetro de versión para evitar que
     * el navegador siga mostrando la imagen anterior.
     */
    @Query("""
            select i.productor.id, i.tipo, i.clave from ImagenProductor i
            where i.productor.id in :ids
            """)
    List<Object[]> findClavesPorProductores(@Param("ids") List<Long> ids);

    @Query("""
            select i.tipo, i.tipoMime, i.tamanoBytes, i.ancho, i.alto, i.nombreOriginal,
                   i.clave, i.actualizadaEn
            from ImagenProductor i
            where i.productor.id = :productorId
            order by i.tipo
            """)
    List<Object[]> findMetadataPorProductor(@Param("productorId") Long productorId);
}
