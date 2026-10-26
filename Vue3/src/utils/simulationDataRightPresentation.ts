import type {
  EvaluationMetricValueStatus,
  EvaluationSnapshot
} from '../types/evaluation';
import {
  formatEvaluationMetric,
  formatEvaluationNumber,
  metricStatusLabel
} from './evaluationPresentation';
import {
  buildDomainViewModel,
  type DomainChartModel
} from './evaluationDomainPresentation';

export interface SimulationObjectiveDisplay {
  metricId: string;
  index: number;
  label: string;
  valueText: string;
  status: EvaluationMetricValueStatus | 'MISSING';
  statusText: string;
  reason: string | null;
}

export interface SimulationServiceConstraintDisplay {
  status: EvaluationMetricValueStatus | 'MISSING';
  statusText: string;
  resultText: string;
  tone: 'ok' | 'warning' | 'unknown';
  thresholdText: string;
  cargoP95Text: string;
  taskP95Text: string;
  reason: string | null;
}

export interface SimulationDataRightViewModel {
  objectives: SimulationObjectiveDisplay[];
  vehicleStateChart: DomainChartModel | null;
  serviceConstraint: SimulationServiceConstraintDisplay;
  errorCodes: string[];
}

const OBJECTIVE_DEFINITIONS = Object.freeze([
  { metricId: 'emptyMileageRatio', fallbackLabel: '全局车辆空载里程率' },
  { metricId: 'capacityWasteRatio', fallbackLabel: '全局里程加权运力浪费率' },
  { metricId: 'unmetDemandRatio', fallbackLabel: '全局运输需求未达成率' },
  { metricId: 'carbonIntensity', fallbackLabel: '全局单位有效吨公里碳排放' }
]);

/** Phase 4：只组合既有评价指标，不对四目标归一化或生成前端评分。 */
export function buildSimulationDataRightViewModel(
  snapshot: EvaluationSnapshot
): SimulationDataRightViewModel {
  const objectives = OBJECTIVE_DEFINITIONS.map((definition, index) => {
    const metric = snapshot.metrics?.[definition.metricId];
    // Phase 7：保留状态联合类型，避免 map 返回对象被 TS 推断为任意 string；运行值不变。
    const status: SimulationObjectiveDisplay['status'] = metric?.status || 'MISSING';
    return {
      metricId: definition.metricId,
      index: index + 1,
      label: metric?.displayName || definition.fallbackLabel,
      valueText: formatEvaluationMetric(metric),
      status,
      statusText: metricStatusLabel(status),
      reason: metric?.reason || null
    };
  });

  const constraintMetric = snapshot.metrics?.waitingServiceCompliant;
  const constraintStatus = constraintMetric?.status || 'MISSING';
  const constraintAvailable = constraintStatus === 'AVAILABLE'
    && constraintMetric?.value !== null
    && Number.isFinite(constraintMetric.value);
  const compliant = constraintAvailable ? Number(constraintMetric!.value) >= 0.5 : null;

  return {
    objectives,
    vehicleStateChart: buildDomainViewModel(snapshot, 'vehicle')?.primary || null,
    serviceConstraint: {
      status: constraintStatus,
      statusText: metricStatusLabel(constraintStatus),
      resultText: compliant === null ? metricStatusLabel(constraintStatus) : compliant ? '满足' : '不满足',
      tone: compliant === null ? 'unknown' : compliant ? 'ok' : 'warning',
      thresholdText: `${formatEvaluationNumber(snapshot.thresholds.maxServiceWaitSeconds, 0)} s`,
      cargoP95Text: formatEvaluationMetric(snapshot.metrics?.cargoP95WaitSeconds),
      taskP95Text: formatEvaluationMetric(snapshot.metrics?.taskP95ServiceWaitSeconds),
      reason: constraintMetric?.reason || null
    },
    errorCodes: [...(snapshot.errorCodes || [])]
  };
}
