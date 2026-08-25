package com.federa.backend.service;

import com.federa.backend.model.enums.Ambito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reglas configurables de firma, pie de firma y sello según el nivel. */
@Component
public class ReglasDirectorio {

    private final boolean pieFirmaSindicatoHabilitado;
    private final boolean firmaSindicatoObligatoria;

    public ReglasDirectorio(
            @Value("${federa.directorio.pie-firma-sindicato-habilitado:false}")
            boolean pieFirmaSindicatoHabilitado,
            @Value("${federa.directorio.firma-sindicato-obligatoria:false}")
            boolean firmaSindicatoObligatoria) {
        this.pieFirmaSindicatoHabilitado = pieFirmaSindicatoHabilitado;
        this.firmaSindicatoObligatoria = firmaSindicatoObligatoria;
    }

    /** Federación y central lo admiten; sindicato depende de la configuración. */
    public boolean permitePieFirmaImagen(Ambito ambito) {
        return ambito != Ambito.SINDICATO || pieFirmaSindicatoHabilitado;
    }

    /** La firma del sindicato es opcional por defecto. */
    public boolean firmaObligatoria(Ambito ambito) {
        return ambito != Ambito.SINDICATO || firmaSindicatoObligatoria;
    }

    /** El sello institucional es requisito en los tres niveles. */
    public boolean selloObligatorio(Ambito ambito) {
        return true;
    }
}
