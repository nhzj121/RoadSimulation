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

/** Phase 8：后端冻结的柴油当量能耗与直接运行排放模型参数。 */
export interface EnergyEmissionModelSnapshot {
  modelId: string;
  energyUnit: string;
  baseFuelLitersPerKm: number;
  directEmissionKgPerLiter: number;
  loadFactorBeta: number;
  environmentFactorGamma: number;
  l1MaxCapacityTonnes: number;
  l2MaxCapacityTonnes: number;
  mediumMaxCapacityTonnes: number;
  l1VehicleFactor: number;
  l2VehicleFactor: number;
  mediumVehicleFactor: number;
  heavyVehicleFactor: number;
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
  energyEmissionModel: EnergyEmissionModelSnapshot;
  metrics: Record<string, EvaluationMetricValue>;
}

/** Phase 9C：跨 reset 保留的评价运行摘要。 */
export interface EvaluationRunSummary {
  simulationRunId: string;
  runKind: EvaluationRunKind;
  contractVersion: string;
  firstLoopIndex: number;
  lastLoopIndex: number;
  snapshotCount: number;
  firstSimTime: string;
  lastSimTime: string;
  latestStatus: EvaluationSnapshotStatus;
}

/** Phase 9C：趋势点保留后端状态和原因，前端不得把缺失值补成 0。 */
export interface EvaluationTrendPoint {
  loopIndex: number;
  snapshotRevision: number;
  simTime: string;
  snapshotStatus: EvaluationSnapshotStatus;
  metrics: Record<string, EvaluationMetricValue>;
}

export interface EvaluationTrend {
  run: EvaluationRunSummary;
  metricIds: string[];
  points: EvaluationTrendPoint[];
}


/** Phase 9C：rightMinusLeft/relativeChangeRatio 仅在左右最终值均 AVAILABLE 时存在。 */
export interface EvaluationMetricComparison {
  metricId: string;
  displayName: string;
  category: EvaluationMetricCategory;
  unit: string;
  leftStatus: EvaluationMetricValueStatus;
  leftValue: number | null;
  rightStatus: EvaluationMetricValueStatus;
  rightValue: number | null;
  rightMinusLeft: number | null;
  relativeChangeRatio: number | null;
}


export interface EvaluationRunComparison {
  leftRun: EvaluationRunSummary;
  rightRun: EvaluationRunSummary;
  leftFinalSnapshot: EvaluationSnapshot;
  rightFinalSnapshot: EvaluationSnapshot;
  metrics: EvaluationMetricComparison[];
}

/** Phase 6C：EvaluationController 使用的实际 ApiResponse 结构。 */
export interface EvaluationApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
}
