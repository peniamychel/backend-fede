package com.federa.backend.integracion.sie;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PersonaSieClientTest {

    @Test
    void enviaElContratoEsperadoYAceptaLaRespuesta201() {
        SiePropiedades propiedades = new SiePropiedades();
        propiedades.setUrl("https://sie.prueba/persona/busqueda");
        propiedades.setToken("token-secreto-prueba");
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(propiedades.getUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION,
                        "Bearer token-secreto-prueba"))
                .andExpect(content().json("""
                        {"carnetIdentidad":"4487439","complemento":""}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "statusCode": 200,
                                  "data": [{
                                    "carnet_identidad": "4487439",
                                    "complemento": "",
                                    "paterno": "YAURI",
                                    "materno": "PUCHO",
                                    "nombre": "INOCENTES"
                                  }]
                                }
                                """));

        PersonaSieClient cliente = new PersonaSieClient(propiedades, constructor.build());
        PersonaSieClient.Resultado resultado = cliente.buscar("4487439", "");

        assertThat(resultado.estado()).isEqualTo(PersonaSieClient.Estado.ENCONTRADA);
        assertThat(resultado.persona().nombre()).isEqualTo("INOCENTES");
        assertThat(resultado.persona().paterno()).isEqualTo("YAURI");
        servidor.verify();
    }

    @Test
    void interpretaElStatusCode404YDataCeroComoPersonaNoEncontrada() {
        SiePropiedades propiedades = new SiePropiedades();
        propiedades.setUrl("https://sie.prueba/persona/busqueda");
        propiedades.setToken("token-secreto-prueba");
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(propiedades.getUrl()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "statusCode": 404,
                                  "message": ["Registro No Encontrado !!"],
                                  "data": "0",
                                  "code": ""
                                }
                                """));

        PersonaSieClient cliente = new PersonaSieClient(propiedades, constructor.build());
        PersonaSieClient.Resultado resultado = cliente.buscar("12834877", "");

        assertThat(resultado.estado()).isEqualTo(PersonaSieClient.Estado.NO_ENCONTRADA);
        assertThat(resultado.mensaje()).contains("no fue encontrada");
        servidor.verify();
    }

    @Test
    void interpretaTambienUnHttp404ComoPersonaNoEncontrada() {
        SiePropiedades propiedades = new SiePropiedades();
        propiedades.setUrl("https://sie.prueba/persona/busqueda");
        propiedades.setToken("token-secreto-prueba");
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(propiedades.getUrl()))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"statusCode":404,"data":"0"}
                                """));

        PersonaSieClient cliente = new PersonaSieClient(propiedades, constructor.build());
        PersonaSieClient.Resultado resultado = cliente.buscar("12834877", "");

        assertThat(resultado.estado()).isEqualTo(PersonaSieClient.Estado.NO_ENCONTRADA);
        servidor.verify();
    }
}
