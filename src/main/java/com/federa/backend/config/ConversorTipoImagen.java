package com.federa.backend.config;

import com.federa.backend.model.enums.TipoImagen;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Permite escribir el tipo de imagen en minúsculas en la URL.
 * <p>
 * El conversor de enums que trae Spring distingue mayúsculas, así que sin esto
 * la ruta tendría que ser {@code /imagenes/MINIATURA}.
 * <p>
 * Ante un valor desconocido lanza en vez de devolver null: un null hace que
 * Spring crea que el parámetro de ruta falta y responda con su error genérico,
 * saltándose el {@code ErrorResponse} de la aplicación. Lanzando, el fallo
 * llega como error de tipo de parámetro y {@code ManejadorGlobalErrores} lo
 * traduce a un 400 con el formato de siempre.
 */
@Component
public class ConversorTipoImagen implements Converter<String, TipoImagen> {

    @Override
    public TipoImagen convert(String valor) {
        for (TipoImagen tipo : TipoImagen.values()) {
            if (tipo.name().equalsIgnoreCase(valor.trim())) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                "Tipo de imagen inválido: '" + valor + "'. Se espera miniatura u original.");
    }
}
