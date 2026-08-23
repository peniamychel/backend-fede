package com.federa.backend.integracion.sie;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class PersonaSieClient {

    private static final Logger log = LoggerFactory.getLogger(PersonaSieClient.class);

    private final SiePropiedades propiedades;
    private final RestClient cliente;

    @Autowired
    public PersonaSieClient(SiePropiedades propiedades, RestClient.Builder constructor) {
        this.propiedades = propiedades;
        SimpleClientHttpRequestFactory peticiones = new SimpleClientHttpRequestFactory();
        peticiones.setConnectTimeout(Duration.ofSeconds(propiedades.getConexionSegundos()));
        peticiones.setReadTimeout(Duration.ofSeconds(propiedades.getLecturaSegundos()));
        this.cliente = constructor.requestFactory(peticiones).build();
    }

    PersonaSieClient(SiePropiedades propiedades, RestClient cliente) {
        this.propiedades = propiedades;
        this.cliente = cliente;
    }

    public Resultado buscar(String carnetIdentidad, String complemento) {
        if (!propiedades.isHabilitado()) {
            return Resultado.noDisponible("La consulta SIE está deshabilitada.");
        }
        if (propiedades.getToken() == null || propiedades.getToken().isBlank()) {
            return Resultado.noDisponible(
                    "El backend no tiene configurado el token de consulta SIE.");
        }

        try {
            RespuestaSie respuesta = cliente.post()
                    .uri(propiedades.getUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + propiedades.getToken().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new PeticionSie(carnetIdentidad, complemento))
                    .retrieve()
                    .body(RespuestaSie.class);

            if (respuesta == null) {
                return Resultado.noDisponible(
                        "El servicio SIE devolvió una respuesta vacía.");
            }
            if (Integer.valueOf(404).equals(respuesta.statusCode())) {
                return Resultado.noEncontrada();
            }
            JsonNode datos = respuesta.data();
            if (datos == null || datos.isNull()
                    || (datos.isArray() && datos.isEmpty())
                    || esCero(datos)) {
                return Resultado.noEncontrada();
            }
            if (!datos.isArray()) {
                return Resultado.noDisponible(
                        "El servicio SIE devolvió datos con un formato no reconocido.");
            }

            List<PersonaSie> personas = new ArrayList<>();
            for (JsonNode dato : datos) {
                if (dato.isObject()) {
                    personas.add(new PersonaSie(
                            texto(dato, "carnet_identidad"),
                            texto(dato, "complemento"),
                            texto(dato, "paterno"),
                            texto(dato, "materno"),
                            texto(dato, "nombre")));
                }
            }
            if (personas.isEmpty()) {
                return Resultado.noDisponible(
                        "El servicio SIE no devolvió una persona utilizable.");
            }

            PersonaSie persona = personas.stream()
                    .filter(p -> carnetIdentidad.equals(limpiar(p.carnetIdentidad())))
                    .filter(p -> complemento.equalsIgnoreCase(limpiar(p.complemento())))
                    .findFirst()
                    .orElse(personas.get(0));
            return Resultado.encontrada(persona);
        } catch (RestClientResponseException e) {
            int codigo = e.getStatusCode().value();
            log.warn("La consulta SIE respondió HTTP {}", codigo);
            if (codigo == 404) {
                return Resultado.noEncontrada();
            }
            if (codigo == 401 || codigo == 403) {
                return Resultado.noDisponible(
                        "El token de consulta SIE no es válido o ha vencido.");
            }
            return Resultado.noDisponible(
                    "El servicio SIE rechazó la consulta (HTTP " + codigo + ").");
        } catch (RestClientException e) {
            log.warn("No se pudo conectar con el servicio SIE: {}", e.getClass().getSimpleName());
            return Resultado.noDisponible(
                    "No se pudo conectar con el servicio SIE en este momento.");
        }
    }

    private static String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static boolean esCero(JsonNode datos) {
        return (datos.isTextual() && "0".equals(datos.asText().trim()))
                || (datos.isNumber() && datos.asInt() == 0);
    }

    private static String texto(JsonNode objeto, String campo) {
        JsonNode valor = objeto.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private record PeticionSie(String carnetIdentidad, String complemento) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespuestaSie(Integer statusCode, JsonNode data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonaSie(
            @JsonProperty("carnet_identidad") String carnetIdentidad,
            String complemento,
            String paterno,
            String materno,
            String nombre
    ) {
    }

    public record Resultado(Estado estado, PersonaSie persona, String mensaje) {

        static Resultado encontrada(PersonaSie persona) {
            return new Resultado(Estado.ENCONTRADA, persona, null);
        }

        static Resultado noEncontrada() {
            return new Resultado(Estado.NO_ENCONTRADA, null,
                    "La cédula no fue encontrada en el servicio SIE.");
        }

        static Resultado noDisponible(String mensaje) {
            return new Resultado(Estado.NO_DISPONIBLE, null, mensaje);
        }
    }

    public enum Estado {
        ENCONTRADA,
        NO_ENCONTRADA,
        NO_DISPONIBLE
    }
}
