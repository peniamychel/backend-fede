package com.federa.backend.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Utilidades de paginación. */
public final class Paginas {

    private Paginas() {
    }

    /**
     * Agrega {@code id} como último criterio de orden.
     * <p>
     * Sin un orden <b>total</b>, {@code LIMIT/OFFSET} no garantiza nada: MySQL
     * puede devolver la misma fila en dos páginas y saltear otra, porque cada
     * página es una consulta independiente y el optimizador puede elegir otro
     * plan. Ordenar por apellido no alcanza — el padrón tiene homónimos — y sin
     * ningún orden es peor todavía. La clave primaria desempata siempre.
     */
    public static Pageable conOrdenEstable(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
