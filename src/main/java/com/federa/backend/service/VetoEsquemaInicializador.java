package com.federa.backend.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapta instalaciones existentes al veto administrado desde el sindicato.
 * Los vetos históricos conservan su reunión; los nuevos pueden no tenerla.
 */
@Component
public class VetoEsquemaInicializador implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public VetoEsquemaInicializador(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String nullable = jdbc.queryForObject("""
                select IS_NULLABLE
                from information_schema.COLUMNS
                where TABLE_SCHEMA = database()
                  and TABLE_NAME = 'vetos'
                  and COLUMN_NAME = 'reunion_id'
                """, String.class);
        if (nullable != null && !"YES".equalsIgnoreCase(nullable)) {
            jdbc.execute("alter table vetos modify column reunion_id bigint null");
        }
    }
}
