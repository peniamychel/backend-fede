package com.federa.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Enciende la auditoría de Spring Data.
 * <p>
 * Sin esto el oyente que declara {@code EntidadAuditable} existe pero nunca
 * hace nada, y las dos fechas se guardarían en null. Como son columnas
 * {@code not null}, el fallo aparecería recién al primer INSERT.
 * <p>
 * Va en su propia clase y no sobre {@code BackendApplication} para que se pueda
 * apagar en una prueba sin apagar la aplicación entera.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "relojDeAuditoria")
public class AuditoriaConfig {

    /**
     * Reloj de la auditoría, truncado al segundo.
     * <p>
     * Las columnas son {@code datetime}, sin fracción de segundo. Sin este
     * truncado la entidad recién creada queda en memoria con microsegundos que
     * la base no guarda, y la respuesta del POST devuelve una hora que no
     * coincide con la que se lee después: 02:26:13.730275 al crear y 02:26:13
     * en cualquier consulta posterior. Truncando en el origen, lo que se
     * devuelve es siempre lo que quedó guardado.
     */
    @Bean
    public DateTimeProvider relojDeAuditoria() {
        return () -> Optional.of(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
