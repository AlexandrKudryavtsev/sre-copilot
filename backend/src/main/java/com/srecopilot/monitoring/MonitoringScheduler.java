package com.srecopilot.monitoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "monitoring.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringScheduler {

    private final MetricCollector collector;

    public MonitoringScheduler(MetricCollector collector) {
        this.collector = collector;
    }

    @Scheduled(fixedDelayString = "${monitoring.collection-interval}",
            initialDelayString = "${monitoring.collection-interval}")
    public void collectMetrics() {
        collector.collect();
    }
}
