package com.srecopilot.incident;

import com.srecopilot.common.exception.FeatureNotImplementedException;
import com.srecopilot.incident.IncidentDtos.IncidentResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    public List<IncidentResponse> list(long projectId, IncidentStatus status, Severity severity) {
        throw new FeatureNotImplementedException("List incidents");
    }

    public IncidentResponse get(long incidentId) {
        throw new FeatureNotImplementedException("Get incident");
    }
}
