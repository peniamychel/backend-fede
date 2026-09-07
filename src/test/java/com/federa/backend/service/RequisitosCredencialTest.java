package com.federa.backend.service;

import com.federa.backend.dto.CredencialPrevia.Faltante;
import com.federa.backend.model.Cargo;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.EstadoRevisionSieProductor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequisitosCredencialTest {

    @Test
    void laFirmaSindicalOpcionalNoBloqueaLaCredencial() {
        Federacion federacion = mock(Federacion.class);
        Central central = mock(Central.class);
        Sindicato sindicato = mock(Sindicato.class);
        when(sindicato.getCentral()).thenReturn(central);
        when(sindicato.getNombre()).thenReturn("LIBERTAD");
        when(sindicato.getSelloClave()).thenReturn("sellos/sindicato.png");
        when(central.getFederacion()).thenReturn(federacion);
        when(central.getNombre()).thenReturn("IVIRGARZAMA");
        when(central.getAbreviatura()).thenReturn("IVI");
        when(central.getSelloClave()).thenReturn("sellos/central.png");
        when(federacion.getNombre()).thenReturn("CARRASCO");
        when(federacion.getNumero()).thenReturn("2");
        when(federacion.getSelloClave()).thenReturn("sellos/federacion.png");

        Cargo ejecutivo = cargoSinFirma("EJECUTIVO");
        Cargo secretarioCentral = cargoSinFirma("CENTRAL");
        RequisitosCredencial requisitos = new RequisitosCredencial(
                new ReglasDirectorio(false, false));

        List<Faltante> faltantes = requisitos.deJerarquia(
                sindicato, ejecutivo, secretarioCentral, null);

        assertThat(faltantes).extracting(Faltante::campo)
                .contains("Firma del ejecutivo de la federación",
                        "Firma del secretario general de la central")
                .doesNotContain("Firma del secretario general del sindicato");
    }

    @Test
    void elSecretarioSindicalSigueSiendoRequisitoCuandoSeConfiguraComoObligatorio() {
        Federacion federacion = mock(Federacion.class);
        Central central = mock(Central.class);
        Sindicato sindicato = mock(Sindicato.class);
        when(sindicato.getCentral()).thenReturn(central);
        when(sindicato.getNombre()).thenReturn("LIBERTAD");
        when(sindicato.getSelloClave()).thenReturn("sellos/sindicato.png");
        when(central.getFederacion()).thenReturn(federacion);
        when(central.getNombre()).thenReturn("IVIRGARZAMA");
        when(central.getAbreviatura()).thenReturn("IVI");
        when(central.getSelloClave()).thenReturn("sellos/central.png");
        when(federacion.getNombre()).thenReturn("CARRASCO");
        when(federacion.getNumero()).thenReturn("2");
        when(federacion.getSelloClave()).thenReturn("sellos/federacion.png");

        RequisitosCredencial requisitos = new RequisitosCredencial(
                new ReglasDirectorio(false, true));

        List<Faltante> faltantes = requisitos.deJerarquia(
                sindicato, cargoSinFirma("EJECUTIVO"), cargoSinFirma("CENTRAL"), null);

        assertThat(faltantes).extracting(Faltante::campo)
                .contains("Secretario General del sindicato");
    }

    @Test
    void elNumeroDeLoteEsObligatorioParaImprimirElCarnetDeProductor() {
        Productor productor = mock(Productor.class);
        when(productor.getNombres()).thenReturn("MARÍA");
        when(productor.getApellidos()).thenReturn("PÉREZ");
        when(productor.getCi()).thenReturn("1234567");
        when(productor.getCorrelativo()).thenReturn(10);
        RequisitosCredencial requisitos = new RequisitosCredencial(
                new ReglasDirectorio(false, false));

        List<Faltante> sinLote = requisitos.delProductor(productor, true, false);
        List<Faltante> conLote = requisitos.delProductor(productor, true, true);

        assertThat(sinLote).extracting(Faltante::campo).contains("Número de lote");
        assertThat(conLote).extracting(Faltante::campo).doesNotContain("Número de lote");
    }

    @Test
    void unaObservacionManualExcluyeAlProductorDeLaImpresion() {
        Productor productor = mock(Productor.class);
        when(productor.isEstado()).thenReturn(true);
        when(productor.getNombres()).thenReturn("MARÍA");
        when(productor.getApellidos()).thenReturn("PÉREZ");
        when(productor.getCi()).thenReturn("1234567");
        when(productor.getCorrelativo()).thenReturn(10);
        when(productor.isObservado()).thenReturn(true);
        when(productor.getObservacionManual()).thenReturn("REVISAR CÉDULA");
        RequisitosCredencial requisitos = new RequisitosCredencial(
                new ReglasDirectorio(false, false));

        List<Faltante> faltantes = requisitos.delProductor(productor, true, true);

        assertThat(faltantes).filteredOn(f -> f.campo().equals("Observación manual"))
                .singleElement()
                .satisfies(f -> assertThat(f.detalle()).isEqualTo("REVISAR CÉDULA"));
    }

    @Test
    void unProductorDeshabilitadoQuedaFueraDeLaImpresion() {
        Productor productor = mock(Productor.class);
        when(productor.getNombres()).thenReturn("MARÍA");
        when(productor.getApellidos()).thenReturn("PÉREZ");
        when(productor.getCi()).thenReturn("1234567");
        when(productor.getCorrelativo()).thenReturn(10);
        when(productor.isEstado()).thenReturn(false);
        RequisitosCredencial requisitos = new RequisitosCredencial(
                new ReglasDirectorio(false, false));

        assertThat(requisitos.delProductor(productor, true, true))
                .extracting(Faltante::campo)
                .contains("Productor deshabilitado");
    }

    @Test
    void unEstadoSieNaranjaQuedaFueraDeLaImpresion() {
        Productor productor = mock(Productor.class);
        when(productor.isEstado()).thenReturn(true);
        when(productor.getNombres()).thenReturn("MARÍA");
        when(productor.getApellidos()).thenReturn("PÉREZ");
        when(productor.getCi()).thenReturn("1234567");
        when(productor.getCorrelativo()).thenReturn(10);
        when(productor.isRevisionSieBloqueaImpresion()).thenReturn(true);
        when(productor.getRevisionSieMensaje()).thenReturn("No encontrado en SIE");
        RequisitosCredencial requisitos = new RequisitosCredencial(
                new ReglasDirectorio(false, false));

        assertThat(requisitos.delProductor(productor, true, true))
                .extracting(Faltante::campo)
                .contains("Revisión SIE pendiente");
    }

    private Cargo cargoSinFirma(String nombre) {
        Cargo cargo = mock(Cargo.class);
        Productor productor = mock(Productor.class);
        when(productor.getNombreCompleto()).thenReturn(nombre);
        when(cargo.getProductor()).thenReturn(productor);
        when(cargo.getImagenes()).thenReturn(new ArrayList<>());
        return cargo;
    }
}
