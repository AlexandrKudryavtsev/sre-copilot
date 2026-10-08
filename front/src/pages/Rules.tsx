import { useCallback, useState } from 'react';
import {
  ArrowLeft,
  Pencil,
  Plus,
  SlidersHorizontal,
  Trash2,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { api } from '../lib/api';
import type { Rule, RuleRequest } from '../lib/types';
import { condition, dateTime, operators, signals } from '../lib/signals';
import { ruleNumbers } from '../lib/rules';
import { useResource } from '../hooks/useResource';
import { Badge, ErrorNotice, Loading } from '../components/Feedback';

export function RulesPage({ projectId }: { projectId: number }) {
  const loadProject = useCallback(
    (signal: AbortSignal) => api.project(projectId, signal),
    [projectId],
  );
  const loadRules = useCallback(
    (signal: AbortSignal) => api.rules(projectId, signal),
    [projectId],
  );
  const project = useResource(loadProject, 15_000);
  const rules = useResource(loadRules, 15_000);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('all');
  const visible = rules.data?.filter(
    (rule) =>
      `${rule.name} ${rule.metric}`
        .toLowerCase()
        .includes(query.toLowerCase()) &&
      (filter === 'all' || rule.enabled === (filter === 'enabled')),
  );
  if (project.loading) return <Loading text="Loading project…" />;
  if (!project.data)
    return (
      <ErrorNotice
        message={project.error || 'Project not found.'}
        retry={project.reload}
      />
    );
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">MONITORING CONFIGURATION</div>
          <h1>Rules</h1>
          <p>
            Turn sustained signal changes into incidents for {project.data.name}
            .
          </p>
        </div>
        <a className="action-link" href={`#/projects/${projectId}/rules/new`}>
          <Plus size={16} />
          New rule
        </a>
      </div>
      {project.error && (
        <ErrorNotice message={project.error} retry={project.reload} />
      )}
      {rules.error && (
        <ErrorNotice message={rules.error} retry={rules.reload} />
      )}
      {!project.data.monitoringEnabled && (
        <output className="connection-message">
          Project monitoring is paused. Rules will be evaluated after you enable
          monitoring on <a href={`#/projects/${projectId}`}>Overview</a>.
        </output>
      )}
      <p className="rules-explanation">
        An incident opens after the configured number of consecutive violations.
        Checks use new measurements and run no more often than the rule
        interval.
      </p>
      {rules.loading ? (
        <Loading text="Loading rules…" />
      ) : (
        rules.data &&
        (!rules.data.length ? (
          <section className="panel empty-state">
            <SlidersHorizontal size={32} />
            <h3>Create your first rule</h3>
            <p>
              Choose a Golden Signal, a threshold and how many checks must
              violate it.
            </p>
            <a
              className="action-link"
              href={`#/projects/${projectId}/rules/new`}
            >
              <Plus size={16} />
              Create rule
            </a>
          </section>
        ) : (
          <>
            <div className="incident-filters rules-filters">
              <Input
                aria-label="Search rules"
                placeholder="Search by name or signal…"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
              <select
                aria-label="Rule status"
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
              >
                <option value="all">All rules</option>
                <option value="enabled">Enabled</option>
                <option value="disabled">Disabled</option>
              </select>
              <Button variant="outline" onClick={rules.reload}>
                Refresh
              </Button>
            </div>
            <div className="rule-list">
              {visible?.length ? (
                visible.map((rule) => (
                  <RuleCard
                    key={rule.id}
                    rule={rule}
                    projectId={projectId}
                    onChanged={rules.reload}
                  />
                ))
              ) : (
                <section className="panel empty-state">
                  <h3>No matching rules</h3>
                  <p>Try another search or filter.</p>
                </section>
              )}
            </div>
          </>
        ))
      )}
    </>
  );
}

