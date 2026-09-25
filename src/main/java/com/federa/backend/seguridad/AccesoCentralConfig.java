package com.federa.backend.seguridad;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AccesoCentralConfig implements WebMvcConfigurer {
    private final AccesoCentralInterceptor interceptor;

    public AccesoCentralConfig(AccesoCentralInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        // Registrar el bean administrado conserva la transacción de preHandle
        // al resolver relaciones LAZY, incluso con open-in-view desactivado.
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
    }
}
