package com.srecopilot.incident;

import com.srecopilot.incident.IncidentDtos.IncidentResponse;
import com.srecopilot.project.ProjectService;
import com.srecopilot.user.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private final IncidentRepository incidents;
    private final ProjectService projects;
    private final CurrentUserProvider currentUser;

    public IncidentService(IncidentRepository incidents, ProjectService projects, CurrentUserProvider currentUser) {
        this.incidents = incidents;
        this.projects = projects;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> list(long projectId, IncidentStatus status, Severity severity) {
        projects.get(projectId);
        return incidents.findForProject(projectId, status, severity).stream().map(Incident::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(long incidentId) {
        return incidents.findOwnedById(incidentId, currentUser.userId())
                .orElseThrow(() -> new IncidentNotFoundException(incidentId)).toResponse();
    }
}
