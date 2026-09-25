package com.federa.backend.seguridad;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Límite sencillo por dirección para frenar intentos automáticos de códigos. */
@Component
public class LimiteIntentosAcceso {
    private static final int MAXIMOS = 8;
    private static final Duration VENTANA = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, Intentos> intentos = new ConcurrentHashMap<>();

    public void verificar(String origen) {
        Intentos actual = intentos.get(origen);
        if (actual == null) return;
        if (actual.inicio.plus(VENTANA).isBefore(Instant.now())) {
            intentos.remove(origen, actual);
            return;
        }
        if (actual.cantidad >= MAXIMOS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Esperá unos minutos antes de volver a probar.");
        }
    }

    public void fallo(String origen) {
        intentos.compute(origen, (clave, actual) -> {
            Instant ahora = Instant.now();
            if (actual == null || actual.inicio.plus(VENTANA).isBefore(ahora)) {
                return new Intentos(1, ahora);
            }
            return new Intentos(actual.cantidad + 1, actual.inicio);
        });
    }

    public void exito(String origen) { intentos.remove(origen); }

    private record Intentos(int cantidad, Instant inicio) {}
}
