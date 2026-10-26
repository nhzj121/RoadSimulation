import type { EvaluationMetricValue, EvaluationSnapshot } from '../types/evaluation'

export interface RecordedMetricGroup { category: string; metrics: EvaluationMetricValue[] }
export interface RecordedMetricDomain extends RecordedMetricGroup { title: string; chartKey?: string }
const domains = [
  { category: 'VEHICLE', title: '车辆', chartKey: 'vehicle' },
  { category: 'CARGO', title: '货物', chartKey: 'cargo' },
  { category: 'TASK', title: '运输任务', chartKey: 'task' },
  { category: 'ENVIRONMENT', title: '外部环境' },
]

/** The four stable entries never hide categories added by historical contracts. */
export function recordedMetricDomains(groups: RecordedMetricGroup[]): RecordedMetricDomain[] {
  return [
    ...domains.map(domain => ({ ...domain, metrics: groups.find(group => group.category === domain.category)?.metrics || [] })),
    ...groups.filter(group => !['GLOBAL_OBJECTIVE','SERVICE_CONSTRAINT'].includes(group.category) && !domains.some(domain => domain.category === group.category)).map(group => ({
      ...group, title: ({ GLOBAL_OBJECTIVE: '全局目标', SERVICE_CONSTRAINT: '服务约束' } as Record<string, string>)[group.category] || group.category,
    })),
  ]
}

export function supplementaryRecordedGroups(groups: RecordedMetricGroup[]): RecordedMetricDomain[] {
  return groups.filter(group => ['GLOBAL_OBJECTIVE','SERVICE_CONSTRAINT'].includes(group.category)).map(group => ({...group,title:group.category}))
}

// These are the units consumed by the existing v1.9 domain charts/display model.
// Missing metrics are left missing: the shared chart reports their original
// availability/reasons. Mismatched units/categories must not be interpreted as v1.9.
const units: Record<string, string> = {}
for (const [unit, ids] of [
  ['vehicle', 'vehicleTotalCount vehicleAvailableCount vehicleInTransportCount vehicleEmptyDrivingCount vehicleLoadedDrivingCount'],
  ['task', 'taskTotalCount taskAssignedPendingCount taskInProgressCount taskCompletedCount taskOverdueCount taskLowLoadCount'],
  ['km', 'vehicleTotalExecutedDistanceKm vehicleEmptyExecutedDistanceKm taskEmptyPickupDistanceKm taskAverageEmptyPickupDistanceKm'],
  ['t', 'vehicleRatedCapacityTonnes vehicleCurrentLoadTonnes vehicleRemainingCapacityTonnes cargoRequiredTonnes cargoUnassignedTonnes cargoAssignedNotLoadedTonnes cargoInTransitTonnes cargoDeliveredTonnes cargoUnmetTonnes cargoOverdueUntransportedTonnes'],
  ['ratio', 'vehicleEmptyMileageRatio vehicleDistanceWeightedLoadRatio vehicleLowLoadRatio vehicleFullLoadRatio cargoDeliveryAchievementRatio taskCompletionRatio taskOnTimeCompletionRatio taskAverageLoadRatio'],
  ['s', 'vehicleCumulativeWaitSeconds vehicleP95WaitSeconds cargoAverageWaitSeconds cargoP95WaitSeconds taskAverageResponseSeconds taskAverageStartWaitSeconds taskP95ServiceWaitSeconds'],
  ['t·km', 'cargoExecutedTonneKm taskTonneKm'],
  ['kgCO2e', 'vehicleTotalEmissionKg taskEmissionKg'],
  ['kgCO2e/(t·km)', 'vehicleEmissionIntensity taskEmissionIntensity'],
]) for (const id of ids.split(' ')) units[id] = unit

export function recordedDomainCompatibility(snapshot: EvaluationSnapshot, category: string): string {
  if (snapshot.contractVersion !== '1.9') return '未知历史评价契约：仅展示记录原值、单位与状态，不套用当前图表规则。'
  const prefix = ({ VEHICLE: 'vehicle', CARGO: 'cargo', TASK: 'task' } as Record<string, string>)[category]
  if (!prefix) return ''
  for (const [id, unit] of Object.entries(units)) {
    if (!id.startsWith(prefix)) continue
    const metric = snapshot.metrics[id]
    if (metric && (metric.metricId !== id || metric.category !== category || metric.unit !== unit)) {
      return `记录 ${id} 的身份、分类或单位与当前图表规则不匹配；仅展示记录值，请展开完整指标明细。`
    }
  }
  const thresholds = snapshot.thresholds
  const required = category === 'VEHICLE'
    ? [thresholds?.lowLoadRatioThreshold, thresholds?.fullLoadRatioThreshold] : [thresholds?.maxServiceWaitSeconds]
  if (required.some(value => typeof value !== 'number' || !Number.isFinite(value) || value < 0 || category === 'VEHICLE' && value > 1)) {
    return '本轮冻结阈值缺失或无效；不套用当前阈值，改为展示记录值和完整指标明细。'
  }
  return ''
}
