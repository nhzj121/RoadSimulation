/**
 * Phase 6C：前端实时评价快照契约。
 *
 * 本文件只映射 Phase 6B 后端返回结构，不在前端重新定义指标公式或阈值，
 * 从而保证动态展示与同一 simulation tick 的后端事实保持一致。
 */

export type EvaluationMetricCategory =
  | 'GLOBAL_OBJECTIVE'
  | 'SERVICE_CONSTRAINT'
  | 'VEHICLE'
  | 'CARGO'
  | 'TASK'
  | 'ENVIRONMENT';

export type EvaluationMetricValueStatus =
  | 'AVAILABLE'
  | 'NOT_AVAILABLE'
  // Phase 7E-R：后端明确声明当前契约不提供该指标，不代表采集失败。
  | 'NOT_SUPPORTED'
  | 'NOT_APPLICABLE'
  | 'INVALID';

export type EvaluationSnapshotStatus = 'COMPLETE' | 'PARTIAL' | 'FAILED';

export type EvaluationRunKind = 'REGULAR' | 'DISPATCH_COMPARISON';

export interface EvaluationMetricValue {
  metricId: string;
  displayName: string;
  category: EvaluationMetricCategory;
  unit: string;
  status: EvaluationMetricValueStatus;
  value: number | null;
  reason: string | null;
}

export interface EvaluationThresholdSnapshot {
  lowLoadRatioThreshold: number;
  fullLoadRatioThreshold: number;
  maxServiceWaitSeconds: number;
}

export interface EvaluationSnapshot {
  contractVersion: string;
  simulationRunId: string;
  runKind: EvaluationRunKind;
  loopIndex: number;
  snapshotRevision: number;
  simTime: string;
  snapshotStatus: EvaluationSnapshotStatus;
  processedAssignmentCount: number;
  failedAssignmentCount: number;
  errorCodes: string[];
  thresholds: EvaluationThresholdSnapshot;
  metrics: Record<string, EvaluationMetricValue>;
}

/** Phase 6C：EvaluationController 使用的实际 ApiResponse 结构。 */
export interface EvaluationApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
}
