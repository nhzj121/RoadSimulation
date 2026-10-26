import type {
  EvaluationMetricValue,
  EvaluationMetricValueStatus,
  EvaluationSnapshot
} from '../types/evaluation';
import {
  formatEvaluationMetric,
  metricStatusLabel,
  toChartValue
} from './evaluationPresentation';
import { evaluationTrendPoints } from './evaluationTrendPresentation';

export interface SimulationDataKpi {
  metricId: string;
  label: string;
  valueText: string;
  status: EvaluationMetricValueStatus | 'MISSING';
  statusText: string;
  reason: string | null;
}

export interface SimulationDataTrendSeries {
  metricId: string;
  name: string;
  data: Array<number | null>;
}

export interface SimulationDataLeftViewModel {
  runId: string;
  loopIndex: number;
  simTimeText: string;
  snapshotStatusText: string;
  snapshotStatusClass: string;
  processedAssignmentCount: number;
  failedAssignmentCount: number;
  errorCount: number;
  kpis: SimulationDataKpi[];
  remainingCapacity: SimulationDataKpi;
  trend: {
    categories: string[];
    series: SimulationDataTrendSeries[];
    availablePointCount: number;
  };
}

const KPI_DEFINITIONS = Object.freeze([
  { metricId: 'vehicleAvailableCount', fallbackLabel: '可用车辆数' },
  { metricId: 'vehicleInTransportCount', fallbackLabel: '运输中车辆数' },
  { metricId: 'vehicleEmptyMileageRatio', fallbackLabel: '车辆空载里程率' },
  { metricId: 'vehicleDistanceWeightedLoadRatio', fallbackLabel: '里程加权载重率' }
]);

const TREND_DEFINITIONS = Object.freeze([
  { metricId: 'vehicleEmptyMileageRatio', fallbackLabel: '空载里程率' },
  { metricId: 'vehicleDistanceWeightedLoadRatio', fallbackLabel: '里程加权载重率' }
]);

const SNAPSHOT_STATUS_LABELS = Object.freeze({
  COMPLETE: '完整',
  PARTIAL: '部分完成',
  FAILED: '失败'
});

function metricDisplay(
  snapshot: EvaluationSnapshot,
  metricId: string,
  fallbackLabel: string
): SimulationDataKpi {
  const metric = snapshot.metrics?.[metricId];
  const status = metric?.status || 'MISSING';
  return {
    metricId,
    label: metric?.displayName || fallbackLabel,
    valueText: formatEvaluationMetric(metric),
    status,
    statusText: metricStatusLabel(status),
    reason: metric?.reason || null
  };
}

function availableChartValue(metric: EvaluationMetricValue | undefined): number | null {
  if (metric?.status !== 'AVAILABLE' || metric.value === null || !Number.isFinite(metric.value)) {
    return null;
  }
  return toChartValue(metric.value, metric.unit);
}

/**
 * Phase 3：只把后端评价快照转换成左栏展示模型。
 * 本函数不计算评价公式，也不会把 NOT_AVAILABLE、INVALID 等状态替换为 0。
 */
export function buildSimulationDataLeftViewModel(
  snapshot: EvaluationSnapshot,
  trendSnapshots: EvaluationSnapshot[]
): SimulationDataLeftViewModel {
  const orderedTrend = evaluationTrendPoints(snapshot, trendSnapshots);

  const series = TREND_DEFINITIONS.map(definition => {
    const latestMetric = snapshot.metrics?.[definition.metricId];
    return {
      metricId: definition.metricId,
      name: latestMetric?.displayName || definition.fallbackLabel,
      data: orderedTrend.map(item => availableChartValue(item.snapshot?.metrics?.[definition.metricId]))
    };
  });

  return {
    runId: snapshot.simulationRunId,
    loopIndex: snapshot.loopIndex,
    simTimeText: snapshot.simTime ? snapshot.simTime.replace('T', ' ').slice(5, 19) : '--',
    snapshotStatusText: SNAPSHOT_STATUS_LABELS[snapshot.snapshotStatus] || snapshot.snapshotStatus,
    snapshotStatusClass: snapshot.snapshotStatus.toLowerCase(),
    processedAssignmentCount: snapshot.processedAssignmentCount,
    failedAssignmentCount: snapshot.failedAssignmentCount,
    errorCount: snapshot.errorCodes?.length || 0,
    kpis: KPI_DEFINITIONS.map(definition =>
      metricDisplay(snapshot, definition.metricId, definition.fallbackLabel)
    ),
    remainingCapacity: metricDisplay(
      snapshot,
      'vehicleRemainingCapacityTonnes',
      '当前剩余运力'
    ),
    trend: {
      categories: orderedTrend.map(item => item.label),
      series,
      availablePointCount: series.reduce(
        (count, item) => count + item.data.filter(value => value !== null).length,
        0
      )
    }
  };
}
