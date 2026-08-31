package com.federa.backend.controller;

import com.federa.backend.dto.ProductorResponse;
import com.federa.backend.service.CredencialService;
import com.federa.backend.service.DirectorioService;
import com.federa.backend.service.ProductorService;
import com.federa.backend.service.RevisionSieProductorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductorControllerTest {

    @Test
    @DisplayName("la descarga de credencial nunca reutiliza un PDF anterior")
    void credencialSinCache() {
        var respuesta = ProductorController.comoAdjunto(
                new CredencialService.Descarga("credencial.pdf", new byte[]{1, 2, 3}));

        assertThat(respuesta.getHeaders().getCacheControl())
                .contains("no-store", "no-cache", "must-revalidate");
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("la impresión manual del anverso se registra en el productor")
    void registraImpresionManual() {
        CredencialService credenciales = mock(CredencialService.class);
        ProductorResponse esperado = mock(ProductorResponse.class);
        when(credenciales.confirmarAnversoImpreso(812L)).thenReturn(esperado);
        ProductorController controller = new ProductorController(
                mock(ProductorService.class),
                mock(DirectorioService.class),
                credenciales,
                mock(RevisionSieProductorService.class));

        assertThat(controller.confirmarImpresionCredencial(812L)).isSameAs(esperado);
        verify(credenciales).confirmarAnversoImpreso(812L);
    }
}
