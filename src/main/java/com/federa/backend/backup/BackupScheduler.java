package com.federa.backend.backup;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupScheduler {

    private final BackupService backupService;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    @Scheduled(
            cron = "${federa.backup.automatico.cron:0 0 2 * * *}",
            zone = "${federa.backup.automatico.zona:America/La_Paz}")
    public void ejecutarDiario() {
        backupService.iniciarAutomatico();
    }
}
