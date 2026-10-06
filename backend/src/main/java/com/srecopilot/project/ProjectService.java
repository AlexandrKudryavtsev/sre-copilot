package com.srecopilot.project;

import com.srecopilot.common.exception.FeatureNotImplementedException;
import com.srecopilot.project.ProjectDtos.ConnectionTestResponse;
import com.srecopilot.project.ProjectDtos.ProjectRequest;
import com.srecopilot.project.ProjectDtos.ProjectResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {

    public List<ProjectResponse> list() {
        throw new FeatureNotImplementedException("List projects");
    }

    public ProjectResponse create(ProjectRequest request) {
        throw new FeatureNotImplementedException("Create project");
    }

    public ProjectResponse get(long id) {
        throw new FeatureNotImplementedException("Get project");
    }

    public ProjectResponse update(long id, ProjectRequest request) {
        throw new FeatureNotImplementedException("Update project");
    }

    public void delete(long id) {
        throw new FeatureNotImplementedException("Delete project");
    }

    public ConnectionTestResponse testConnection(long id) {
        throw new FeatureNotImplementedException("Test connection");
    }

    public ProjectResponse setMonitoringEnabled(long id, boolean enabled) {
        throw new FeatureNotImplementedException("Set monitoring enabled");
    }
}
