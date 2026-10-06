package com.srecopilot.rule;

import com.srecopilot.common.exception.FeatureNotImplementedException;
import com.srecopilot.rule.RuleDtos.RuleRequest;
import com.srecopilot.rule.RuleDtos.RuleResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RuleService {

    public List<RuleResponse> list(long projectId) {
        throw new FeatureNotImplementedException("List monitoring rules");
    }

    public RuleResponse create(long projectId, RuleRequest request) {
        throw new FeatureNotImplementedException("Create monitoring rule");
    }

    public RuleResponse get(long projectId, long ruleId) {
        throw new FeatureNotImplementedException("Get monitoring rule");
    }

    public RuleResponse update(long projectId, long ruleId, RuleRequest request) {
        throw new FeatureNotImplementedException("Update monitoring rule");
    }

    public void delete(long projectId, long ruleId) {
        throw new FeatureNotImplementedException("Delete monitoring rule");
    }

    public RuleResponse setEnabled(long projectId, long ruleId, boolean enabled) {
        throw new FeatureNotImplementedException("Set monitoring rule enabled");
    }
}
