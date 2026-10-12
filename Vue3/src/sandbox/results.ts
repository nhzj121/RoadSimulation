import type { EvaluationMetricValue, EvaluationSnapshot } from '../types/evaluation'
import type { SandboxExecutionSummary } from '../types/sandbox'
export const objectiveIds = ['emptyMileageRatio', 'capacityWasteRatio', 'unmetDemandRatio', 'carbonIntensity']
export const closedExecution = (status: string) => ['COMPLETED', 'CANCELLED', 'FAILED'].includes(status)
export const terminalExecution = (status: string) => closedExecution(status) || status === 'INTERRUPTED'
export const resultLabel = (kind: string) => ({FULL_COMPLETION:'完整完成', CANCELLED_PREFIX:'取消 · 部分结果', FAILED_PREFIX:'失败 · 部分结果', INTERRUPTED_PREFIX:'异常中断 · 需要人工检查', IN_PROGRESS_PREFIX:'运行中 · 已记录前缀'}[kind] || kind)
export function latestWindow(recorded: number): [number, number] | null { return recorded > 0 ? [Math.max(0, recorded - 200), recorded - 1] : null }
export function verificationBinding(s: SandboxExecutionSummary): string { return JSON.stringify([s.executionId, s.manifestSha256, s.lastTickSha256, s.progress.recordedLoops]) }
export function formatRecordedMetric(metric: EvaluationMetricValue | null | undefined, raw = false): string {
  if (!metric || metric.status !== 'AVAILABLE' || metric.value === null || !Number.isFinite(metric.value)) return '--'
  if (raw) return `${metric.value} ${metric.unit}`
  if (metric.unit === 'boolean') return metric.value >= .5 ? '满足' : '不满足'
  const format = (v: number, digits: number) => new Intl.NumberFormat('zh-CN', {minimumFractionDigits:0, maximumFractionDigits:digits}).format(v)
  if (metric.unit === 'ratio') return `${format(metric.value * 100, 2)}%`
  return `${format(metric.value, ['vehicle', 'task', 'road', 'event'].includes(metric.unit) ? 0 : 2)} ${metric.unit}`
}
/** Missing values break lines; incompatible units/contracts never join. No smoothing or sampling. */
export function trendSegments(snapshots: EvaluationSnapshot[], id: string): string[] {
  const values = snapshots.map(s => s.metrics[id]?.status === 'AVAILABLE' ? s.metrics[id].value : null)
  const finite = values.filter((v): v is number => v !== null && Number.isFinite(v))
  if (!finite.length) return []
  const min = finite.reduce((a,b)=>Math.min(a,b)), max = finite.reduce((a,b)=>Math.max(a,b)), segments: string[] = []
  let points: string[] = [], previousContract = '', previousUnit = ''
  snapshots.forEach((s, i) => {
    const value = values[i], unit = s.metrics[id]?.unit || ''
    if (value === null || !Number.isFinite(value) || (points.length && (s.contractVersion !== previousContract || unit !== previousUnit))) {
      if (points.length) segments.push(points.join(' ')); points = []
    }
    if (value !== null && Number.isFinite(value)) points.push(`${20 + i * 360 / Math.max(1, snapshots.length - 1)},${max === min ? 60 : 100 - (value - min) * 80 / (max - min)}`)
    previousContract = s.contractVersion; previousUnit = unit
  })
  if (points.length) segments.push(points.join(' '))
  return segments
}
