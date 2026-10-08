package com.srecopilot.rule;

import com.srecopilot.incident.IncidentRepository;
import com.srecopilot.incident.IncidentStatus;
import com.srecopilot.project.ProjectNotFoundException;
import com.srecopilot.project.ProjectRepository;
import com.srecopilot.project.ProjectService;
import com.srecopilot.rule.RuleDtos.RuleRequest;
import com.srecopilot.rule.RuleDtos.RuleResponse;
import com.srecopilot.user.CurrentUserProvider;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleService {

    private final MonitoringRuleRepository rules;
    private final MonitoringRuleStateRepository states;
    private final IncidentRepository incidents;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public RuleService(MonitoringRuleRepository rules, MonitoringRuleStateRepository states,
            IncidentRepository incidents, ProjectRepository projects, ProjectService projectService,
            CurrentUserProvider currentUser, Clock clock) {
        this.rules = rules;
        this.states = states;
        this.incidents = incidents;
        this.projects = projects;
        this.projectService = projectService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<RuleResponse> list(long projectId) {
        projectService.get(projectId);
        var projectRules = rules.findAllByProjectIdOrderByIdAsc(projectId);
        Map<Long, MonitoringRuleState> runtime = states.findAllById(projectRules.stream().map(MonitoringRule::getId).toList())
                .stream().collect(Collectors.toMap(MonitoringRuleState::getRuleId, Function.identity()));
        return projectRules.stream().map(rule -> rule.toResponse(runtime.get(rule.getId()))).toList();
    }

    @Transactional
    public RuleResponse create(long projectId, RuleRequest request) {
        lockOwnedProject(projectId);
        var rule = rules.save(new MonitoringRule(projectId, request, clock.instant()));
        var state = states.save(new MonitoringRuleState(rule.getId()));
        return rule.toResponse(state);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RuleResponse get(long projectId, long ruleId) {
        projectService.get(projectId);
        return requireRule(projectId, ruleId).toResponse(states.findById(ruleId).orElseThrow());
    }

    @Transactional
    public RuleResponse update(long projectId, long ruleId, RuleRequest request) {
        lockOwnedProject(projectId);
        var rule = requireRule(projectId, ruleId);
        var state = states.findForUpdate(ruleId).orElseThrow();
        rule.update(request, clock.instant());
        state.resetProgress();
        return rule.toResponse(state);
    }

    @Transactional
    public void delete(long projectId, long ruleId) {
        lockOwnedProject(projectId);
        var rule = requireRule(projectId, ruleId);
        states.findForUpdate(ruleId).orElseThrow();
        if (incidents.findByRuleIdAndStatus(ruleId, IncidentStatus.OPEN).isPresent()) {
            throw new RuleHasOpenIncidentException(ruleId);
        }
        // Database FKs cascade the runtime state and retain historical incidents with ruleId=null.
        rules.delete(rule);
    }

    @Transactional
    public RuleResponse setEnabled(long projectId, long ruleId, boolean enabled) {
        lockOwnedProject(projectId);
        var rule = requireRule(projectId, ruleId);
        var state = states.findForUpdate(ruleId).orElseThrow();
        if (rule.isEnabled() != enabled) {
            rule.setEnabled(enabled, clock.instant());
            state.resetProgress();
        }
        return rule.toResponse(state);
    }

    private MonitoringRule requireRule(long projectId, long ruleId) {
        return rules.findByIdAndProjectId(ruleId, projectId).orElseThrow(() -> new RuleNotFoundException(ruleId));
    }

    private void lockOwnedProject(long projectId) {
        projects.findOwnedByIdForUpdate(projectId, currentUser.userId())
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }
}
