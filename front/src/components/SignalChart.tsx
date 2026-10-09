import { useId } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ChartContainer } from '@/components/ui/chart';
import { formatValue, operators, type Signal } from '../lib/signals';
import type { Rule, Snapshot } from '../lib/types';

export function SignalChart({
  signal,
  snapshots,
  rules = [],
  small = false,
  detectedAt,
}: {
  signal: Signal;
  snapshots: Snapshot[];
  rules?: Pick<Rule, 'id' | 'metric' | 'operator' | 'threshold' | 'enabled'>[];
  small?: boolean;
  detectedAt?: string;
}) {
  const fillId = useId().replace(/:/g, '');
  if (!snapshots.length)
    return (
      <div
        className={
          small ? 'spark-chart chart-empty' : 'metric-chart chart-empty'
        }
      >
        No measurements in this period
      </div>
    );
  const data = snapshots.map((snapshot) => ({
    timestamp: Date.parse(snapshot.timestamp),
    value: snapshot[signal.key],
  }));
  const thresholds = rules.filter(
    (rule) => rule.enabled && rule.metric === signal.metric,
  );
  const time = (value: number) =>
    new Date(value).toLocaleTimeString('en-GB', {
      timeZone: 'UTC',
      hour: '2-digit',
      minute: '2-digit',
    });
  return (
    <ChartContainer
      className={small ? 'spark-chart' : 'metric-chart'}
      aria-label={`${signal.name} history in ${signal.unit}`}
    >
      <AreaChart
        data={data}
        margin={{
          top: 15,
          right: small ? 0 : 15,
          bottom: 0,
          left: small ? 0 : -15,
        }}
      >
        <defs>
          <linearGradient id={fillId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={signal.color} stopOpacity={0.18} />
            <stop offset="100%" stopColor={signal.color} stopOpacity={0.015} />
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
          tickFormatter={time}
          tickLine={false}
          axisLine={false}
          tick={{ fontSize: 10, fill: '#8c9491' }}
          minTickGap={40}
        />
        <YAxis
          hide={small}
          tickFormatter={formatValue}
          domain={[
            0,
            (max: number) =>
              Math.max(max, ...thresholds.map((rule) => rule.threshold), 1) *
              1.1,
          ]}
          tickLine={false}
          axisLine={false}
          tick={{ fontSize: 10, fill: '#8c9491' }}
          width={55}
        />
        <Tooltip
          labelFormatter={(value) =>
            new Date(Number(value)).toLocaleString('en-GB', {
              timeZone: 'UTC',
            }) + ' UTC'
          }
          formatter={(value) => [
            Number(value).toLocaleString('en-US', {
              maximumFractionDigits: 2,
            }) +
              ' ' +
              signal.unit,
            signal.name,
          ]}
          contentStyle={{ borderRadius: 8, fontSize: 12 }}
        />
        {!small &&
          thresholds.map((rule) => (
            <ReferenceLine
              key={rule.id}
              y={rule.threshold}
              stroke="#c4a18e"
              strokeDasharray="4 4"
              label={{
                value: `${operators[rule.operator]} ${rule.threshold}`,
                position: 'insideTopRight',
                fill: '#9a8a81',
                fontSize: 10,
              }}
            />
          ))}
        {!small && detectedAt && (
          <ReferenceLine
            x={Date.parse(detectedAt)}
            stroke="#9caaa1"
            strokeDasharray="3 3"
            label={{
              value: 'Incident created',
              position: 'insideTopLeft',
              fontSize: 10,
            }}
          />
        )}
        <Area
          isAnimationActive={false}
          type="linear"
          dataKey="value"
          stroke={signal.color}
          strokeWidth={2}
          fill={`url(#${fillId})`}
          dot={data.length === 1 ? { r: 3 } : false}
          connectNulls={false}
        />
      </AreaChart>
    </ChartContainer>
  );
}
