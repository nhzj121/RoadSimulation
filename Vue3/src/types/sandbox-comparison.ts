import type { EvaluationSnapshot, EvaluationMetricValue } from './evaluation'

export interface SandboxComparisonSide {
  executionId: string
  runSpecKey: string
  revision: number
  displayName: string
  scenarioName: string
  baselineId: string
  strategy: string
  algorithmProfileId: string
  clock: { startLocalDateTime: string; tickDurationSeconds: number; totalLoops: number }
  manifestSha256: string
  finalEvaluationSha256: string
  finalSnapshot: EvaluationSnapshot
}
export interface SandboxMetricComparison {
  metricId: string
  displayName: string
  category: string
  unit: string
  left: EvaluationMetricValue | null
  right: EvaluationMetricValue | null
  comparable: boolean
  reason: string | null
  rightMinusLeft: number | null
  relativeChangeRatio: number | null
}
export interface SandboxComparison {
  artifactVersion: 'sandbox-execution-comparison/v1'
  left: SandboxComparisonSide
  right: SandboxComparisonSide
  metrics: SandboxMetricComparison[]
  differences: Array<{ path: string; left: unknown; right: unknown }>
  integrityScope: 'MANIFEST_AND_EVALUATION_PAYLOADS'
  ledgerIntegrity: 'NOT_CHECKED'
}
export interface SandboxExecutionList {
  items: Array<{ execution_id: string; run_spec_key: string; run_spec_revision: number;
    execution_status: string; requested_loops: number; completed_loops: number; created_at: string; finished_at: string | null }>
  hasMore: boolean
  offset: number
  limit: number
}
