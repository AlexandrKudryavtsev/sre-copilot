package com.srecopilot.ai;

import com.srecopilot.ai.AiDtos.AnalysisResponse;
import com.srecopilot.incident.IncidentService;
import com.srecopilot.project.ProjectRepository;
import com.srecopilot.project.ProjectNotFoundException;
import com.srecopilot.user.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiAnalysisService {
    private final IncidentService incidents;
    private final AiAnalysisRepository analyses;
    private final ProjectRepository projects;
    private final CurrentUserProvider currentUser;
    public AiAnalysisService(IncidentService incidents, AiAnalysisRepository analyses,
            ProjectRepository projects, CurrentUserProvider currentUser) {
        this.incidents = incidents; this.analyses = analyses; this.projects = projects; this.currentUser = currentUser;
    }
    @Transactional(readOnly = true)
    public AnalysisResponse get(long incidentId) {
        incidents.get(incidentId); // Same owner checks as incident details; no cross-project data exposure.
        return analyses.find(incidentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI analysis not found."));
    }
    @Transactional
    public AnalysisResponse retry(long incidentId) {
        var incident = incidents.get(incidentId);
        // Lock in the same order as monitoring/deletion; prevents retry racing with project deletion.
        projects.findOwnedByIdForUpdate(incident.projectId(), currentUser.userId())
                .orElseThrow(() -> new ProjectNotFoundException(incident.projectId()));
        analyses.retry(incidentId);
        return analyses.find(incidentId).orElseThrow();
    }
}
