package com.federa.backend.service;

import com.federa.backend.repository.ProductorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@Transactional
class PapeleraProductorRepositoryTest {
    @Autowired ProductorRepository productores;

    @Test
    void lasConsultasHabitualesOcultanLaPapeleraSinPerderLaFicha() {
        var muestra = productores.findAll(PageRequest.of(0, 1)).getContent();
        assumeTrue(!muestra.isEmpty(), "La base de pruebas no tiene productores");
        var productor = muestra.get(0);
        Long id = productor.getId();
        String ci = productor.getCi();
        Long sindicatoId = productor.getSindicato().getId();
        long cantidadAntes = productores.countBySindicatoId(sindicatoId);
        productor.setEliminadoEn(LocalDateTime.now());
        productores.flush();

        assertThat(productores.findById(id)).isEmpty();
        assertThat(productores.findAllById(List.of(id))).isEmpty();
        assertThat(productores.findAll()).allMatch(p -> !p.getId().equals(id));
        assertThat(productores.findBySindicatoId(sindicatoId))
                .allMatch(p -> !p.getId().equals(id));
        assertThat(productores.countBySindicatoId(sindicatoId))
                .isEqualTo(cantidadAntes - 1);
        assertThat(productores.findEnPapeleraPorId(id)).isPresent();
        assertThat(productores.listarPapelera(null, false, List.of(-1L)))
                .anyMatch(p -> p.getId().equals(id));
        if (ci != null) {
            assertThat(productores.findByCi(ci)).allMatch(p -> !p.getId().equals(id));
        }
    }
}
