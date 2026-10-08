import { LoaderCircle, TriangleAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';

export function Loading({ text = 'Loading…' }: { text?: string }) {
  return (
    <output className="loading-state">
      <LoaderCircle size={22} className="spin" />
      {text}
    </output>
  );
}
export function ErrorNotice({
  message,
  retry,
}: {
  message: string;
  retry?: () => void;
}) {
  return (
    <div className="error-notice" role="alert">
      <TriangleAlert size={18} />
      <span>{message}</span>
      {retry && (
        <Button variant="outline" onClick={retry}>
          Try again
        </Button>
      )}
    </div>
  );
}
export function Badge({
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
