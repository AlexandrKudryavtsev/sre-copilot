import {
  BrainCircuit,
  LoaderCircle,
  RotateCw,
  TriangleAlert,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAiAnalysis } from '../hooks/useAiAnalysis';
import { dateTime } from '../lib/signals';
import { Badge, ErrorNotice, Loading } from './Feedback';

const statuses = {
  PENDING: { label: 'Analyzing', tone: 'neutral' },
  COMPLETED: { label: 'Completed', tone: 'green' },
  FAILED: { label: 'Failed', tone: 'red' },
  INSUFFICIENT_DATA: { label: 'Insufficient data', tone: 'amber' },
};

export function AiAnalysisPanel({ incidentId }: { incidentId: number }) {
  const { analysis, loading, retrying, error, refresh, retry } =
    useAiAnalysis(incidentId);
  const pending = analysis?.status === 'PENDING';
  const hasResult =
    analysis?.status === 'COMPLETED' ||
    analysis?.status === 'INSUFFICIENT_DATA';
  const status = analysis && statuses[analysis.status];
  return (
    <section
      className="panel ai-analysis-panel"
      aria-labelledby="ai-analysis-heading"
    >
      <div className="panel-heading ai-analysis-heading">
        <div className="ai-analysis-title">
          <span className="ai-analysis-icon">
            <BrainCircuit size={20} />
          </span>
          <div>
            <h2 id="ai-analysis-heading">AI Analysis</h2>
            <p>Potential causes and suggested next steps.</p>
          </div>
        </div>
        <div className="ai-analysis-controls">
          {status && <Badge tone={status.tone}>{status.label}</Badge>}
          {!loading && !pending && analysis !== undefined && (
            <Button variant="outline" disabled={retrying} onClick={retry}>
              {retrying ? <LoaderCircle className="spin" /> : <RotateCw />}
              {retrying
                ? 'Starting analysis…'
                : analysis === null
                  ? 'Analyze incident'
                  : 'Retry Analysis'}
            </Button>
          )}
        </div>
      </div>
      <div className="ai-analysis-body">
        {error && <ErrorNotice message={error} retry={refresh} />}
        <div aria-live="polite" aria-atomic="true">
          {loading ? (
            <Loading text="Loading AI analysis…" />
          ) : pending ? (
            <div className="ai-analysis-message">
              <LoaderCircle size={22} className="spin" />
              <div>
                <strong>Analyzing incident…</strong>
                <p>
                  Reviewing the detection metrics and recent signal history.
                </p>
              </div>
            </div>
          ) : analysis?.status === 'FAILED' ? (
            <div className="ai-analysis-message ai-analysis-failed">
              <TriangleAlert size={22} />
              <div>
                <strong>Analysis could not be completed</strong>
                <p>
                  {analysis.errorMessage ||
                    'The AI service could not analyze this incident. Try again.'}
                </p>
              </div>
            </div>
          ) : analysis === null ? (
            <div className="ai-analysis-message">
              <BrainCircuit size={22} />
              <div>
                <strong>No analysis yet</strong>
                <p>
                  Start an analysis using the incident’s detection metrics and
                  recent history.
                </p>
              </div>
            </div>
          ) : analysis?.status === 'INSUFFICIENT_DATA' ? (
            <div className="ai-analysis-message ai-analysis-insufficient">
              <TriangleAlert size={22} />
              <div>
                <strong>More data is needed</strong>
                <p>
                  The available metrics do not support a useful cause
                  hypothesis. Review the evidence and suggested checks below.
                </p>
              </div>
            </div>
          ) : analysis?.status === 'COMPLETED' ? (
            <span className="sr-only">Analysis completed.</span>
          ) : null}
        </div>
        {hasResult && analysis && (
          <div className="ai-analysis-result">
            {analysis.probableCause && (
              <div className="ai-analysis-cause">
                <h3>Probable cause</h3>
                <p>{analysis.probableCause}</p>
              </div>
            )}
            <div className="ai-analysis-columns">
              <div>
                <h3>Evidence</h3>
                {analysis.evidence.length ? (
                  <ul>
                    {analysis.evidence.map((item, index) => (
                      <li key={index}>{item}</li>
                    ))}
                  </ul>
                ) : (
                  <p className="ai-analysis-empty">
                    No supporting evidence provided.
                  </p>
                )}
              </div>
              <div>
                <h3>Recommended actions</h3>
                {analysis.recommendations.length ? (
                  <ol>
                    {analysis.recommendations.map((item, index) => (
                      <li key={index}>{item}</li>
                    ))}
                  </ol>
                ) : (
                  <p className="ai-analysis-empty">
                    No recommended actions provided.
                  </p>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
      <div className="ai-analysis-footer">
        <span>
          AI-generated suggestions. Verify findings before taking action.
        </span>
        {analysis?.completedAt && (
          <span>Analyzed {dateTime(analysis.completedAt)}</span>
        )}
      </div>
    </section>
  );
}
