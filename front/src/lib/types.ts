export type EndpointType = 'PROMETHEUS' | 'ACTUATOR';
export type ConnectionStatus = 'UNKNOWN' | 'CONNECTED' | 'UNAVAILABLE';
export type Metric = 'LATENCY' | 'TRAFFIC' | 'ERRORS' | 'SATURATION';
export type Operator =
  | 'GREATER_THAN'
  | 'GREATER_OR_EQUAL'
  | 'LESS_THAN'
  | 'LESS_OR_EQUAL';
export interface ProjectRequest {
  name: string;
  description: string | null;
  endpointUrl: string;
  endpointType: EndpointType;
}
export interface Project extends ProjectRequest {
  id: number;
  userId: number;
  monitoringEnabled: boolean;
  connectionStatus: ConnectionStatus;
  createdAt: string;
  updatedAt: string;
}
export interface Snapshot {
  id: number;
  projectId: number;
  timestamp: string;
  latency: number;
  traffic: number;
  errors: number;
  saturation: number;
}
export type Severity = 'WARNING' | 'CRITICAL';
export interface RuleRequest {
  name: string;
  metric: Metric;
  operator: Operator;
  threshold: number;
  enabled: boolean;
  checkIntervalSeconds: number;
  consecutiveViolations: number;
  severity: Severity;
}
export interface Rule extends RuleRequest {
  id: number;
  projectId: number;
  createdAt: string;
  updatedAt: string;
  runtimeState: {
    state: 'HEALTHY' | 'VIOLATING' | 'INCIDENT_OPEN';
    consecutiveViolations: number;
    consecutiveRecoveries: number;
    activeIncidentId: number | null;
    lastCheckedAt: string | null;
    lastViolationAt: string | null;
  };
}
export interface Incident {
  id: number;
  projectId: number;
  ruleId: number | null;
  title: string;
  status: 'OPEN' | 'RESOLVED';
  severity: Severity;
  startedAt: string;
  resolvedAt: string | null;
  triggerMetric: Metric;
  triggerValue: number;
  ruleMetric: Metric;
  ruleOperator: Operator;
  ruleThreshold: number;
  requiredConsecutiveViolations: number;
  latencyAtDetection: number;
  trafficAtDetection: number;
  errorsAtDetection: number;
  saturationAtDetection: number;
}
