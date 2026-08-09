package com.federa.backend.seguridad;

import com.federa.backend.config.ApiRutas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    private static final Logger log = LoggerFactory.getLogger(SeguridadConfig.class);

    private final JwtFiltro jwtFiltro;
    private final PuntoDeEntradaNoAutorizado puntoDeEntrada;
    private final boolean exigirAutenticacion;
    private final List<String> origenesPermitidos;

    public SeguridadConfig(
            JwtFiltro jwtFiltro,
            PuntoDeEntradaNoAutorizado puntoDeEntrada,
            @Value("${federa.seguridad.exigir-autenticacion:false}") boolean exigirAutenticacion,
            @Value("${federa.seguridad.origenes:http://localhost:5173}")
            List<String> origenesPermitidos) {
        this.jwtFiltro = jwtFiltro;
        this.puntoDeEntrada = puntoDeEntrada;
        this.exigirAutenticacion = exigirAutenticacion;
        this.origenesPermitidos = origenesPermitidos;
    }

    @Bean
    public PasswordEncoder codificadorContrasenas() {
        // BCrypt y no un hash simple: incorpora sal y es deliberadamente lento,
        // que es lo que hace inviable probar contraseñas por fuerza bruta.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadenaDeFiltros(HttpSecurity http) throws Exception {
        if (!exigirAutenticacion) {
            log.warn("""
                    La autenticación está DESACTIVADA: el padrón responde a cualquiera.
                    El inicio de sesión funciona y emite tokens; solo falta exigirlos.
                    Para activarla: federa.seguridad.exigir-autenticacion=true""");
        }

        http
                // Sin esto, la cadena de Security rechaza el preflight con 403
                // antes de que llegue a Spring MVC, y el CorsConfig que hay en
                // config/ no se entera: ese es un WebMvcConfigurer y actúa más
                // tarde. Es el error más común al agregar Security a una API
                // que ya funcionaba con un cliente web.
                .cors(cors -> cors.configurationSource(fuenteCors()))

                // La API no usa cookies ni formularios: el token viaja en una
                // cabecera que el navegador no manda solo. Sin cookies no hay
                // petición cruzada que falsificar, así que CSRF no aplica.
                .csrf(csrf -> csrf.disable())

                // Sin sesión en el servidor: cada petición se autentica sola
                // con su token. Es lo que permite reiniciar el backend o correr
                // varias instancias sin desloguear a nadie.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(this::rutas)

                // Sin esto, una petición sin token recibe 403 en vez de 401 y
                // el cliente no puede distinguir "iniciá sesión" de "no tenés
                // permiso".
                .exceptionHandling(e -> e.authenticationEntryPoint(puntoDeEntrada))

                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void rutas(
            org.springframework.security.config.annotation.web.configurers
                    .AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry registro) {

        registro
                // Iniciar sesión no puede exigir estar autenticado.
                .requestMatchers(ApiRutas.V1 + "/auth/**").permitAll()

                // La documentación queda abierta en desarrollo.
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll()

                // Los preflight nunca llevan credenciales: exigirlas los
                // rompería y con ellos toda la app web.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Las imágenes quedan públicas incluso con la autenticación
                // activada, y es una decisión consciente: una etiqueta <img>
                // del navegador no manda la cabecera Authorization, así que un
                // archivo protegido simplemente no se vería. Las claves llevan
                // un identificador aleatorio, de modo que no se pueden adivinar
                // ni enumerar, pero quien tenga la URL puede abrirla. Cuando
                // eso no alcance, el camino es firmar las URL con un token
                // corto en el parámetro, no exigir la cabecera.
                .requestMatchers(HttpMethod.GET, ApiRutas.V1 + "/archivos/**").permitAll();

        if (exigirAutenticacion) {
            registro.anyRequest().authenticated();
        } else {
            registro.anyRequest().permitAll();
        }
    }

    /**
     * CORS para la cadena de Security.
     * <p>
     * Repite lo que ya declara {@code config.CorsConfig} porque son dos capas
     * distintas: aquella cubre Spring MVC y esta el filtro de seguridad, que
     * corre antes. Las dos tienen que coincidir.
     */
    @Bean
    public CorsConfigurationSource fuenteCors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenesPermitidos);
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}
