package com.srecopilot.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI monitoringCoreOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SRE Copilot — Monitoring Core")
                .version("0.0.1")
                .description("Projects, metrics, monitoring rules, automatic incident detection/recovery and incident reads are implemented. "
                        + "Project editing/deletion return 501 until implemented."));
    }
}
