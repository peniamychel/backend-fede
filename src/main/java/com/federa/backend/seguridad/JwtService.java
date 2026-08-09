package com.federa.backend.seguridad;

import com.federa.backend.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

/**
 * Emite y valida los tokens de sesión.
 * <p>
 * Un JWT es un texto firmado que dice quién es el portador y hasta cuándo vale.
 * El servidor no guarda sesiones: verifica la firma y confía en el contenido,
 * y por eso la clave de firma es lo único que separa un token legítimo de uno
 * inventado.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey clave;
    private final Duration duracion;

    public JwtService(
            @Value("${federa.seguridad.jwt.clave:}") String claveConfigurada,
            @Value("${federa.seguridad.jwt.duracion-horas:12}") long horas) {

        this.duracion = Duration.ofHours(horas);
        this.clave = resolverClave(claveConfigurada);
    }

    /**
     * Toma la clave de la configuración, o genera una al arrancar si no hay.
     * <p>
     * Generarla no es lo correcto para producción y por eso avisa fuerte: al
     * reiniciar cambia, y todas las sesiones abiertas dejan de valer. Se hace
     * igual porque la alternativa —dejar una clave de ejemplo escrita en el
     * repositorio— es peor: esas terminan llegando a producción sin que nadie
     * las cambie.
     */
    private SecretKey resolverClave(String configurada) {
        if (configurada != null && !configurada.isBlank()) {
            byte[] bytes = Decoders.BASE64.decode(configurada);
            if (bytes.length < 32) {
                throw new IllegalStateException(
                        "federa.seguridad.jwt.clave es demasiado corta: HS256 necesita al "
                        + "menos 32 bytes (256 bits).");
            }
            return Keys.hmacShaKeyFor(bytes);
        }

        SecretKey generada = Jwts.SIG.HS256.key().build();
        log.warn("""
                No hay clave JWT configurada; se generó una al azar para esta ejecución.
                Las sesiones se invalidan en cada reinicio y no sirve si corren varias
                instancias. Para producción, poné una clave fija en base64:
                  federa.seguridad.jwt.clave={}""",
                Base64.getEncoder().encodeToString(generada.getEncoded()));
        return generada;
    }

    /** Token para ese usuario, válido por la duración configurada. */
    public String generar(Usuario usuario) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(usuario.getNombreUsuario())
                .claim("rol", usuario.getRol())
                .claim("nombre", usuario.getNombreCompleto())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + duracion.toMillis()))
                .signWith(clave)
                .compact();
    }

    /**
     * Devuelve el contenido del token si la firma y la vigencia son válidas, o
     * null si no.
     * <p>
     * No lanza: un token vencido o falsificado es una situación esperable —
     * pasa cada vez que expira una sesión— y no una excepción del sistema.
     */
    public Claims validar(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token rechazado: {}", e.getMessage());
            return null;
        }
    }

    public long getDuracionSegundos() {
        return duracion.toSeconds();
    }
}
