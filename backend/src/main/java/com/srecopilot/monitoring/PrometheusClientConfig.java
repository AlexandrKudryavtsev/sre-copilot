package com.srecopilot.monitoring;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PrometheusClientConfig {

    @Bean
    HttpClient prometheusHttpClient(MonitoringProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
