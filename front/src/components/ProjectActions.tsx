import { useState } from 'react';
import {
  Pause,
  Pencil,
  Play,
  Radio,
  SlidersHorizontal,
  Trash2,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { api } from '../lib/api';
import type { Project } from '../lib/types';
import { ErrorNotice } from './Feedback';

export function ProjectActions({
  project,
  onChanged,
}: {
  project: Project;
  onChanged: () => void;
}) {
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(false);
  async function perform(action: string) {
    setBusy(action);
    setError('');
    setMessage('');
    try {
      if (action === 'test') {
        const result = await api.testConnection(project.id);
        setMessage(result.message);
      } else if (action === 'monitoring')
        await api.monitoring(project.id, !project.monitoringEnabled);
      else {
        await api.delete(project.id);
        window.location.hash = '/projects';
      }
      onChanged();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Operation failed.');
    } finally {
      setBusy('');
    }
  }
  return (
    <>
      <div className="project-controls">
        <Button
          variant="outline"
          disabled={!!busy}
          onClick={() => void perform('test')}
        >
          <Radio size={15} />
          {busy === 'test' ? 'Checking…' : 'Test connection'}
        </Button>
        <Button disabled={!!busy} onClick={() => void perform('monitoring')}>
          {project.monitoringEnabled ? <Pause size={15} /> : <Play size={15} />}
          {busy === 'monitoring'
            ? 'Updating…'
            : project.monitoringEnabled
              ? 'Pause monitoring'
              : 'Enable monitoring'}
        </Button>
        <a
          className={`secondary-link ${busy ? 'link-disabled' : ''}`}
          aria-disabled={!!busy}
          href={`#/projects/${project.id}/edit`}
        >
          <Pencil size={14} />
          Edit project
        </a>
        <Button
          variant="ghost"
          aria-label="Delete project"
          disabled={!!busy}
          onClick={() => setConfirmDelete(true)}
        >
          <Trash2 size={16} />
        </Button>
        <a className="secondary-link" href={`#/projects/${project.id}/rules`}>
          <SlidersHorizontal size={14} /> Manage rules
        </a>
      </div>
      {message && <output className="connection-message">{message}</output>}
      {error && <ErrorNotice message={error} />}
      {confirmDelete && (
        <section
          className="delete-confirm"
          role="alertdialog"
          aria-labelledby="delete-heading"
          aria-describedby="delete-description"
        >
          <h2 id="delete-heading">Delete {project.name}?</h2>
          <p id="delete-description">
            This deletes the project, its metric history, rules and incidents.
          </p>
          <div>
            <Button
              variant="outline"
              disabled={!!busy}
              onClick={() => setConfirmDelete(false)}
            >
              Keep project
            </Button>
            <Button
              variant="destructive"
              disabled={!!busy}
              onClick={() => void perform('delete')}
            >
              {busy === 'delete' ? 'Deleting…' : 'Delete project and data'}
            </Button>
          </div>
        </section>
      )}
    </>
  );
}
