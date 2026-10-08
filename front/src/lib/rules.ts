import type { RuleRequest } from './types';

export function ruleNumbers(
  threshold: string,
  interval: string,
  checks: string,
) {
  const value = Number(threshold);
  if (!threshold.trim() || !Number.isFinite(value) || value < 0)
    throw new Error(
      'Threshold must be a finite number greater than or equal to zero.',
    );
  const positiveInteger = (input: string, label: string) => {
    const parsed = Number(input);
    if (
      !input.trim() ||
      !Number.isInteger(parsed) ||
      parsed < 1 ||
      parsed > 2_147_483_647
    )
      throw new Error(
        `${label} must be a whole number between 1 and 2147483647.`,
      );
    return parsed;
  };
  return {
    threshold: value,
    checkIntervalSeconds: positiveInteger(interval, 'Check interval'),
    consecutiveViolations: positiveInteger(checks, 'Consecutive violations'),
  } satisfies Pick<
    RuleRequest,
    'threshold' | 'checkIntervalSeconds' | 'consecutiveViolations'
  >;
}
