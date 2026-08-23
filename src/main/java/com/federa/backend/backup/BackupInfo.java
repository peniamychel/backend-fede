package com.federa.backend.backup;

import java.time.Instant;

public record BackupInfo(
        String id,
        TipoBackup tipo,
        EstadoBackup estado,
        Instant iniciadoEn,
        Instant finalizadoEn,
        String archivo,
        Long tamanoBytes,
        String sha256,
        String baseDatos,
        String solicitadoPor,
        String versionMariaDb,
        String error
) {
}
