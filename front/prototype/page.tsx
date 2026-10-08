'use client';
import { useEffect, useRef, useState } from 'react';
import {
  Activity,
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  BellRing,
  Check,
  CheckCheck,
  ChevronRight,
  CircleHelp,
  Clock3,
  Cpu,
  ExternalLink,
  Gauge,
  LayoutDashboard,
  ListFilter,
  LoaderCircle,
  Radio,
  RotateCcw,
  Search,
  Server,
  ShieldCheck,
  Sparkles,
  Terminal,
  TriangleAlert,
  Workflow,
  Zap,
} from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ChartContainer } from '@/components/ui/chart';
import {
  initialIncidents,
  PRIMARY_INCIDENT_ID,
  SNAPSHOT,
  ANALYZING_INCIDENT_ID,
  currentValues,
  compareIncidents,
  metrics,
  series,
  date,
  time,
  type Incident,
  type AIStatus,
  type IncidentStatus,
} from '@/lib/mock-data';

const icons = [Clock3, Activity, TriangleAlert, Cpu];
function Badge({
  children,
  tone = 'neutral',
}: {
  children: React.ReactNode;
  tone?: string;
}) {
  return (
    <span className={`badge ${tone}`}>
      <span className="badge-dot" />
      {children}
    </span>
  );
}
function AIBadge({ status }: { status: AIStatus }) {
  return (
    <span className={`ai-state ${status.toLowerCase()}`}>
      {status === 'Completed' ? (
        <Sparkles size={13} />
      ) : status === 'Analyzing' ? (
        <LoaderCircle className="spin" size={13} />
      ) : (
        <Clock3 size={13} />
      )}
      {status}
    </span>
  );
}
function Chart({
  metric,
  small = false,
  incident,
  range = 60,
}: {
  metric: number;
  small?: boolean;
  incident?: Incident;
  range?: number;
}) {
  const m = metrics[metric];
  const data = series(metric, incident, range);
  return (
    <ChartContainer
      className={small ? 'spark-chart' : 'metric-chart'}
      config={{ value: { label: m.name, color: m.color } }}
      aria-label={`${m.name} in ${m.unit}, ${incident ? 'incident context, capped at the mock snapshot' : `last ${range} minutes`}`}
    >
      <AreaChart
        data={data}
        margin={{
          top: 12,
          right: small ? 0 : 16,
          bottom: 0,
          left: small ? 0 : -20,
        }}
      >
        <defs>
          <linearGradient
            id={`fill-${metric}-${small ? 'small' : 'large'}`}
            x1="0"
            y1="0"
            x2="0"
            y2="1"
          >
            <stop offset="0%" stopColor={m.color} stopOpacity={0.18} />
            <stop offset="100%" stopColor={m.color} stopOpacity={0.015} />
          </linearGradient>
        </defs>
        {!small && (
          <CartesianGrid
            vertical={false}
            stroke="#edf0ee"
            strokeDasharray="3 3"
          />
        )}
        <XAxis
          hide={small}
          dataKey="timestamp"
          type="number"
          domain={['dataMin', 'dataMax']}
          tickFormatter={(v) => time(new Date(v).toISOString())}
          tickLine={false}
          axisLine={false}
          tick={{ fontSize: 10, fill: '#8c9491' }}
          minTickGap={40}
        />
        <YAxis
          hide={small}
          domain={[0, m.max]}
          tickLine={false}
          axisLine={false}
          tick={{ fontSize: 10, fill: '#8c9491' }}
          tickCount={4}
        />
        <Tooltip
          labelFormatter={(v) =>
            `${time(new Date(Number(v)).toISOString())} UTC`
          }
          formatter={(v) => [`${String(v)} ${m.unit}`, m.name]}
          contentStyle={{
            border: '1px solid #e3e7e4',
            borderRadius: 8,
            fontSize: 12,
          }}
        />
        {!small && m.threshold !== null && (
          <ReferenceLine
            y={m.threshold}
            stroke="#c4a18e"
            strokeDasharray="4 4"
            label={{
              value: `Threshold ${m.threshold}`,
              position: 'insideTopRight',
              fill: '#9a8a81',
              fontSize: 10,
            }}
          />
        )}
        {!small && incident && (
          <ReferenceLine
            x={new Date(incident.at).getTime()}
            stroke="#9caaa1"
            strokeDasharray="3 3"
            label={{
              value: 'Incident created',
              position: 'insideTopLeft',
              fill: '#75827a',
              fontSize: 10,
            }}
          />
        )}
        <Area
          isAnimationActive={false}
          type="linear"
          dataKey="value"
          stroke={m.color}
          strokeWidth={small ? 1.8 : 2}
          fill={`url(#fill-${metric}-${small ? 'small' : 'large'})`}
        />
      </AreaChart>
    </ChartContainer>
  );
}
function IncidentTable({
  incidents,
  compact = false,
}: {
  incidents: Incident[];
  compact?: boolean;
}) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Incident</th>
            <th>Status</th>
            <th>Severity</th>
            {!compact && <th>Triggered rule</th>}
            <th>Started · UTC</th>
            <th>AI analysis</th>
            <th>
              <span className="sr-only">Open incident</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {incidents.map((i) => (
            <tr
              key={i.id}
              className={
                i.id === PRIMARY_INCIDENT_ID && i.status !== 'Resolved'
                  ? 'primary-incident-row'
                  : undefined
              }
            >
              <td aria-label={`${i.id}: ${i.title}`}>
                <a className="incident-link" href={`#/incidents/${i.id}`}>
                  <span className="incident-id">
                    {i.id}
                    {i.id === PRIMARY_INCIDENT_ID &&
                      i.status !== 'Resolved' && (
                        <span className="primary-label">Primary incident</span>
                      )}
                  </span>
                  <strong>{i.title}</strong>
                </a>
              </td>
              <td>
                <Badge
                  tone={
                    i.status === 'Open'
                      ? 'red'
                      : i.status === 'Acknowledged'
                        ? 'amber'
                        : 'green'
                  }
                >
                  {i.status}
                </Badge>
              </td>
              <td>
                <span className={`severity ${i.severity.toLowerCase()}`}>
                  <span>{i.severity === 'Critical' ? '▰' : '▱'}</span>
                  {i.severity}
                </span>
              </td>
              {!compact && <td className="rule-cell">{i.rule}</td>}
              <td className="time-cell">
                {date(i.at)}
                <span>{time(i.at)}</span>
              </td>
              <td>
                <AIBadge status={i.ai} />
                {i.ai !== 'Completed' && (
                  <small className="ai-pending-hint">
                    Incident available to investigate
                  </small>
                )}
              </td>
              <td>
                <a
                  href={`#/incidents/${i.id}`}
                  className="arrow-link"
                  aria-label={`Open ${i.id}`}
                >
                  <ArrowUpRight size={17} />
                </a>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!incidents.length && (
        <div className="empty-state">
          <Search size={24} />
          <h3>No incidents found</h3>
          <p>Try a different search or reset your filters.</p>
        </div>
      )}
    </div>
  );
}
export default function Home() {
  const [route, setRoute] = useState('/');
  const [analysisStarted, setAnalysisStarted] = useState(false);
  const [incidents, setIncidents] = useState(initialIncidents);
  const [range, setRange] = useState(60);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');
  const [severity, setSeverity] = useState('All');
  const [notice, setNotice] = useState('');
  const [help, setHelp] = useState(false);
  useEffect(() => {
    const sync = () => {
      const nextRoute = window.location.hash.slice(1) || '/';
      setRoute(nextRoute);
      if (nextRoute.startsWith('/incidents')) setAnalysisStarted(true);
      window.scrollTo(0, 0);
    };
    sync();
    window.addEventListener('hashchange', sync);
    return () => window.removeEventListener('hashchange', sync);
  }, []);
  const pending = incidents
    .filter((i) => i.ai === 'Queued' || i.ai === 'Analyzing')
    .map((i) => `${i.id}:${i.ai}`)
    .join(',');
  const analysisTimers = useRef(
    new Map<string, { status: string; timer: number }>(),
  );
  useEffect(() => {
    if (!analysisStarted) return;
    const jobs = new Map(
      pending
        .split(',')
        .filter(Boolean)
        .map((entry) => {
          const [id, status] = entry.split(':');
          return [id, status];
        }),
    );
    for (const [id, job] of analysisTimers.current) {
      if (jobs.get(id) !== job.status) {
        clearTimeout(job.timer);
        analysisTimers.current.delete(id);
      }
    }
    for (const [id, status] of jobs) {
      if (analysisTimers.current.has(id)) continue;
      const timer = window.setTimeout(
        () => {
          setIncidents((current) =>
            current.map((i) =>
              i.id === id && i.ai === status
                ? { ...i, ai: status === 'Queued' ? 'Analyzing' : 'Completed' }
                : i,
            ),
          );
        },
        status === 'Queued'
          ? 2500
          : id === ANALYZING_INCIDENT_ID
            ? 30000
            : 8500,
      );
      analysisTimers.current.set(id, { status, timer });
    }
  }, [pending, analysisStarted]);
  useEffect(() => {
    const timers = analysisTimers.current;
    return () => {
      for (const job of timers.values()) clearTimeout(job.timer);
      timers.clear();
    };
  }, []);
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(''), 4000);
    return () => clearTimeout(timer);
  }, [notice]);
  const active = incidents
    .filter((i) => i.status !== 'Resolved')
    .sort(compareIncidents);
  const primary = active.find((i) => i.id === PRIMARY_INCIDENT_ID);
  const detailId = route.startsWith('/incidents/') ? route.split('/')[2] : null;
  const incident = incidents.find((i) => i.id === detailId);
  const isList = route === '/incidents';
  const isDashboard = route === '/' || route === '/dashboard';
  const changeStatus = (id: string, status: IncidentStatus) => {
    setIncidents((current) =>
      current.map((i) => (i.id === id ? { ...i, status } : i)),
    );
    setNotice(`${id} marked as ${status.toLowerCase()}.`);
  };
  const rerun = (id: string) => {
    setIncidents((current) =>
      current.map((i) => (i.id === id ? { ...i, ai: 'Queued' } : i)),
    );
    setNotice('Mock analysis queued. The incident already exists.');
  };
  const filtered = incidents
    .filter(
      (i) =>
        (filter === 'All' ||
          (filter === 'Active'
            ? i.status !== 'Resolved'
            : i.status === filter)) &&
        (severity === 'All' || i.severity === severity) &&
        `${i.id} ${i.title} ${i.rule}`
          .toLowerCase()
          .includes(query.toLowerCase()),
    )
    .sort(compareIncidents);
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a href="#/" className="brand">
          <span className="brand-icon">
            <Activity size={24} />
          </span>
          <span>
            SRE<span className="brand-light"> Copilot</span>
          </span>
        </a>
        <div className="workspace-label">WORKSPACE</div>
        <div className="project-switch">
          <span className="project-icon">
            <Terminal size={18} />
          </span>
          <div>
            <strong>Acme Engineering</strong>
            <small>Platform team</small>
          </div>
          <span className="workspace-letter">A</span>
        </div>
        <div className="nav-section">OBSERVABILITY</div>
        <nav>
          <a className={isDashboard ? 'selected' : ''} href="#/">
            <LayoutDashboard size={18} />
            Overview{isDashboard && <span className="nav-dot" />}
          </a>
          <a
            className={isList || detailId ? 'selected' : ''}
            href="#/incidents"
          >
            <BellRing size={18} />
            Incidents<span className="nav-count">{active.length}</span>
          </a>
        </nav>
        <div className="sidebar-context">
          <span className="context-line" />
          <div>
            <small>MONITORED PROJECT</small>
            <strong>checkout-api</strong>
            <span>
              <i /> Production
            </span>
          </div>
        </div>
        <div className="sidebar-bottom">
          <div className="mock-card">
            <span>
              <Radio size={14} /> Demo workspace
            </span>
            <p>Mock signals. Real workflow.</p>
          </div>
          <button className="help-button" onClick={() => setHelp(!help)}>
            <CircleHelp size={17} />
            About this demo
            <ExternalLink size={13} />
          </button>
          <div className="user-profile">
            <span className="avatar">JD</span>
            <div>
              <strong>Jamie Davis</strong>
              <small>Platform engineer</small>
            </div>
            <span className="online-dot" />
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <span>Projects</span>
            <ChevronRight size={14} />
            <span className="crumb-project">
              <Server size={14} /> Checkout API
            </span>
            <ChevronRight size={14} />
            <strong>{isDashboard ? 'Overview' : 'Incidents'}</strong>
            {incident && (
              <>
                <ChevronRight size={14} />
                <strong>{incident.id}</strong>
              </>
            )}
          </div>
          <div className="topbar-right">
            <span className="environment">
              <i />
              production
            </span>
            <span className="topbar-divider" />
            <span className="avatar small">JD</span>
          </div>
        </header>
        <main className={incident ? 'incident-details-page' : undefined}>
          {help && (
            <div className="help-panel">
              <div>
                <strong>How this demo works</strong>
                <p>
                  All data is simulated at 26 Sep 2026, 14:32 UTC. Monitoring
                  rules create incidents after sustained threshold breaches. AI
                  only analyzes existing incidents; it does not decide whether
                  to create them. Analysis advances automatically; use “Replay
                  analysis” to repeat it. Changes reset on reload.
                </p>
              </div>
              <Button variant="ghost" onClick={() => setHelp(false)}>
                Close
              </Button>
            </div>
          )}
          {isDashboard ? (
            <>
              <div className="page-heading">
                <div>
                  <div className="eyebrow">PROJECT OVERVIEW</div>
                  <h1>
                    Checkout API{' '}
                    <span className="project-tag">checkout-api</span>
                  </h1>
                  <p>Your service health, at a glance.</p>
                </div>
                <div className="heading-actions">
                  <span className="snapshot">
                    <span className="live-dot" />
                    Mock snapshot · 14:32 UTC
                  </span>
                  <label className="range-select">
                    <Clock3 size={14} />
                    <select
                      aria-label="Chart time range"
                      value={range}
                      onChange={(e) => setRange(Number(e.target.value))}
                    >
                      <option value={60}>Last 1 hour</option>
                      <option value={180}>Last 3 hours</option>
                      <option value={360}>Last 6 hours</option>
                    </select>
                  </label>
                </div>
              </div>
              <div className="health-banner">
                <div className="health-icon">
                  <TriangleAlert size={21} />
                </div>
                <div>
                  <strong>Service is degraded</strong>
                  <p>
                    Latency and error rate are above their configured
                    thresholds.
                  </p>
                </div>
                <a href="#/incidents">
                  {active.length} active incident
                  {active.length !== 1 ? 's' : ''}
                  <ArrowRight size={16} />
                </a>
              </div>
              {primary && (
                <a className="primary-incident-context" href="#/incidents">
                  <span>
                    <span className="incident-id">{primary.id}</span>
                    <strong>{primary.title}</strong>
                    <Badge tone={primary.status === 'Open' ? 'red' : 'amber'}>
                      {primary.status}
                    </Badge>
                    <span className="severity critical">Critical</span>
                  </span>
                  <span>
                    Investigate primary incident <ArrowRight size={14} />
                  </span>
                </a>
              )}
              <div className="section-heading">
                <h2>
                  Golden signals{' '}
                  <span className="muted-label">The four essentials</span>
                </h2>
                <span className="subtle">26 Sep 2026 · UTC</span>
              </div>
              <div className="signal-grid">
                {metrics.map((m, index) => {
                  const Icon = icons[index];
                  const value = currentValues[index];
                  const breached = m.threshold !== null && value > m.threshold;
                  return (
                    <article className="signal-card" key={m.name}>
                      <div className="signal-top">
                        <span>
                          <Icon size={16} />
                          {m.name}
                        </span>
                        <Badge tone={breached ? 'amber' : 'green'}>
                          {breached ? 'Elevated' : 'Healthy'}
                        </Badge>
                      </div>
                      <div className="signal-value">
                        {value.toLocaleString('en-US')}
                        <span>{m.unit}</span>
                      </div>
                      <div className="signal-description">
                        {m.description}
                        <span className={breached ? 'trend bad' : 'trend'}>
                          <ArrowUpRight size={12} />
                          {index < 2
                            ? `+${Math.round((value / m.baseline - 1) * 100)}%`
                            : `+${Number((value - m.baseline).toFixed(2))} pp`}
                        </span>
                      </div>
                      <Chart metric={index} small range={range} />
                      <div className="signal-foot">
                        <span>
                          {m.threshold
                            ? `Threshold: ${m.threshold} ${m.unit}`
                            : 'Baseline: 1.05k req/s'}
                        </span>
                        <span>vs. baseline</span>
                      </div>
                    </article>
                  );
                })}
              </div>
              <section className="panel overview-trends">
                <div className="panel-heading">
                  <div>
                    <h2>Signal trends</h2>
                    <p>A closer look at your service performance</p>
                  </div>
                  <div className="legend">
                    <span className="legend-dash" />
                    Alert threshold
                    <span className="period-chip">{range / 60}h window</span>
                  </div>
                </div>
                <div className="trend-grid">
                  {metrics.map((m, index) => (
                    <div className="trend-item" key={m.name}>
                      <div className="trend-title">
                        <span>
                          <i style={{ background: m.color }} />
                          {m.name}
                        </span>
                        <small>{m.unit}</small>
                      </div>
                      <Chart metric={index} range={range} />
                    </div>
                  ))}
                </div>
              </section>
              <section className="panel">
                <div className="panel-heading">
                  <div className="title-with-count">
                    <h2>Active incidents</h2>
                    <span className="number-pill">{active.length}</span>
                  </div>
                  <a className="text-link" href="#/incidents">
                    View all incidents
                    <ArrowRight size={15} />
                  </a>
                </div>
                {active.length ? (
                  <IncidentTable incidents={active} compact />
                ) : (
                  <div className="empty-state">
                    <CheckCheck size={24} />
                    <h3>No active incidents</h3>
                    <p>
                      Current metric thresholds are still elevated in this fixed
                      demo snapshot.
                    </p>
                  </div>
                )}
              </section>
              <div className="dashboard-note">
                <ShieldCheck size={15} />
                <span>Detected by rules. Investigated with AI.</span>
                <span>
                  Incidents are created only after a sustained threshold breach.
                </span>
              </div>
            </>
          ) : isList ? (
            <>
              <div className="page-heading">
                <div>
                  <div className="eyebrow">INCIDENT MANAGEMENT</div>
                  <h1>Incidents</h1>
                  <p>
                    Understand what happened. Get your service back on track.
                  </p>
                </div>
                <span className="snapshot">
                  <span className="live-dot" />
                  Mock data · 26 Sep 2026
                </span>
              </div>
              <div className="incident-stats">
                <div>
                  <span>Active incidents</span>
                  <strong>
                    {active.length}
                    <BellRing size={20} />
                  </strong>
                  <small>Require your attention</small>
                </div>
                <div>
                  <span>Critical</span>
                  <strong className="text-red">
                    {active.filter((i) => i.severity === 'Critical').length}
                    <TriangleAlert size={20} />
                  </strong>
                  <small>Active critical incidents</small>
                </div>
                <div>
                  <span>Resolved</span>
                  <strong>
                    {incidents.filter((i) => i.status === 'Resolved').length}
                    <CheckCheck size={20} />
                  </strong>
                  <small>Across the demo history</small>
                </div>
                <div>
                  <span>AI analysis complete</span>
                  <strong>
                    {incidents.filter((i) => i.ai === 'Completed').length}
                    <Sparkles size={20} />
                  </strong>
                  <small>Of {incidents.length} total incidents</small>
                </div>
              </div>
              <section className="panel">
                <fieldset
                  className="filter-tabs"
                  aria-label="Incident status filter"
                >
                  {['All', 'Active', 'Acknowledged', 'Resolved'].map((f) => (
                    <button
                      key={f}
                      className={filter === f ? 'active' : ''}
                      onClick={() => setFilter(f)}
                    >
                      {f}
                      <span>
                        {
                          incidents.filter(
                            (i) =>
                              f === 'All' ||
                              (f === 'Active'
                                ? i.status !== 'Resolved'
                                : i.status === f),
                          ).length
                        }
                      </span>
                    </button>
                  ))}
                </fieldset>
                <div className="table-toolbar">
                  <div className="search-field">
                    <Search size={16} />
                    <Input
                      aria-label="Search incidents"
                      placeholder="Search by ID, title, or rule..."
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                    />
                  </div>
                  <label className="range-select">
                    <ListFilter size={15} />
                    <select
                      aria-label="Filter severity"
                      value={severity}
                      onChange={(e) => setSeverity(e.target.value)}
                    >
                      <option value="All">All severities</option>
                      <option>Critical</option>
                      <option>Warning</option>
                    </select>
                  </label>
                  {(query || filter !== 'All' || severity !== 'All') && (
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setQuery('');
                        setFilter('All');
                        setSeverity('All');
                      }}
                    >
                      Reset filters
                    </Button>
                  )}
                </div>
                <IncidentTable incidents={filtered} />
                <div className="table-footer">
                  Showing {filtered.length} of {incidents.length} incidents
                  <span>Active critical first · then newest</span>
                </div>
              </section>
              <div className="dashboard-note">
                <Workflow size={16} />
                <span>Rule-triggered, always.</span>
                <span>
                  AI analysis begins asynchronously after an incident is
                  created.
                </span>
              </div>
            </>
          ) : incident ? (
            <>
              <a className="back-link" href="#/incidents">
                <ArrowLeft size={14} />
                Back to incidents
              </a>
              <div className="page-heading detail-heading">
                <div>
                  <div className="detail-id">
                    <span>{incident.id}</span>
                    <Badge
                      tone={
                        incident.status === 'Resolved'
                          ? 'green'
                          : incident.status === 'Open'
                            ? 'red'
                            : 'amber'
                      }
                    >
                      {incident.status}
                    </Badge>
                    <span
                      className={`severity ${incident.severity.toLowerCase()}`}
                    >
                      {incident.severity}
                    </span>
                  </div>
                  <h1>{incident.title}</h1>
                  <p>
                    Checkout API <span className="dot-separator">·</span>{' '}
                    Production <span className="dot-separator">·</span> Started{' '}
                    {date(incident.at)} 2026 at {time(incident.at)} UTC
                  </p>
                </div>
                <div className="heading-actions">
                  {incident.status === 'Open' && (
                    <Button
                      variant="outline"
                      onClick={() => changeStatus(incident.id, 'Acknowledged')}
                    >
                      <Check size={15} />
                      Acknowledge
                    </Button>
                  )}
                  {incident.status !== 'Resolved' ? (
                    <Button
                      onClick={() => changeStatus(incident.id, 'Resolved')}
                    >
                      <CheckCheck size={15} />
                      Resolve incident
                    </Button>
                  ) : (
                    <Button
                      variant="outline"
                      onClick={() => changeStatus(incident.id, 'Open')}
                    >
                      <RotateCcw size={15} />
                      Reopen incident
                    </Button>
                  )}
                </div>
              </div>
              <div className="rule-banner">
                <span className="rule-icon">
                  <Zap size={20} />
                </span>
                <div>
                  <div className="eyebrow">TRIGGERED MONITORING RULE</div>
                  <strong>{incident.rule}</strong>
                  <p>
                    Threshold exceeded continuously for {incident.duration}.
                    Incident created by the monitoring rule engine.
                  </p>
                </div>
                <span className="rule-engine">
                  <ShieldCheck size={15} />
                  Rule-based detection
                </span>
              </div>
              <div className="details-layout">
                <div className="details-main">
                  <section className="panel">
                    <div className="panel-heading">
                      <div>
                        <h2>Metrics at detection</h2>
                        <p>Snapshot captured at {time(incident.at)} UTC</p>
                      </div>
                      <Gauge size={18} className="muted-icon" />
                    </div>
                    <div className="detection-metrics">
                      {metrics.map((m, index) => (
                        <div key={m.name}>
                          <span>{m.name}</span>
                          <strong
                            className={
                              incident.metric === index ? 'text-red' : ''
                            }
                          >
                            {incident.values[index].toLocaleString('en-US')}
                            <small>{m.unit}</small>
                          </strong>
                          <small>
                            {incident.metric === index
                              ? 'Triggered this incident'
                              : m.description}
                          </small>
                        </div>
                      ))}
                    </div>
                    <div className="panel-heading chart-section-heading">
                      <h2>Metrics around the incident</h2>
                      <span className="subtle">
                        {time(
                          new Date(
                            Date.parse(incident.at) - 15 * 60000,
                          ).toISOString(),
                        )}
                        –
                        {time(
                          new Date(
                            Math.min(
                              Date.parse(incident.at) + 15 * 60000,
                              Date.parse(SNAPSHOT),
                            ),
                          ).toISOString(),
                        )}{' '}
                        UTC
                      </span>
                    </div>
                    <div className="trend-grid detail-trends">
                      {metrics.map((m, index) => (
                        <div className="trend-item" key={m.name}>
                          <div className="trend-title">
                            <span>
                              <i style={{ background: m.color }} />
                              {m.name}
                            </span>
                            <small>{m.unit}</small>
                          </div>
                          <Chart metric={index} incident={incident} />
                        </div>
                      ))}
                    </div>
                  </section>
                  <section className="panel timeline-panel">
                    <div className="panel-heading">
                      <h2>Incident timeline</h2>
                      <Clock3 size={16} className="muted-icon" />
                    </div>
                    <div className="timeline-row">
                      <span className="timeline-dot amber-dot" />
                      <div>
                        <strong>Rule condition detected</strong>
                        <p>
                          Threshold breach began; monitoring waited for the
                          configured duration.
                        </p>
                      </div>
                      <time>
                        {time(
                          new Date(
                            new Date(incident.at).getTime() -
                              parseInt(incident.duration) * 60000,
                          ).toISOString(),
                        )}{' '}
                        UTC
                      </time>
                    </div>
                    <div className="timeline-row">
                      <span className="timeline-dot red-dot" />
                      <div>
                        <strong>Incident created by rule engine</strong>
                        <p>{incident.rule}</p>
                      </div>
                      <time>{time(incident.at)} UTC</time>
                    </div>
                    <div className="timeline-row">
                      <span className="timeline-dot green-dot" />
                      <div>
                        <strong>Context submitted to AI analysis</strong>
                        <p>Analysis is independent of incident creation.</p>
                      </div>
                      <AIBadge status={incident.ai} />
                    </div>
                    <div className="timeline-row">
                      <span className="timeline-dot" />
                      <div>
                        <strong>Current incident status</strong>
                        <p>
                          {incident.status === 'Resolved'
                            ? 'Marked resolved in this demo; metric history is unchanged.'
                            : incident.status === 'Acknowledged'
                              ? 'Acknowledged by the platform team.'
                              : 'Awaiting investigation by the platform team.'}
                        </p>
                      </div>
                      <span className="subtle">{incident.status}</span>
                    </div>
                  </section>
                </div>
                <aside className="analysis-panel">
                  <div className="analysis-heading">
                    <span className="ai-icon">
                      <Sparkles size={20} />
                    </span>
                    <h2>AI Analysis</h2>
                    <AIBadge status={incident.ai} />
                  </div>
                  <p className="analysis-subtitle">
                    Findings from the captured incident context.
                  </p>
                  <div className="ai-boundary">
                    <ShieldCheck size={15} />
                    <p>
                      AI analyzes this existing incident. It does not trigger
                      incidents or execute changes.
                    </p>
                  </div>
                  <div aria-live="polite">
                    {incident.ai !== 'Completed' ? (
                      <div className="analysis-loading">
                        <div className="loading-orbit">
                          <LoaderCircle className="spin" size={30} />
                        </div>
                        <h3>
                          {incident.ai === 'Queued'
                            ? 'Analysis is queued'
                            : 'Connecting the signals'}
                        </h3>
                        <p>
                          {incident.ai === 'Queued'
                            ? 'The incident context is ready for asynchronous analysis.'
                            : 'Reviewing metric trends and the triggered monitoring rule.'}
                        </p>
                        <div className="analysis-steps">
                          <span>
                            <Check size={14} />
                            Incident created by rules
                          </span>
                          <span>
                            <Check size={14} />
                            Metric context collected
                          </span>
                          <span>
                            <LoaderCircle className="spin" size={14} />
                            {incident.ai === 'Queued'
                              ? 'Waiting for analysis'
                              : 'Preparing findings'}
                          </span>
                        </div>
                        <small>
                          Simulated analysis completes automatically within 30
                          seconds. You can investigate this incident now.
                        </small>
                      </div>
                    ) : (
                      <>
                        <div className="analysis-section">
                          <div className="eyebrow">
                            <Search size={13} />
                            SUSPECTED ROOT CAUSE
                          </div>
                          <h3>
                            {incident.metric === 0 && incident.id === 'INC-1042'
                              ? 'Database connection pool exhaustion'
                              : incident.metric === 3
                                ? 'Resource contention'
                                : 'Downstream dependency degradation'}
                          </h3>
                          <p>{incident.cause}</p>
                          <span className="hypothesis-tag">
                            Hypothesis · validate with telemetry
                          </span>
                        </div>
                        <div className="analysis-section">
                          <div className="eyebrow">
                            <Terminal size={13} />
                            RECOMMENDED NEXT STEPS
                          </div>
                          <ol className="recommendations">
                            {incident.recommendations.map((r, index) => (
                              <li key={r}>
                                <span>{index + 1}</span>
                                <p>{r}</p>
                              </li>
                            ))}
                          </ol>
                        </div>
                        <div className="analysis-footer">
                          <span>
                            <CheckCheck size={14} />
                            Analysis complete · mock model
                          </span>
                          <Button
                            variant="outline"
                            onClick={() => rerun(incident.id)}
                          >
                            <RotateCcw size={14} />
                            Replay analysis
                          </Button>
                        </div>
                      </>
                    )}
                  </div>
                  <div className="ai-disclaimer">
                    AI suggestions may be incomplete. Verify findings before
                    making production changes.
                  </div>
                </aside>
              </div>
            </>
          ) : (
            <div className="empty-state">
              <TriangleAlert size={28} />
              <h1>Page not found</h1>
              <p>This incident or page does not exist in the demo.</p>
              <a className="text-link" href="#/">
                Return to overview
                <ArrowRight size={15} />
              </a>
            </div>
          )}
          <footer className="page-footer">
            <span>
              <Activity size={13} />
              SRE Copilot<span className="footer-separator">/</span> A little
              more signal. A little less noise.
            </span>
            <span>
              Local prototype <i /> All data is simulated
            </span>
          </footer>
        </main>
      </div>
      {notice && (
        <output className="toast">
          <CheckCheck size={17} />
          {notice}
        </output>
      )}
    </div>
  );
}
