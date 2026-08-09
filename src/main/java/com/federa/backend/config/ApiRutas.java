package com.federa.backend.config;

/**
 * Prefijo de las rutas de la API.
 * <p>
 * Al ser una constante de compilación se puede usar dentro de
 * {@code @RequestMapping} y, a la vez, para armar la cabecera {@code Location}
 * de los 201, de modo que la versión quede escrita en un solo lugar. Cuando
 * salga una v2 conviven agregando otra constante, sin tocar la v1.
 */
public final class ApiRutas {

    /** Versión actual de la API. */
    public static final String V1 = "/api/v1";

    private ApiRutas() {
    }
}
