package com.federa.backend.backup;

import java.nio.file.Path;

public interface EjecutorDumpMariaDb {

    ResultadoDump ejecutar(Path destinoTemporal);

    record ResultadoDump(long tamanoBytes, String versionMariaDb) {
    }
}
