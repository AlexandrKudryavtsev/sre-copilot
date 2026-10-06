package com.srecopilot.monitoring;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MonitoringConfig {

    @Bean
    Clock monitoringClock() {
        return Clock.systemUTC();
    }
}
