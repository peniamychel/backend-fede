package com.federa.backend.service;

import com.federa.backend.dto.ConsultaPersonaResponse;
import com.federa.backend.integracion.sie.PersonaSieClient;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;

import static com.federa.backend.dto.ConsultaPersonaResponse.Estado.ENCONTRADA;
import static com.federa.backend.dto.ConsultaPersonaResponse.Estado.NO_DISPONIBLE;
import static com.federa.backend.dto.ConsultaPersonaResponse.Estado.NO_ENCONTRADA;

@Service
public class ConsultaPersonaService {

    private final PersonaSieClient sie;

    public ConsultaPersonaService(PersonaSieClient sie) {
        this.sie = sie;
    }

    public ConsultaPersonaResponse consultar(String ci) {
        Cedula cedula = separar(ci);
        PersonaSieClient.Resultado resultado = sie.buscar(cedula.numero(), cedula.complemento());
        return switch (resultado.estado()) {
            case NO_ENCONTRADA -> new ConsultaPersonaResponse(
                    NO_ENCONTRADA, null, null, resultado.mensaje());
            case NO_DISPONIBLE -> new ConsultaPersonaResponse(
                    NO_DISPONIBLE, null, null, resultado.mensaje());
            case ENCONTRADA -> {
                PersonaSieClient.PersonaSie persona = resultado.persona();
                String nombres = Textos.normalizarParaGuardar(persona.nombre());
                String apellidos = Textos.normalizarParaGuardar(unir(
                        persona.paterno(), persona.materno()));
                if (nombres == null) {
                    yield new ConsultaPersonaResponse(NO_DISPONIBLE, null, null,
                            "SIE devolvió una persona sin nombres utilizables.");
                }
                yield new ConsultaPersonaResponse(ENCONTRADA, nombres, apellidos,
                        "Datos encontrados en el servicio SIE.");
            }
        };
    }

    private Cedula separar(String valor) {
        String limpia = valor.trim();
        int guion = limpia.indexOf('-');
        if (guion < 0) return new Cedula(limpia, "");
        return new Cedula(
                limpia.substring(0, guion).trim(),
                limpia.substring(guion + 1).trim());
    }

    private String unir(String paterno, String materno) {
        String primero = paterno == null ? "" : paterno.trim();
        String segundo = materno == null ? "" : materno.trim();
        return (primero + " " + segundo).trim();
    }

    private record Cedula(String numero, String complemento) {
    }
}
