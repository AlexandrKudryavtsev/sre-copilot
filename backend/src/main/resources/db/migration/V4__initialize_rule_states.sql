-- Ensure pre-existing rules have a runtime row before the Rule Engine starts.
INSERT INTO monitoring_rule_states(rule_id)
SELECT id FROM monitoring_rules
ON CONFLICT (rule_id) DO NOTHING;

-- Synchronize runtime pointers with any pre-existing open incidents.
UPDATE monitoring_rule_states s
SET state = 'INCIDENT_OPEN', active_incident_id = i.id
FROM incidents i
WHERE i.rule_id = s.rule_id AND i.status = 'OPEN';
