package com.federa.backend.integracion.sie;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "federa.sie")
public class SiePropiedades {

    private boolean habilitado = true;
    private String url = "https://api.itt.sie.gob.bo/persona/busqueda";
    private String token;
    private int conexionSegundos = 5;
    private int lecturaSegundos = 15;

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getConexionSegundos() {
        return conexionSegundos;
    }

    public void setConexionSegundos(int conexionSegundos) {
        this.conexionSegundos = conexionSegundos;
    }

    public int getLecturaSegundos() {
        return lecturaSegundos;
    }

    public void setLecturaSegundos(int lecturaSegundos) {
        this.lecturaSegundos = lecturaSegundos;
    }
}
