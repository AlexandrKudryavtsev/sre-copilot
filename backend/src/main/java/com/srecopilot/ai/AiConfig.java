package com.srecopilot.ai;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
@EnableScheduling
public class AiConfig {
    @Bean HttpClient openAiHttpClient(AiProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean(name = "taskScheduler") ThreadPoolTaskScheduler monitoringTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("monitoring-");
        scheduler.setPoolSize(1);
        return scheduler;
    }

    @Bean ThreadPoolTaskScheduler aiAnalysisScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("ai-analysis-");
        scheduler.setPoolSize(1);
        return scheduler;
    }
}
