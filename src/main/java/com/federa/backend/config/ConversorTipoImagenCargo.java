package com.federa.backend.config;

import com.federa.backend.model.enums.TipoImagenCargo;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Acepta {@code firma}, {@code pie_firma} y {@code pie-firma} en la URL.
 * <p>
 * El guion es lo natural en una dirección web; el guion bajo es como se llama
 * el valor del enum. Admitir los dos evita una fuente tonta de errores 400.
 */
@Component
public class ConversorTipoImagenCargo implements Converter<String, TipoImagenCargo> {

    @Override
    public TipoImagenCargo convert(String valor) {
        return TipoImagenCargo.desde(valor.trim());
    }
}