function RuleCard({
  rule,
  projectId,
  onChanged,
}: {
  rule: Rule;
  projectId: number;
  onChanged: () => void;
}) {
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const state = rule.runtimeState;
  const incidentId = state.activeIncidentId;
  async function perform(action: 'toggle' | 'delete') {
    setBusy(action);
    setError('');
    try {
      if (action === 'toggle')
        await api.ruleEnabled(projectId, rule.id, !rule.enabled);
      else {
        await api.deleteRule(projectId, rule.id);
        setConfirmDelete(false);
      }
      onChanged();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not update rule.',
      );
      onChanged();
    } finally {
      setBusy('');
    }
  }
  return (
    <article className="panel rule-card">
      <div className="rule-heading">
        <div>
          <h2>{rule.name}</h2>
          <p className="rule-condition">
            {condition(
              rule.metric,
              rule.operator,
              rule.threshold,
              rule.consecutiveViolations,
            )}
          </p>
        </div>
        <div className="rule-badges">
          <Badge tone={rule.enabled ? 'green' : 'neutral'}>
            {rule.enabled ? 'Enabled' : 'Disabled'}
          </Badge>
          <Badge tone={rule.severity === 'CRITICAL' ? 'red' : 'amber'}>
            {rule.severity === 'CRITICAL' ? 'Critical' : 'Warning'}
          </Badge>
        </div>
      </div>
      <div className="rule-metadata">
        <span>Check interval: {rule.checkIntervalSeconds}s</span>
        <span>
          Last checked:{' '}
          {state.lastCheckedAt
            ? dateTime(state.lastCheckedAt)
            : 'Not checked yet'}
        </span>
      </div>
      <div className="rule-runtime">
        {incidentId ? (
          <a className="rule-incident" href={`#/incidents/${incidentId}`}>
            Open incident INC-{incidentId} · {state.consecutiveRecoveries}{' '}
            consecutive recovery checks
          </a>
        ) : !rule.enabled ? (
          <span>Evaluation paused</span>
        ) : !state.lastCheckedAt ? (
          <span>Waiting for a measurement</span>
        ) : state.state === 'VIOLATING' ? (
          <span className="rule-violating">
            {state.consecutiveViolations} / {rule.consecutiveViolations}{' '}
            consecutive violations
          </span>
        ) : (
          <span>No current violation</span>
        )}
        {!rule.enabled && incidentId && (
          <p className="field-hint">
            Recovery checks are paused while the rule is disabled. Enable it to
            resume automatic recovery.
          </p>
        )}
      </div>
      <div className="rule-actions">
        <Button
          variant="outline"
          disabled={!!busy}
          onClick={() => void perform('toggle')}
        >
          {busy === 'toggle'
            ? 'Updating…'
            : rule.enabled
              ? 'Disable rule'
              : 'Enable rule'}
        </Button>
        <a
          className={`secondary-link ${busy ? 'link-disabled' : ''}`}
          aria-disabled={!!busy}
          href={`#/projects/${projectId}/rules/${rule.id}/edit`}
        >
          <Pencil size={14} />
          Edit rule
        </a>
        <Button
          variant="ghost"
          aria-label={`Delete rule ${rule.name}`}
          disabled={!!busy || !!incidentId}
          onClick={() => setConfirmDelete(true)}
        >
          <Trash2 size={15} />
          Delete
        </Button>
      </div>
      {!!incidentId && (
        <p className="field-hint">
          Deletion is available after the open incident resolves automatically.
        </p>
      )}
      {error && <ErrorNotice message={error} />}
      {confirmDelete && (
        <section
          className="delete-confirm"
          role="alertdialog"
          aria-labelledby={`delete-rule-${rule.id}`}
        >
          <h2 id={`delete-rule-${rule.id}`}>Delete {rule.name}?</h2>
          <p>
            The rule will stop being evaluated. Resolved incident history stays
            available. A rule with an open incident cannot be deleted.
          </p>
          <div>
            <Button
              variant="outline"
              disabled={!!busy}
              onClick={() => setConfirmDelete(false)}
            >
              Keep rule
            </Button>
            <Button
              variant="destructive"
              disabled={!!busy || !!incidentId}
              onClick={() => void perform('delete')}
            >
              {busy === 'delete' ? 'Deleting…' : 'Delete rule permanently'}
            </Button>
          </div>
        </section>
      )}
    </article>
  );
}

