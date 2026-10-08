import { useCallback, useState } from 'react';
import {
  ArrowLeft,
  ArrowUpRight,
  FolderPlus,
  Plus,
  Server,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { api } from '../lib/api';
import type { Project, ProjectRequest } from '../lib/types';
import { dateTime } from '../lib/signals';
import { useResource } from '../hooks/useResource';
import { Badge, ErrorNotice, Loading } from '../components/Feedback';

export function ProjectsPage({
  projects,
  loading,
  error,
  reload,
}: {
  projects?: Project[];
  loading: boolean;
  error?: string;
  reload: () => void;
}) {
  const [query, setQuery] = useState('');
  const visible = projects?.filter((project) =>
    `${project.name} ${project.description ?? ''} ${project.endpointUrl}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">SERVICE MONITORING</div>
          <h1>Projects</h1>
          <p>Your services, their signals and their incidents.</p>
        </div>
        <a href="#/projects/new" className="action-link">
          <Plus size={16} />
          New project
        </a>
      </div>
      {error && <ErrorNotice message={error} retry={reload} />}
      {loading ? (
        <Loading text="Loading projects…" />
      ) : (
        projects && (
          <>
            <div className="project-summary">
              <span>
                <strong>{projects.length}</strong> projects
              </span>
              <span>
                <strong>
                  {
                    projects.filter((project) => project.monitoringEnabled)
                      .length
                  }
                </strong>{' '}
                monitoring active
              </span>
              <span>
                <strong>
                  {
                    projects.filter(
                      (project) => project.connectionStatus === 'UNAVAILABLE',
                    ).length
                  }
                </strong>{' '}
                unavailable sources
              </span>
            </div>
            {!!projects.length && (
              <div className="project-search">
                <Input
                  aria-label="Search projects"
                  placeholder="Search by name or endpoint…"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                />
              </div>
            )}
            {!projects.length ? (
              <section className="panel empty-state">
                <FolderPlus size={32} />
                <h3>Add your first service</h3>
                <p>
                  Create a project, check its metrics endpoint and enable
                  monitoring.
                </p>
                <a className="action-link" href="#/projects/new">
                  <Plus size={16} />
                  Create project
                </a>
              </section>
            ) : !visible?.length ? (
              <section className="panel empty-state">
                <h3>No matching projects</h3>
                <p>Try another name or endpoint.</p>
              </section>
            ) : (
              <div className="project-grid">
                {visible.map((project) => (
                  <a
                    key={project.id}
                    href={`#/projects/${project.id}`}
                    className="panel project-card"
                  >
                    <div className="project-card-heading">
                      <span className="project-card-icon">
                        <Server size={20} />
                      </span>
                      <Badge
                        tone={
                          project.connectionStatus === 'CONNECTED'
                            ? 'green'
                            : project.connectionStatus === 'UNAVAILABLE'
                              ? 'red'
                              : 'neutral'
                        }
                      >
                        {project.connectionStatus === 'UNKNOWN'
                          ? 'Not checked'
                          : project.connectionStatus === 'CONNECTED'
                            ? 'Connected'
                            : 'Unavailable'}
                      </Badge>
                    </div>
                    <h2>{project.name}</h2>
                    <p>{project.description || 'No description'}</p>
                    <div className="endpoint-text">{project.endpointUrl}</div>
                    <div className="project-card-footer">
                      <span>
                        {project.monitoringEnabled
                          ? '● Monitoring active'
                          : 'Monitoring paused'}
                      </span>
                      <span>
                        Overview <ArrowUpRight size={14} />
                      </span>
                    </div>
                  </a>
                ))}
              </div>
            )}
          </>
        )
      )}
    </>
  );
}

export function ProjectEditor({
  id,
  onChanged,
}: {
  id?: number;
  onChanged: () => void;
}) {
  const load = useCallback(
    (signal: AbortSignal) =>
      id ? api.project(id, signal) : Promise.resolve(null),
    [id],
  );
  const project = useResource(load);
  if (project.loading) return <Loading text="Loading project…" />;
  if (project.error)
    return <ErrorNotice message={project.error} retry={project.reload} />;
  return <ProjectForm initial={project.data ?? null} onChanged={onChanged} />;
}

function ProjectForm({
  initial,
  onChanged,
}: {
  initial: Project | null;
  onChanged: () => void;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [endpointUrl, setEndpointUrl] = useState(initial?.endpointUrl ?? '');
  const [endpointType, setEndpointType] = useState<
    ProjectRequest['endpointType']
  >(initial?.endpointType ?? 'PROMETHEUS');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: React.SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const value = {
        name: name.trim(),
        description: description.trim() || null,
        endpointUrl: endpointUrl.trim(),
        endpointType,
      };
      const project = initial
        ? await api.update(initial.id, value)
        : await api.create(value);
      onChanged();
      window.location.hash = `/projects/${project.id}`;
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not save project.',
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <a
        className="back-link"
        href={initial ? `#/projects/${initial.id}` : '#/projects'}
      >
        <ArrowLeft size={14} />
        {initial ? 'Back to overview' : 'Back to projects'}
      </a>
      <div className="page-heading">
        <div>
          <div className="eyebrow">PROJECT SETUP</div>
          <h1>{initial ? 'Edit project' : 'New project'}</h1>
          <p>Connect a service and start collecting its Golden Signals.</p>
        </div>
      </div>
      <form className="panel project-form" onSubmit={submit}>
        {error && <ErrorNotice message={error} />}
        <fieldset disabled={busy}>
          <label htmlFor="project-name">
            Project name <span>*</span>
          </label>
          <Input
            id="project-name"
            required
            maxLength={255}
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Checkout API"
          />
          <label htmlFor="project-description">Description</label>
          <textarea
            id="project-description"
            maxLength={2000}
            rows={3}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="What does this service do?"
          />
          <div className="form-section">
            <Server size={17} />
            <strong>Monitoring endpoint</strong>
          </div>
          <label htmlFor="endpoint-type">Endpoint type</label>
          <select
            id="endpoint-type"
            value={endpointType}
            onChange={(event) =>
              setEndpointType(
                event.target.value as ProjectRequest['endpointType'],
              )
            }
          >
            <option value="PROMETHEUS">Prometheus</option>
            <option value="ACTUATOR">Spring Boot Actuator</option>
          </select>
          <label htmlFor="endpoint-url">
            Endpoint URL <span>*</span>
          </label>
          <Input
            id="endpoint-url"
            type="url"
            required
            maxLength={2048}
            value={endpointUrl}
            onChange={(event) => setEndpointUrl(event.target.value)}
            placeholder="http://demo-service:8081/metrics"
          />
          <p className="field-hint">
            The endpoint must be reachable from the backend. For the Docker
            demo, use <code>http://demo-service:8081/metrics</code>.
          </p>
          <p className="field-hint">
            Save the project to check its endpoint and enable monitoring.
            {initial &&
              ' Changing the endpoint starts a fresh measurement baseline; history stays available.'}
          </p>
          {initial && (
            <p className="field-hint">Created {dateTime(initial.createdAt)}</p>
          )}
          <div className="form-actions">
            <a href={initial ? `#/projects/${initial.id}` : '#/projects'}>
              Cancel
            </a>
            <Button type="submit" disabled={busy || !name.trim()}>
              {busy ? 'Saving…' : initial ? 'Save changes' : 'Create project'}
            </Button>
          </div>
        </fieldset>
      </form>
    </>
  );
}
