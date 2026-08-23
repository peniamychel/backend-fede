package com.federa.backend.integracion.sie;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SiePropiedades.class)
public class SieConfiguracion {
}
