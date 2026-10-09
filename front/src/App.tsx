import { useEffect, useState } from 'react';
import {
  Activity,
  BellRing,
  ChevronRight,
  FolderKanban,
  LayoutDashboard,
  Radio,
  Server,
  SlidersHorizontal,
} from 'lucide-react';
import { api } from './lib/api';
import { useResource } from './hooks/useResource';
import { ProjectsPage, ProjectEditor } from './pages/Projects';
import { OverviewPage } from './pages/Overview';
import { IncidentDetails, IncidentsPage } from './pages/Incidents';
import { RulesPage, RuleEditor } from './pages/Rules';
import { ErrorNotice } from './components/Feedback';

export function App() {
  const [route, setRoute] = useState(
    () => window.location.hash.slice(1) || '/projects',
  );
  useEffect(() => {
    const sync = () => {
      setRoute(window.location.hash.slice(1) || '/projects');
      window.scrollTo(0, 0);
    };
    window.addEventListener('hashchange', sync);
    return () => window.removeEventListener('hashchange', sync);
  }, []);
  const isProjectsPage = route === '/' || route === '/projects';
  const projects = useResource(api.projects, isProjectsPage ? 30_000 : 0);
  const projectRoute =
    /^\/projects\/(\d+)(?:\/(edit|incidents|rules)(?:\/(new|\d+\/edit))?)?$/.exec(
      route,
    );
  const incidentRoute = /^\/incidents\/(\d+)$/.exec(route);
  const projectId = projectRoute ? Number(projectRoute[1]) : undefined;
  const selected = projects.data?.find((project) => project.id === projectId);
  const section =
    projectRoute?.[2] === 'edit'
      ? 'Edit project'
      : projectRoute?.[2] === 'rules'
        ? projectRoute[3] === 'new'
          ? 'New rule'
          : projectRoute[3]
            ? 'Edit rule'
            : 'Rules'
        : projectRoute?.[2] === 'incidents' || incidentRoute
          ? 'Incidents'
          : projectId
            ? 'Overview'
            : route === '/projects/new'
              ? 'New project'
              : 'Projects';
  const known =
    route === '/' ||
    route === '/projects' ||
    route === '/projects/new' ||
    (projectRoute && (!projectRoute[3] || projectRoute[2] === 'rules')) ||
    incidentRoute;
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a href="#/projects" className="brand">
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
            <Server size={18} />
          </span>
          <div>
            <strong>Local workspace</strong>
            <small>Monitoring Core</small>
          </div>
        </div>
        <div className="nav-section">OBSERVABILITY</div>
        <nav>
          <a
            href="#/projects"
            aria-label="Projects"
            className={!projectId && !incidentRoute ? 'selected' : ''}
          >
            <FolderKanban size={18} />
            Projects
          </a>
          {projectId && (
            <>
              <a
                href={`#/projects/${projectId}`}
                aria-label="Overview"
                className={section === 'Overview' ? 'selected' : ''}
              >
                <LayoutDashboard size={18} />
                Overview
              </a>
              <a
                href={`#/projects/${projectId}/rules`}
                aria-label="Rules"
                className={projectRoute?.[2] === 'rules' ? 'selected' : ''}
              >
                <SlidersHorizontal size={18} />
                Rules
              </a>
              <a
                href={`#/projects/${projectId}/incidents`}
                aria-label="Incidents"
                className={section === 'Incidents' ? 'selected' : ''}
              >
                <BellRing size={18} />
                Incidents
              </a>
            </>
          )}
        </nav>
        {projectId && (
          <div className="sidebar-context">
            <span className="context-line" />
            <div>
              <small>MONITORED PROJECT</small>
              <strong>{selected?.name || `Project #${projectId}`}</strong>
              <span>
                {selected?.monitoringEnabled
                  ? 'Monitoring active'
                  : 'Monitoring paused'}
              </span>
            </div>
          </div>
        )}
        <div className="sidebar-bottom">
          <div className="workspace-card">
            <span>
              <Radio size={14} />
              Monitoring Core
            </span>
            <p>Real metrics. Rule-based incidents.</p>
          </div>
          <div className="user-profile">
            <span className="avatar">LU</span>
            <div>
              <strong>Local user</strong>
              <small>Development workspace</small>
            </div>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <a href="#/projects">Projects</a>
            {projectId && (
              <>
                <ChevronRight size={14} />
                <a href={`#/projects/${projectId}`} className="crumb-project">
                  <Server size={14} />
                  {selected?.name || `Project #${projectId}`}
                </a>
              </>
            )}
            <ChevronRight size={14} />
            <strong>{section}</strong>
          </div>
          <span className="environment">local workspace</span>
        </header>
        <main>
          {!known ? (
            <ErrorNotice message="Page not found. Use Projects to select a service." />
          ) : route === '/projects/new' || projectRoute?.[2] === 'edit' ? (
            <ProjectEditor
              key={route}
              id={projectId}
              onChanged={projects.reload}
            />
          ) : projectRoute?.[2] === 'rules' ? (
            projectRoute[3] ? (
              <RuleEditor
                key={route}
                projectId={projectId!}
                ruleId={
                  projectRoute[3] === 'new'
                    ? undefined
                    : Number(projectRoute[3].split('/')[0])
                }
              />
            ) : (
              <RulesPage key={route} projectId={projectId!} />
            )
          ) : projectRoute?.[2] === 'incidents' ? (
            <IncidentsPage key={route} projectId={projectId!} />
          ) : projectId ? (
            <OverviewPage
              key={projectId}
              projectId={projectId}
              onChanged={projects.reload}
            />
          ) : incidentRoute ? (
            <IncidentDetails
              key={route}
              incidentId={Number(incidentRoute[1])}
            />
          ) : (
            <ProjectsPage
              projects={projects.data}
              loading={projects.loading}
              error={projects.error}
              reload={projects.reload}
            />
          )}
          <footer className="page-footer">
            <span>
              SRE Copilot / A little more signal. A little less noise.
            </span>
            <span>Monitoring Core · REST API</span>
          </footer>
        </main>
      </div>
    </div>
  );
}
