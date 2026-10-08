package com.srecopilot.ai;

import com.srecopilot.ai.AiDtos.AnalysisResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents/{incidentId}/ai-analysis")
public class AiAnalysisController {
    private final AiAnalysisService service;
    public AiAnalysisController(AiAnalysisService service) { this.service = service; }
    @GetMapping public AnalysisResponse get(@PathVariable long incidentId) { return service.get(incidentId); }
    @PostMapping("/retry") public ResponseEntity<AnalysisResponse> retry(@PathVariable long incidentId) {
        return ResponseEntity.accepted().body(service.retry(incidentId));
    }
}
