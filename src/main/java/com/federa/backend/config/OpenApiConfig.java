package com.federa.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Metadatos de la documentación OpenAPI que sirve Swagger UI. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiPadronFedera() {
        return new OpenAPI()
                .info(new Info()
                        .title("API del padrón — FEDERA")
                        .version("v1")
                        .description("""
                                Gestión del padrón de productores de la federación: centrales,
                                sindicatos, productores, lotes y credenciales.

                                **Sobre los datos.** El padrón viene de una planilla con 4.051
                                filas y muchas inconsistencias, y la API está pensada para
                                aceptarlas en vez de rechazarlas:

                                - La cédula y el carné de productor son texto y admiten repetidos:
                                  hay 27 cédulas y 208 carnés duplicados en el padrón real. Los
                                  duplicados se consultan, no se bloquean.
                                - El número de lote es texto porque hay rangos (`30-31`) y códigos
                                  (`B.N47`).
                                - El estado del lote, la extensión y el mercado se mandan tal como
                                  están escritos en la planilla (`C-S`, `FRANSIONADOS`, `detallista`)
                                  y el backend los normaliza. Un estado no reconocido se guarda como
                                  `DESCONOCIDO` conservando el texto original en `estadoOriginal`,
                                  en vez de rechazar la carga.

                                **Errores.** Todos devuelven el mismo cuerpo con `estado`,
                                `mensaje`, `errores` y `momento`: 400 validación, 404 id inexistente,
                                409 regla de negocio (nombre duplicado, o borrar algo que todavía
                                tiene hijos colgando).""")
                        .contact(new Contact().name("FEDERA"))
                        .license(new License().name("Uso interno")))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Entorno local")));
    }
}
