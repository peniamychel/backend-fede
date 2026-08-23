package com.federa.backend.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class BackupServiceTest {

    @TempDir
    Path temporal;

    private BackupPropiedades propiedades;
    private BackupService servicio;

    @BeforeEach
    void preparar() {
        propiedades = new BackupPropiedades();
        propiedades.setRaiz(temporal);
        propiedades.setBaseDatos("federa_prueba");
        Executor directo = Runnable::run;
        servicio = new BackupService(
                propiedades,
                new DumpCorrecto(),
                new ObjectMapper().findAndRegisterModules(),
                directo);
        servicio.prepararDirectorio();
    }

    @Test
    void creaManifiestoArchivoComprimidoYChecksum() throws IOException {
        BackupInfo iniciado = servicio.iniciarManual("admin");

        BackupInfo terminado = servicio.obtener(iniciado.id());
        assertThat(terminado.estado()).isEqualTo(EstadoBackup.COMPLETADO);
        assertThat(terminado.tipo()).isEqualTo(TipoBackup.MANUAL);
        assertThat(terminado.solicitadoPor()).isEqualTo("admin");
        assertThat(terminado.sha256()).hasSize(64);
        assertThat(terminado.tamanoBytes()).isPositive();
        assertThat(Files.isRegularFile(temporal.resolve(terminado.archivo()))).isTrue();
        assertThat(Files.readString(temporal.resolve(terminado.id() + ".sha256")))
                .contains(terminado.sha256(), terminado.archivo());
    }

    @Test
    void eliminaTodosLosArchivosDelRespaldo() {
        BackupInfo creado = servicio.iniciarManual("admin");

        servicio.eliminar(creado.id());

        assertThat(servicio.listar()).isEmpty();
        assertThat(temporal.resolve(creado.id() + ".sql.gz")).doesNotExist();
        assertThat(temporal.resolve(creado.id() + ".sha256")).doesNotExist();
        assertThat(temporal.resolve(creado.id() + ".json")).doesNotExist();
    }

    @Test
    void retencionNuncaEliminaElUltimoAutomaticoCorrecto() {
        propiedades.getRetencion().setDiarios(0);
        propiedades.getRetencion().setSemanales(0);
        propiedades.getRetencion().setMensuales(0);

        servicio.iniciarAutomatico();
        servicio.iniciarAutomatico();
        servicio.iniciarAutomatico();

        List<BackupInfo> automaticos = servicio.listar().stream()
                .filter(b -> b.tipo() == TipoBackup.AUTOMATICO)
                .toList();
        assertThat(automaticos).hasSize(1);
        assertThat(automaticos.get(0).estado()).isEqualTo(EstadoBackup.COMPLETADO);
    }

    private static class DumpCorrecto implements EjecutorDumpMariaDb {
        @Override
        public ResultadoDump ejecutar(Path destinoTemporal) {
            byte[] sql = "CREATE DATABASE federa_prueba;\nCREATE TABLE ejemplo(id INT);\n"
                    .getBytes(StandardCharsets.UTF_8);
            try (GZIPOutputStream salida = new GZIPOutputStream(
                    Files.newOutputStream(destinoTemporal))) {
                salida.write(sql);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            try {
                return new ResultadoDump(Files.size(destinoTemporal), "MariaDB prueba");
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }
}
