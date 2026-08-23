package com.federa.backend.backup;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(BackupPropiedades.class)
public class BackupConfiguracion {

    @Bean(name = "ejecutorBackups")
    Executor ejecutorBackups() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(1);
        ejecutor.setMaxPoolSize(1);
        ejecutor.setQueueCapacity(0);
        ejecutor.setThreadNamePrefix("backup-mariadb-");
        ejecutor.setWaitForTasksToCompleteOnShutdown(false);
        ejecutor.initialize();
        return ejecutor;
    }
}
