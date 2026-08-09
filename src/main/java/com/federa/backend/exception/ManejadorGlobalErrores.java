package com.federa.backend.exception;

import com.federa.backend.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte las excepciones de la aplicación en respuestas JSON uniformes.
 * <p>
 * El cuerpo siempre es {@link ErrorResponse}; qué operación puede devolver cada
 * código se documenta en el contrato OpenAPI desde
 * {@code config.RespuestasErrorCustomizer}.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.de(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> reglaNegocio(ReglaNegocioException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.de(HttpStatus.CONFLICT, ex.getMessage()));
    }

    /**
     * Archivo subido que no sirve: planilla ilegible, imagen que pesa de más o
     * que no es una imagen. Cubre también a PlanillaInvalidaException, que
     * hereda de esta.
     */
    @ExceptionHandler(ArchivoInvalidoException.class)
    public ResponseEntity<ErrorResponse> archivoInvalido(ArchivoInvalidoException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * El multipart superó el límite de spring.servlet.multipart. Sin este
     * handler Spring devuelve un 500 sin explicación.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> archivoDemasiadoGrande(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.de(HttpStatus.PAYLOAD_TOO_LARGE,
                        "El archivo supera el tamaño máximo admitido por el servidor."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ErrorResponse.deValidacion(errores));
    }

    /** JSON mal formado o valor de enum inexistente en el cuerpo. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de(HttpStatus.BAD_REQUEST, "No se pudo leer el cuerpo de la petición: "
                        + ex.getMostSpecificCause().getMessage()));
    }

    /** Parámetro de URL con tipo equivocado (id no numérico, enum inexistente). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.de(HttpStatus.BAD_REQUEST,
                "Valor inválido para el parámetro '" + ex.getName() + "': " + ex.getValue()));
    }

    /**
     * Credenciales incorrectas.
     * <p>
     * El mensaje es el mismo tanto si el usuario no existe como si la
     * contraseña está mal: distinguirlos le permitiría a alguien averiguar qué
     * usuarios existen probando nombres.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.de(HttpStatus.UNAUTHORIZED,
                        "Usuario o contraseña incorrectos"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.de(HttpStatus.CONFLICT,
                        "La operación viola una restricción de la base de datos"));
    }
}
