import type {
  EvaluationMetricComparison,
  EvaluationMetricValue,
  EvaluationMetricValueStatus,
  EvaluationRunComparison,
  EvaluationTrend
} from '../types/evaluation';

export interface EvaluationTrendChartPoint {
  loopIndex: number;
  snapshotRevision: number;
  simTime: string;
  snapshotStatus: string;
  metricStatus: EvaluationMetricValueStatus | 'MISSING';
  reason: string | null;
  rawValue: number | null;
  chartValue: number | null;
}

export interface EvaluationTrendChartViewModel {
  metricId: string;
  displayName: string;
  unit: string;
  axisUnit: string;
  points: EvaluationTrendChartPoint[];
  availablePointCount: number;
}

export interface EvaluationComparisonChartItem {
  side: 'left' | 'right';
  runId: string;
  runLabel: string;
  status: EvaluationMetricValueStatus;
  rawValue: number | null;
  chartValue: number | null;
}

export interface EvaluationComparisonChartViewModel {
  metric: EvaluationMetricComparison;
  displayName: string;
  unit: string;
  axisUnit: string;
  items: EvaluationComparisonChartItem[];
  comparable: boolean;
}

export function metricStatusLabel(status: EvaluationMetricValueStatus | 'MISSING'): string {
  return ({
    AVAILABLE: '可用',
    NOT_AVAILABLE: '暂不可用',
    NOT_SUPPORTED: '不支持',
    NOT_APPLICABLE: '不适用',
    INVALID: '无效',
    MISSING: '字段缺失'
  })[status];
}

export function formatEvaluationNumber(value: number, maximumFractionDigits = 2): string {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits
  }).format(value);
}

export function formatEvaluationMetric(metric: EvaluationMetricValue | null | undefined): string {
  if (!metric || metric.status !== 'AVAILABLE' || metric.value === null) return '--';
  if (metric.unit === 'ratio') return `${formatEvaluationNumber(metric.value * 100, 2)}%`;
  if (metric.unit === 'boolean') return metric.value >= 0.5 ? '满足' : '不满足';
  const digits = ['vehicle', 'task', 'road', 'event'].includes(metric.unit) ? 0 : 2;
  return `${formatEvaluationNumber(metric.value, digits)} ${metric.unit}`;
}

export function chartAxisUnit(unit: string): string {
  if (unit === 'ratio') return '%';
  if (unit === 'boolean') return '状态';
  return unit;
}

export function toChartValue(value: number | null, unit: string): number | null {
  if (value === null || !Number.isFinite(value)) return null;
  if (unit === 'ratio') return value * 100;
  return value;
}

export function buildTrendChartViewModel(
  trend: EvaluationTrend | null,
  metricId: string
): EvaluationTrendChartViewModel | null {
  if (!trend || !metricId) return null;
  const firstMetric = trend.points
    .map(point => point.metrics?.[metricId])
    .find((metric): metric is EvaluationMetricValue => Boolean(metric));
  if (!firstMetric) return null;

  const points = trend.points.map(point => {
    const metric = point.metrics?.[metricId];
    const available = metric?.status === 'AVAILABLE'
      && metric.value !== null
      && Number.isFinite(metric.value);
    const rawValue = available ? Number(metric!.value) : null;
    return {
      loopIndex: point.loopIndex,
      snapshotRevision: point.snapshotRevision,
      simTime: point.simTime,
      snapshotStatus: point.snapshotStatus,
      metricStatus: metric?.status || 'MISSING',
      reason: metric?.reason || null,
      rawValue,
      chartValue: toChartValue(rawValue, firstMetric.unit)
    } as EvaluationTrendChartPoint;
  });

  return {
    metricId,
    displayName: firstMetric.displayName,
    unit: firstMetric.unit,
    axisUnit: chartAxisUnit(firstMetric.unit),
    points,
    availablePointCount: points.filter(point => point.chartValue !== null).length
  };
}

export function buildComparisonChartViewModel(
  comparison: EvaluationRunComparison | null,
  metricId: string
): EvaluationComparisonChartViewModel | null {
  if (!comparison || !metricId) return null;
  const metric = comparison.metrics.find(item => item.metricId === metricId);
  if (!metric) return null;

  const items: EvaluationComparisonChartItem[] = [
    {
      side: 'left',
      runId: comparison.leftRun.simulationRunId,
      runLabel: compactRunLabel(comparison.leftRun.simulationRunId, '左侧运行'),
      status: metric.leftStatus,
      rawValue: metric.leftValue,
      chartValue: metric.leftStatus === 'AVAILABLE'
        ? toChartValue(metric.leftValue, metric.unit)
        : null
    },
    {
      side: 'right',
      runId: comparison.rightRun.simulationRunId,
      runLabel: compactRunLabel(comparison.rightRun.simulationRunId, '右侧运行'),
      status: metric.rightStatus,
      rawValue: metric.rightValue,
      chartValue: metric.rightStatus === 'AVAILABLE'
        ? toChartValue(metric.rightValue, metric.unit)
        : null
    }
  ];

  return {
    metric,
    displayName: metric.displayName,
    unit: metric.unit,
    axisUnit: chartAxisUnit(metric.unit),
    items,
    comparable: items.every(item => item.chartValue !== null)
  };
}

export function formatComparisonMetricValue(
  value: number | null,
  status: EvaluationMetricValueStatus,
  unit: string
): string {
  if (status !== 'AVAILABLE' || value === null) return metricStatusLabel(status);
  if (unit === 'ratio') return `${formatEvaluationNumber(value * 100, 2)}%`;
  if (unit === 'boolean') return value >= 0.5 ? '满足' : '不满足';
  const digits = ['vehicle', 'task', 'road', 'event', 'count'].includes(unit) ? 0 : 2;
  return `${formatEvaluationNumber(value, digits)} ${unit}`;
}

function compactRunLabel(runId: string, fallback: string): string {
  if (!runId) return fallback;
  return runId.length <= 12 ? runId : `${runId.slice(0, 8)}…${runId.slice(-4)}`;
}
