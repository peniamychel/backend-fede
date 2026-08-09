package com.federa.backend.config;

import com.federa.backend.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * Documenta en OpenAPI los errores que produce {@code ManejadorGlobalErrores}.
 * <p>
 * Se hace por código y no anotando 39 operaciones a mano, para que no se
 * desincronice cuando se agregue un endpoint. Los códigos se asignan según la
 * forma real de cada operación, no a todas por igual: un listado sin parámetros
 * no puede dar 404, y decir que sí sería documentación falsa.
 */
@Configuration
public class RespuestasErrorCustomizer {

    private static final String REF = "#/components/schemas/ErrorResponse";

    /**
     * Mete {@link ErrorResponse} en components. Hace falta explícitamente
     * porque ningún método de controlador lo devuelve en su firma —lo produce
     * el {@code @RestControllerAdvice}— y sin esto las referencias quedarían
     * colgadas.
     */
    @Bean
    public OpenApiCustomizer registrarSchemaDeError() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ModelConverters.getInstance().read(ErrorResponse.class)
                    .forEach(openApi.getComponents()::addSchemas);
        };
    }

    @Bean
    public OperationCustomizer respuestasDeError() {
        return (operation, handlerMethod) -> {
            ApiResponses respuestas = operation.getResponses();
            if (respuestas == null) {
                return operation;
            }

            // 400: solo si hay algo que el cliente pueda mandar mal.
            if (operation.getRequestBody() != null) {
                agregar(respuestas, "400", "Falló la validación del cuerpo. El campo `errores` "
                        + "indica qué atributo se rechazó y por qué.");
            } else if (tieneParametros(operation)) {
                agregar(respuestas, "400", "Algún parámetro no se pudo interpretar "
                        + "(por ejemplo, un id no numérico).");
            }

            // 404: solo donde la ruta identifica un recurso puntual por id. Los
            // /por-cedula/{ci} y /por-carnet/{carnet} devuelven lista vacía, no 404.
            if (identificaRecursoPorId(operation)) {
                agregar(respuestas, "404", "No existe ningún recurso con ese id.");
            }

            // 409: reglas del padrón (nombre repetido, borrar algo con hijos) y
            // violaciones de integridad. Solo las escrituras pueden producirlo;
            // los PATCH de resolver/reabrir no, por eso se mira el verbo HTTP.
            if (esEscritura(handlerMethod)) {
                agregar(respuestas, "409", "La operación choca con una regla del padrón: un "
                        + "nombre que ya existe, o el borrado de algo que todavía tiene "
                        + "registros dependientes.");
            }

            return operation;
        };
    }

    private void agregar(ApiResponses respuestas, String codigo, String descripcion) {
        if (respuestas.containsKey(codigo)) {
            return; // una @ApiResponse puesta a mano en el método tiene prioridad
        }
        respuestas.addApiResponse(codigo, new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref(REF)))));
    }

    private boolean tieneParametros(Operation operation) {
        return operation.getParameters() != null && !operation.getParameters().isEmpty();
    }

    private boolean identificaRecursoPorId(Operation operation) {
        return operation.getParameters() != null && operation.getParameters().stream()
                .anyMatch(p -> "path".equals(p.getIn()) && "id".equals(p.getName()));
    }

    private boolean esEscritura(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(PostMapping.class)
                || handlerMethod.hasMethodAnnotation(PutMapping.class)
                || handlerMethod.hasMethodAnnotation(DeleteMapping.class);
    }
}