export function RuleEditor({
  projectId,
  ruleId,
}: {
  projectId: number;
  ruleId?: number;
}) {
  const loadProject = useCallback(
    (signal: AbortSignal) => api.project(projectId, signal),
    [projectId],
  );
  const loadRule = useCallback(
    (signal: AbortSignal) =>
      ruleId ? api.rule(projectId, ruleId, signal) : Promise.resolve(null),
    [projectId, ruleId],
  );
  const project = useResource(loadProject);
  const rule = useResource(loadRule);
  if (project.loading || rule.loading) return <Loading text="Loading rule…" />;
  if (project.error || rule.error)
    return (
      <ErrorNotice
        message={project.error || rule.error!}
        retry={() => {
          project.reload();
          rule.reload();
        }}
      />
    );
  if (!project.data) return <ErrorNotice message="Project not found." />;
  return (
    <RuleForm
      initial={rule.data ?? null}
      projectId={projectId}
      projectName={project.data.name}
    />
  );
}

function RuleForm({
  initial,
  projectId,
  projectName,
}: {
  initial: Rule | null;
  projectId: number;
  projectName: string;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [metric, setMetric] = useState<RuleRequest['metric']>(
    initial?.metric ?? 'LATENCY',
  );
  const [operator, setOperator] = useState<RuleRequest['operator']>(
    initial?.operator ?? 'GREATER_OR_EQUAL',
  );
  const [threshold, setThreshold] = useState(String(initial?.threshold ?? 800));
  const [severity, setSeverity] = useState<RuleRequest['severity']>(
    initial?.severity ?? 'WARNING',
  );
  const [interval, setInterval] = useState(
    String(initial?.checkIntervalSeconds ?? 30),
  );
  const [checks, setChecks] = useState(
    String(initial?.consecutiveViolations ?? 3),
  );
  const [enabled, setEnabled] = useState(initial?.enabled ?? true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const signal = signals.find((item) => item.metric === metric)!;
  let numbers: ReturnType<typeof ruleNumbers> | undefined;
  try {
    numbers = ruleNumbers(threshold, interval, checks);
  } catch {
    /* Partial input has no preview. */
  }
  async function submit(event: React.SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const value: RuleRequest = {
        name: name.trim(),
        metric,
        operator,
        severity,
        enabled,
        ...ruleNumbers(threshold, interval, checks),
      };
      if (!value.name) throw new Error('Rule name is required.');
      if (initial) await api.updateRule(projectId, initial.id, value);
      else await api.createRule(projectId, value);
      window.location.hash = `/projects/${projectId}/rules`;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not save rule.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <a className="back-link" href={`#/projects/${projectId}/rules`}>
        <ArrowLeft size={14} />
        Back to rules
      </a>
      <div className="page-heading">
        <div>
          <div className="eyebrow">MONITORING CONFIGURATION</div>
          <h1>{initial ? 'Edit rule' : 'New rule'}</h1>
          <p>Configure incident detection for {projectName}.</p>
        </div>
      </div>
      <form className="panel project-form rule-form" onSubmit={submit}>
        {error && <ErrorNotice message={error} />}
        <fieldset disabled={busy}>
          <label htmlFor="rule-name">
            Rule name <span>*</span>
          </label>
          <Input
            id="rule-name"
            required
            maxLength={255}
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="High latency"
          />
          <div className="form-section">
            <SlidersHorizontal size={17} />
            <strong>Trigger condition</strong>
          </div>
          <label htmlFor="rule-metric">Golden Signal</label>
          <select
            id="rule-metric"
            value={metric}
            onChange={(event) =>
              setMetric(event.target.value as RuleRequest['metric'])
            }
          >
            {signals.map((item) => (
              <option key={item.metric} value={item.metric}>
                {item.name} ({item.unit})
              </option>
            ))}
          </select>
          <p className="field-hint">
            {signal.description}.{' '}
            {metric === 'LATENCY' || metric === 'ERRORS'
              ? 'No-traffic measurements do not count as violations or recovery.'
              : ''}
          </p>
          <div className="form-grid">
            <div>
              <label htmlFor="rule-operator">Comparison</label>
              <select
                id="rule-operator"
                value={operator}
                onChange={(event) =>
                  setOperator(event.target.value as RuleRequest['operator'])
                }
              >
                <option value="GREATER_THAN">
                  {operators.GREATER_THAN} Greater than
                </option>
                <option value="GREATER_OR_EQUAL">
                  {operators.GREATER_OR_EQUAL} Greater than or equal
                </option>
                <option value="LESS_THAN">
                  {operators.LESS_THAN} Less than
                </option>
                <option value="LESS_OR_EQUAL">
                  {operators.LESS_OR_EQUAL} Less than or equal
                </option>
              </select>
            </div>
            <div>
              <label htmlFor="rule-threshold">
                Threshold ({signal.unit}) <span>*</span>
              </label>
              <Input
                id="rule-threshold"
                type="number"
                required
                min={0}
                step="any"
                value={threshold}
                onChange={(event) => setThreshold(event.target.value)}
              />
            </div>
          </div>
          <label htmlFor="rule-severity">Incident severity</label>
          <select
            id="rule-severity"
            value={severity}
            onChange={(event) =>
              setSeverity(event.target.value as RuleRequest['severity'])
            }
          >
            <option value="WARNING">Warning</option>
            <option value="CRITICAL">Critical</option>
          </select>
          <div className="form-grid">
            <div>
              <label htmlFor="rule-interval">
                Check interval (seconds) <span>*</span>
              </label>
              <Input
                id="rule-interval"
                type="number"
                required
                min={1}
                max={2147483647}
                step={1}
                value={interval}
                onChange={(event) => setInterval(event.target.value)}
              />
            </div>
            <div>
              <label htmlFor="rule-checks">
                Consecutive violations <span>*</span>
              </label>
              <Input
                id="rule-checks"
                type="number"
                required
                min={1}
                max={2147483647}
                step={1}
                value={checks}
                onChange={(event) => setChecks(event.target.value)}
              />
            </div>
          </div>
          <p className="field-hint">
            The interval is the minimum time between evaluations of new
            snapshots. Actual checks also depend on the project collection
            interval. Consecutive violations are a count of checks.
          </p>
          <label className="checkbox-label" htmlFor="rule-enabled">
            <input
              id="rule-enabled"
              type="checkbox"
              checked={enabled}
              onChange={(event) => setEnabled(event.target.checked)}
            />
            Enable rule
          </label>
          {numbers && (
            <output className="rule-preview">
              <strong>
                {condition(
                  metric,
                  operator,
                  numbers.threshold,
                  numbers.consecutiveViolations,
                )}
              </strong>
              <span>
                {enabled
                  ? 'Evaluated while project monitoring is active.'
                  : 'Saved disabled. Enable it later from the rule list.'}
              </span>
            </output>
          )}
          {initial && (
            <p className="field-hint">
              Saving resets consecutive-check progress. Existing incidents
              retain their original condition. Disabling a rule pauses recovery
              checks for its open incident.
            </p>
          )}
          <div className="form-actions">
            <a href={`#/projects/${projectId}/rules`}>Cancel</a>
            <Button type="submit" disabled={busy || !name.trim() || !numbers}>
              {busy ? 'Saving…' : initial ? 'Save changes' : 'Create rule'}
            </Button>
          </div>
        </fieldset>
      </form>
    </>
  );
}
