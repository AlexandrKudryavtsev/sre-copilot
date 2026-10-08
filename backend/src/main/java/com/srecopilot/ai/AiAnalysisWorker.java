package com.srecopilot.ai;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisWorker {
    private static final Logger log = LoggerFactory.getLogger(AiAnalysisWorker.class);
    private final AiAnalysisRepository analyses;
    private final AiContextService contexts;
    private final OpenAiClient openAi;
    private final AiProperties properties;
    private final Clock clock;
    public AiAnalysisWorker(AiAnalysisRepository analyses, AiContextService contexts, OpenAiClient openAi,
            AiProperties properties, Clock clock) {
        this.analyses = analyses; this.contexts = contexts; this.openAi = openAi; this.properties = properties; this.clock = clock;
    }
    public void processNext() {
        var now = clock.instant();
        var job = analyses.claim(now, now.plus(properties.requestTimeout()).plusSeconds(60));
        if (job.isEmpty()) { return; }
        try {
            var result = openAi.analyze(contexts.build(job.get().incidentId()));
            analyses.complete(job.get(), result, clock.instant());
        } catch (RuntimeException exception) {
            String message = exception instanceof AiAnalysisException ? exception.getMessage() : "AI analysis failed.";
            analyses.fail(job.get(), message, clock.instant());
            // Do not log exception objects: HTTP clients and parsers may include sensitive payloads.
            log.warn("AI analysis failed for incident {}", job.get().incidentId());
        }
    }

    @Component
    @ConditionalOnProperty(name = "ai.scheduler-enabled", havingValue = "true", matchIfMissing = true)
    public static class Scheduler {
        private final AiAnalysisWorker worker;
        public Scheduler(AiAnalysisWorker worker) { this.worker = worker; }
        @Scheduled(scheduler = "aiAnalysisScheduler", fixedDelayString = "${ai.poll-interval}",
                initialDelayString = "${ai.poll-interval}")
        public void poll() {
            try { worker.processNext(); }
            catch (RuntimeException exception) { log.warn("AI analysis queue processing failed; will retry on the next poll"); }
        }
    }
}
