package com.federa.backend.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "federa.backup")
public class BackupPropiedades {

    private boolean habilitado = true;
    private Path raiz = Path.of("./respaldos");
    private String ejecutable = "mariadb-dump";
    private String archivoCredenciales;
    private String host = "localhost";
    private int puerto = 3307;
    private String baseDatos = "federa";
    private String usuario = "root";
    private String contrasena = "root";
    private int maxDuracionMinutos = 30;
    private final Automatico automatico = new Automatico();
    private final Retencion retencion = new Retencion();

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    public Path getRaiz() {
        return raiz;
    }

    public void setRaiz(Path raiz) {
        this.raiz = raiz;
    }

    public String getEjecutable() {
        return ejecutable;
    }

    public void setEjecutable(String ejecutable) {
        this.ejecutable = ejecutable;
    }

    public String getArchivoCredenciales() {
        return archivoCredenciales;
    }

    public void setArchivoCredenciales(String archivoCredenciales) {
        this.archivoCredenciales = archivoCredenciales;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPuerto() {
        return puerto;
    }

    public void setPuerto(int puerto) {
        this.puerto = puerto;
    }

    public String getBaseDatos() {
        return baseDatos;
    }

    public void setBaseDatos(String baseDatos) {
        this.baseDatos = baseDatos;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public int getMaxDuracionMinutos() {
        return maxDuracionMinutos;
    }

    public void setMaxDuracionMinutos(int maxDuracionMinutos) {
        this.maxDuracionMinutos = maxDuracionMinutos;
    }

    public Automatico getAutomatico() {
        return automatico;
    }

    public Retencion getRetencion() {
        return retencion;
    }

    public static class Automatico {
        private boolean habilitado = true;
        private String cron = "0 0 2 * * *";
        private String zona = "America/La_Paz";

        public boolean isHabilitado() {
            return habilitado;
        }

        public void setHabilitado(boolean habilitado) {
            this.habilitado = habilitado;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getZona() {
            return zona;
        }

        public void setZona(String zona) {
            this.zona = zona;
        }
    }

    public static class Retencion {
        private int diarios = 7;
        private int semanales = 4;
        private int mensuales = 12;

        public int getDiarios() {
            return diarios;
        }

        public void setDiarios(int diarios) {
            this.diarios = diarios;
        }

        public int getSemanales() {
            return semanales;
        }

        public void setSemanales(int semanales) {
            this.semanales = semanales;
        }

        public int getMensuales() {
            return mensuales;
        }

        public void setMensuales(int mensuales) {
            this.mensuales = mensuales;
        }
    }
}
