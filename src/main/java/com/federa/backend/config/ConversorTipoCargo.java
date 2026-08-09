package com.federa.backend.config;

import com.federa.backend.model.enums.TipoCargo;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Permite escribir el cargo en minúsculas en la URL.
 * <p>
 * Lanza ante un valor desconocido en vez de devolver null: con null, Spring
 * cree que falta el parámetro de ruta y responde con su error genérico en lugar
 * del {@code ErrorResponse} de la aplicación.
 */
@Component
public class ConversorTipoCargo implements Converter<String, TipoCargo> {

    @Override
    public TipoCargo convert(String valor) {
        return TipoCargo.desde(valor.trim());
    }
}
