package com.federa.backend.seguridad;

import com.federa.backend.config.ApiRutas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity
public class SeguridadConfig {

    private static final Logger log = LoggerFactory.getLogger(SeguridadConfig.class);

    private final JwtFiltro jwtFiltro;
    private final PuntoDeEntradaNoAutorizado puntoDeEntrada;
    private final AccesoDenegadoJson accesoDenegado;
    private final boolean exigirAutenticacion;
    private final List<String> origenesPermitidos;

    public SeguridadConfig(
            JwtFiltro jwtFiltro,
            PuntoDeEntradaNoAutorizado puntoDeEntrada,
            AccesoDenegadoJson accesoDenegado,
            @Value("${federa.seguridad.exigir-autenticacion:false}") boolean exigirAutenticacion,
            @Value("${federa.seguridad.origenes:http://localhost:5173}")
            List<String> origenesPermitidos) {
        this.jwtFiltro = jwtFiltro;
        this.puntoDeEntrada = puntoDeEntrada;
        this.accesoDenegado = accesoDenegado;
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

                // No se usa la HttpSession de Spring: cada petición presenta
                // el token y se comprueba también su sesión revocable en BD.
                // Esto sigue permitiendo reinicios y varias instancias.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(this::rutas)

                // Sin esto, una petición sin token recibe 403 en vez de 401 y
                // el cliente no puede distinguir "iniciá sesión" de "no tenés
                // permiso".
                .exceptionHandling(e -> e.authenticationEntryPoint(puntoDeEntrada)
                        .accessDeniedHandler(accesoDenegado))

                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void rutas(
            org.springframework.security.config.annotation.web.configurers
                    .AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry registro) {

        registro
                // Iniciar sesión no puede exigir estar autenticado.
                .requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/auth/acceso").permitAll()

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

        // Las plantillas son fondos institucionales sin datos personales. Se
        // cargan mediante <img>, y el navegador no adjunta el token Bearer en
        // esa petición. La configuración y su edición continúan protegidas.
        registro.requestMatchers(HttpMethod.GET,
                        ApiRutas.V1 + "/configuracion/credencial/plantilla/*")
                .permitAll();

        // Los respaldos contienen toda la base de datos. Se protegen incluso
        // mientras el resto del padrón siga en el modo transitorio sin exigir
        // autenticación global.
        registro.requestMatchers(ApiRutas.V1 + "/administracion/backups/**")
                .hasAnyAuthority("RESPALDOS_ADMINISTRAR", "ROLE_ADMIN");
        registro.requestMatchers(ApiRutas.V1 + "/administracion/accesos/**")
                .hasAnyAuthority("USUARIOS_ADMINISTRAR", "ROLE_ADMIN");

        // Estos GET no son una simple consulta: generan el documento que se
        // envía a la impresora. Deben ir antes de la regla general de lectura
        // de productores.
        registro.requestMatchers(HttpMethod.GET,
                        ApiRutas.V1 + "/productores/*/credencial.pdf",
                        ApiRutas.V1 + "/cargos/*/credencial.pdf")
                .hasAnyAuthority("CARNETS_IMPRIMIR", "ROLE_ADMIN");

        // Separar lectura de edición es importante: un usuario de consulta
        // debe poder ver fotos, directorios y el diseño aplicado al carnet sin
        // recibir permiso para reemplazarlos. Las reglas de escritura más
        // abajo siguen exigiendo el permiso específico.
        registro.requestMatchers(HttpMethod.GET, ApiRutas.V1 + "/productores/**")
                .hasAnyAuthority("PRODUCTORES_VER", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.GET,
                        ApiRutas.V1 + "/cargos/*/imagenes/**",
                        ApiRutas.V1 + "/sindicatos/*/lista-fisica/**",
                        ApiRutas.V1 + "/sindicatos/*/directorio/**",
                        ApiRutas.V1 + "/centrales/*/directorio/**",
                        ApiRutas.V1 + "/federaciones/*/directorio/**")
                .hasAnyAuthority("PRODUCTORES_VER", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.GET, ApiRutas.V1 + "/configuracion/credencial/**")
                .hasAnyAuthority("PRODUCTORES_VER", "CARNETS_IMPRIMIR",
                        "CARNETS_DISENO", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.GET, ApiRutas.V1 + "/centrales/*/fases-impresion/**")
                .hasAnyAuthority("INFORMES_DESCARGAR", "CARNETS_IMPRIMIR",
                        "FASES_GESTIONAR", "ROLE_ADMIN");
        // El estado se consulta también desde Android y web, donde no existe
        // impresión física. Generar caras/reversos y confirmar tandas continúa
        // protegido por la regla amplia de CARNETS_IMPRIMIR de más abajo.
        registro.requestMatchers(HttpMethod.GET,
                        ApiRutas.V1 + "/sindicatos/*/credenciales/impresion")
                .hasAnyAuthority("PRODUCTORES_VER", "INFORMES_DESCARGAR", "ROLE_ADMIN");

        // Operaciones sensibles. El orden importa: las rutas particulares van
        // antes que la regla general de productores.
        registro.requestMatchers(ApiRutas.V1 + "/conciliaciones-udestro/**")
                .hasAnyAuthority("CONCILIAR_UDESTRO", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/importaciones/**")
                .hasAnyAuthority("IMPORTAR_PADRON", "ROLE_ADMIN");
        registro.requestMatchers(ApiRutas.V1 + "/personas/**",
                        ApiRutas.V1 + "/productores/*/revision-sie/**",
                        ApiRutas.V1 + "/productores/*/verificacion-sie/**")
                .hasAnyAuthority("SIE_REVISAR", "ROLE_ADMIN");
        registro.requestMatchers(ApiRutas.V1 + "/productores/*/credencial/impresion",
                        ApiRutas.V1 + "/productores/*/fase-impresion/**")
                .hasAnyAuthority("CARNETS_IMPRIMIR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PATCH, ApiRutas.V1 + "/productores/*/observacion")
                .hasAnyAuthority("PRODUCTORES_OBSERVAR", "PRODUCTORES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PUT, ApiRutas.V1 + "/productores/*/numero-lote")
                .hasAnyAuthority("NUMERO_LOTE_EDITAR", "LOTES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/productores/*/imagenes")
                .hasAnyAuthority("FOTOS_PRODUCTORES_EDITAR", "IMAGENES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.DELETE, ApiRutas.V1 + "/productores/*/imagenes")
                .hasAnyAuthority("FOTOS_PRODUCTORES_EDITAR", "IMAGENES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(ApiRutas.V1 + "/productores/*/observacion",
                        ApiRutas.V1 + "/productores/*/confirmar-correccion-nombre")
                .hasAnyAuthority("PRODUCTORES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/configuracion/credencial/**")
                .hasAnyAuthority("CARNETS_DISENO", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PUT, ApiRutas.V1 + "/configuracion/credencial/**")
                .hasAnyAuthority("CARNETS_DISENO", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PATCH, ApiRutas.V1 + "/configuracion/credencial/**")
                .hasAnyAuthority("CARNETS_DISENO", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.DELETE, ApiRutas.V1 + "/configuracion/credencial/**")
                .hasAnyAuthority("CARNETS_DISENO", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/centrales/*/fases-impresion/**")
                .hasAnyAuthority("FASES_GESTIONAR", "ROLE_ADMIN");
        registro.requestMatchers(ApiRutas.V1 + "/sindicatos/*/credenciales/impresion/**")
                .hasAnyAuthority("CARNETS_IMPRIMIR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.GET, ApiRutas.V1 + "/sindicatos/*/informes/**")
                .hasAnyAuthority("INFORMES_DESCARGAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.GET, ApiRutas.V1 + "/centrales/*/credenciales/impresion")
                .hasAnyAuthority("PRODUCTORES_VER", "INFORMES_DESCARGAR", "ROLE_ADMIN");
        registro.requestMatchers(ApiRutas.V1 + "/centrales/*/credenciales/impresion/**")
                .hasAnyAuthority("INFORMES_DESCARGAR", "ROLE_ADMIN");
        registro.requestMatchers(ApiRutas.V1 + "/federaciones/*/credenciales/impresion/**",
                        ApiRutas.V1 + "/sindicatos/*/informe.pdf")
                .hasAnyAuthority("INFORMES_DESCARGAR", "ROLE_ADMIN");
        registro.requestMatchers(ApiRutas.V1 + "/sindicatos/*/credenciales.pdf")
                .hasAnyAuthority("CARNETS_IMPRIMIR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.GET, ApiRutas.V1 + "/productores/papelera")
                .hasAnyAuthority("PRODUCTORES_ELIMINAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/productores/papelera/*/restaurar")
                .hasAnyAuthority("PRODUCTORES_ELIMINAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.DELETE, ApiRutas.V1 + "/productores/papelera/*")
                .hasAnyAuthority("PRODUCTORES_ELIMINAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.DELETE, ApiRutas.V1 + "/productores/*")
                .hasAnyAuthority("PRODUCTORES_ELIMINAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/productores")
                .hasAnyAuthority("PRODUCTORES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PUT, ApiRutas.V1 + "/productores/*")
                .hasAnyAuthority("PRODUCTORES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PATCH, ApiRutas.V1 + "/productores/*")
                .hasAnyAuthority("PRODUCTORES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/productores/*/imagenes/**",
                        ApiRutas.V1 + "/cargos/*/imagenes/**",
                        ApiRutas.V1 + "/sindicatos/*/lista-fisica/**")
                .hasAnyAuthority("IMAGENES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PUT, ApiRutas.V1 + "/productores/*/imagenes/**",
                        ApiRutas.V1 + "/cargos/*/imagenes/**",
                        ApiRutas.V1 + "/sindicatos/*/lista-fisica/**")
                .hasAnyAuthority("IMAGENES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PATCH, ApiRutas.V1 + "/productores/*/imagenes/**",
                        ApiRutas.V1 + "/cargos/*/imagenes/**",
                        ApiRutas.V1 + "/sindicatos/*/lista-fisica/**")
                .hasAnyAuthority("IMAGENES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.DELETE, ApiRutas.V1 + "/productores/*/imagenes/**",
                        ApiRutas.V1 + "/cargos/*/imagenes/**",
                        ApiRutas.V1 + "/sindicatos/*/lista-fisica/**")
                .hasAnyAuthority("IMAGENES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/vetos/**")
                .hasAnyAuthority("PRODUCTORES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PUT, ApiRutas.V1 + "/vetos/**")
                .hasAnyAuthority("PRODUCTORES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/sindicatos/*/directorio/**",
                        ApiRutas.V1 + "/centrales/*/directorio/**",
                        ApiRutas.V1 + "/federaciones/*/directorio/**",
                        ApiRutas.V1 + "/cargos/*/pie-firma")
                .hasAnyAuthority("DIRECTORIOS_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PUT, ApiRutas.V1 + "/sindicatos/*/directorio/**",
                        ApiRutas.V1 + "/centrales/*/directorio/**",
                        ApiRutas.V1 + "/federaciones/*/directorio/**",
                        ApiRutas.V1 + "/cargos/*/pie-firma")
                .hasAnyAuthority("DIRECTORIOS_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PATCH, ApiRutas.V1 + "/sindicatos/*/directorio/**",
                        ApiRutas.V1 + "/centrales/*/directorio/**",
                        ApiRutas.V1 + "/federaciones/*/directorio/**",
                        ApiRutas.V1 + "/cargos/*/pie-firma")
                .hasAnyAuthority("DIRECTORIOS_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.DELETE, ApiRutas.V1 + "/sindicatos/*/directorio/**",
                        ApiRutas.V1 + "/centrales/*/directorio/**",
                        ApiRutas.V1 + "/federaciones/*/directorio/**",
                        ApiRutas.V1 + "/cargos/*/pie-firma")
                .hasAnyAuthority("DIRECTORIOS_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/lotes/**", ApiRutas.V1 + "/sistemas/**")
                .hasAnyAuthority("LOTES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PUT, ApiRutas.V1 + "/lotes/**", ApiRutas.V1 + "/sistemas/**")
                .hasAnyAuthority("LOTES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.DELETE, ApiRutas.V1 + "/lotes/**", ApiRutas.V1 + "/sistemas/**")
                .hasAnyAuthority("LOTES_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/reuniones/**", ApiRutas.V1 + "/llamadas/**")
                .hasAnyAuthority("REUNIONES_GESTIONAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PUT, ApiRutas.V1 + "/reuniones/**", ApiRutas.V1 + "/llamadas/**")
                .hasAnyAuthority("REUNIONES_GESTIONAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PATCH, ApiRutas.V1 + "/reuniones/**", ApiRutas.V1 + "/llamadas/**")
                .hasAnyAuthority("REUNIONES_GESTIONAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.DELETE, ApiRutas.V1 + "/reuniones/**", ApiRutas.V1 + "/llamadas/**")
                .hasAnyAuthority("REUNIONES_GESTIONAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.POST, ApiRutas.V1 + "/centrales", ApiRutas.V1 + "/sindicatos", ApiRutas.V1 + "/federaciones")
                .hasAnyAuthority("JERARQUIA_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PUT, ApiRutas.V1 + "/centrales/**", ApiRutas.V1 + "/sindicatos/**", ApiRutas.V1 + "/federaciones/**")
                .hasAnyAuthority("JERARQUIA_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.PATCH, ApiRutas.V1 + "/centrales/**", ApiRutas.V1 + "/sindicatos/**", ApiRutas.V1 + "/federaciones/**")
                .hasAnyAuthority("JERARQUIA_EDITAR", "ROLE_ADMIN");
        registro.requestMatchers(HttpMethod.DELETE, ApiRutas.V1 + "/centrales/**", ApiRutas.V1 + "/sindicatos/**", ApiRutas.V1 + "/federaciones/**")
                .hasAnyAuthority("JERARQUIA_EDITAR", "ROLE_ADMIN");

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
        config.setAllowedOriginPatterns(origenesPermitidos);
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}
