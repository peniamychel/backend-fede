package com.federa.backend.service;

import com.federa.backend.dto.CredencialPrevia.Faltante;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.Central;
import com.federa.backend.model.ImagenCargo;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.TipoImagenCargo;
import com.federa.backend.model.enums.Ambito;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Requisitos que deben estar completos antes de emitir una credencial. */
@Component
public class RequisitosCredencial {

    private static final String EN_LA_FICHA = "Ficha del productor";
    private static final String EN_LA_JERARQUIA = "Jerarquía";
    private final ReglasDirectorio reglas;

    public RequisitosCredencial(ReglasDirectorio reglas) {
        this.reglas = reglas;
    }

    /** Revisa los tres niveles que componen el reverso de la credencial. */
    public List<Faltante> deJerarquia(Sindicato sindicato, Cargo ejecutivoFederacion,
                                     Cargo secretarioCentral, Cargo secretarioSindicato) {
        List<Faltante> faltantes = new ArrayList<>();
        Central central = sindicato.getCentral();

        if (vacio(central.getFederacion().getNumero())) {
            faltantes.add(new Faltante("Número de la federación",
                    "La federación " + central.getFederacion().getNombre()
                            + " no tiene número, y es la primera parte del código",
                    EN_LA_JERARQUIA + " → Federaciones → Editar"));
        }
        if (vacio(central.getAbreviatura())) {
            faltantes.add(new Faltante("Abreviatura de la central",
                    "La central " + central.getNombre()
                            + " no tiene sigla, y es la segunda parte del código",
                    EN_LA_JERARQUIA + " → Centrales → Editar"));
        }

        revisarSello(faltantes, "federación", central.getFederacion().getNombre(),
                central.getFederacion().getSelloClave(), "Federaciones", false);
        revisarSello(faltantes, "central", central.getNombre(), central.getSelloClave(),
                "Centrales", false);
        revisarSello(faltantes, "sindicato", sindicato.getNombre(), sindicato.getSelloClave(),
                "Sindicatos", true);

        revisarFirmante(faltantes, ejecutivoFederacion, "Ejecutivo de la federación",
                central.getFederacion().getNombre(), "Federaciones",
                reglas.firmaObligatoria(Ambito.FEDERACION));
        revisarFirmante(faltantes, secretarioCentral, "Secretario General de la central",
                central.getNombre(), "Centrales", reglas.firmaObligatoria(Ambito.CENTRAL));
        revisarFirmante(faltantes, secretarioSindicato, "Secretario General del sindicato",
                sindicato.getNombre(), "Sindicatos",
                reglas.firmaObligatoria(Ambito.SINDICATO));
        return faltantes;
    }

    public List<Faltante> delProductor(Productor productor, boolean tieneFoto) {
        List<Faltante> faltantes = new ArrayList<>();
        if (vacio(apellidosDe(productor))) {
            faltantes.add(new Faltante("Apellidos",
                    productor.getNombres() + " no tiene apellidos cargados",
                    EN_LA_FICHA + " → Editar"));
        }
        if (vacio(productor.getCi())) {
            faltantes.add(new Faltante("Cédula", "Sin cédula la credencial no identifica a nadie",
                    EN_LA_FICHA + " → Editar"));
        }
        if (!tieneFoto) {
            faltantes.add(new Faltante("Fotografía", "La tarjeta saldría con el recuadro vacío",
                    EN_LA_FICHA + " → Fotografía"));
        }
        if (productor.getCorrelativo() == null) {
            faltantes.add(new Faltante("Número en la central",
                    "No tiene número asignado; se lo da la migración productor-correlativo.sql "
                            + "o guardar la ficha de nuevo",
                    EN_LA_FICHA + " → Editar → Guardar"));
        }
        return faltantes;
    }

    private void revisarSello(List<Faltante> faltantes, String nivel, String nombre,
                              String clave, String pantalla, boolean masculino) {
        if (vacio(clave)) {
            String articulo = masculino ? "El " : "La ";
            String enlace = masculino ? "del " : "de la ";
            faltantes.add(new Faltante("Sello " + enlace + nivel,
                    articulo + nivel + " " + nombre + " no tiene sello institucional",
                    EN_LA_JERARQUIA + " → " + pantalla + " → Directorio"));
        }
    }

    private void revisarFirmante(List<Faltante> faltantes, Cargo cargo, String titulo,
                                 String organizacion, String pantalla,
                                 boolean firmaObligatoria) {
        String donde = EN_LA_JERARQUIA + " → " + pantalla + " → Directorio";
        if (cargo == null) {
            if (!firmaObligatoria) {
                return;
            }
            faltantes.add(new Faltante(titulo,
                    organizacion + " no tiene " + titulo.toLowerCase() + " en funciones",
                    donde));
            return;
        }
        if (firmaObligatoria && !tieneImagen(cargo, TipoImagenCargo.FIRMA)) {
            faltantes.add(new Faltante("Firma del " + titulo.toLowerCase(),
                    cargo.getProductor().getNombreCompleto() + " no subió su firma", donde));
        }
    }

    public boolean firmaObligatoria(Ambito ambito) {
        return reglas.firmaObligatoria(ambito);
    }

    private boolean tieneImagen(Cargo cargo, TipoImagenCargo tipo) {
        for (ImagenCargo imagen : cargo.getImagenes()) {
            if (imagen.getTipo() == tipo) return true;
        }
        return false;
    }

    private static String apellidosDe(Productor productor) {
        return productor.getApellidosCorregidos() != null
                ? productor.getApellidosCorregidos() : productor.getApellidos();
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
