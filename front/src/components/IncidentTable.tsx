import { ArrowUpRight, CheckCheck } from 'lucide-react';
import type { Incident } from '../lib/types';
import { dateTime, incidentCondition } from '../lib/signals';
import { Badge } from './Feedback';

export function IncidentTable({ incidents }: { incidents: Incident[] }) {
  if (!incidents.length)
    return (
      <div className="empty-state">
        <CheckCheck size={25} />
        <h3>No incidents to display</h3>
        <p>Incidents appear when a rule detects consecutive violations.</p>
      </div>
    );
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Incident</th>
            <th>Status</th>
            <th>Severity</th>
            <th>Triggered rule</th>
            <th>Started · UTC</th>
            <th>
              <span className="sr-only">Open incident</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {incidents.map((incident) => (
            <tr key={incident.id}>
              <td>
                <a
                  href={`#/incidents/${incident.id}`}
                  aria-label={`INC-${incident.id}: ${incident.title}`}
                  className="incident-link"
                >
                  <span className="incident-id">INC-{incident.id}</span>
                  <strong>{incident.title}</strong>
                </a>
              </td>
              <td>
                <Badge tone={incident.status === 'OPEN' ? 'red' : 'green'}>
                  {incident.status === 'OPEN' ? 'Open' : 'Resolved'}
                </Badge>
              </td>
              <td>
                <span className={`severity ${incident.severity.toLowerCase()}`}>
                  {incident.severity === 'CRITICAL' ? 'Critical' : 'Warning'}
                </span>
              </td>
              <td className="rule-cell">{incidentCondition(incident)}</td>
              <td>{dateTime(incident.startedAt)}</td>
              <td>
                <a
                  aria-label={`Open INC-${incident.id}`}
                  href={`#/incidents/${incident.id}`}
                >
                  <ArrowUpRight size={17} />
                </a>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
