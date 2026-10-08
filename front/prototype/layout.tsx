import type { Metadata } from 'next';
import './globals.css';
export const metadata: Metadata = {
  title: 'SRE Copilot · Checkout API',
  icons: { icon: '/favicon.svg' },
  description:
    'Monitor Golden Signals and investigate rule-triggered incidents with AI-assisted analysis. Local mock prototype.',
};
export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
