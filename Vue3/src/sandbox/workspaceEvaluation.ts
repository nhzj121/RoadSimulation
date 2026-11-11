import type { sandboxApi } from '../api/sandbox'
import type {
  EvaluationMetricCategory, EvaluationRunComparison, EvaluationRunSummary, EvaluationSnapshot, EvaluationTrend,
} from '../types/evaluation'

export type WorkspaceEvaluationRun = Pick<EvaluationRunSummary, 'simulationRunId' | 'firstLoopIndex' | 'lastLoopIndex' | 'snapshotCount'>
  & Partial<Pick<EvaluationRunSummary, 'runKind' | 'contractVersion' | 'latestStatus'>>

export interface WorkspaceEvaluationSource {
  preferredRunId: string
  runs(signal: AbortSignal): Promise<WorkspaceEvaluationRun[]>
  history(id: string, signal: AbortSignal): Promise<EvaluationSnapshot[]>
  trend(id: string, metrics: string[], signal: AbortSignal): Promise<EvaluationTrend>
  compare(left: string, right: string, signal: AbortSignal): Promise<EvaluationRunComparison>
}

type SandboxEvaluationApi = Pick<typeof sandboxApi, 'executions' | 'executionSummary' | 'evaluations' | 'compareExecutions'>

export async function readSandboxEvaluationRange(
  api: Pick<SandboxEvaluationApi, 'evaluations'>, id: string, from: number, to: number, signal: AbortSignal,
): Promise<EvaluationSnapshot[]> {
  const result: EvaluationSnapshot[] = []
  if (to < from) return result
  while (true) {
    signal.throwIfAborted()
    const offset = result.length
    const page = await api.evaluations(id, from, to, offset, signal)
    signal.throwIfAborted()
    if (page.artifactVersion !== 'sandbox-evaluation-page/v1' || page.executionId !== id
      || page.offset !== offset || page.fromLoopIndex !== from || page.toLoopIndex !== to
      || page.items.length === 0 || offset + page.items.length > to - from + 1) {
      throw new Error('沙箱评价历史与执行或轮次不匹配')
    }
    for (const item of page.items) {
      const snapshot = item.evaluation as unknown as EvaluationSnapshot
      if (item.loopIndex !== from + result.length || snapshot?.loopIndex !== item.loopIndex
        || !snapshot.simulationRunId || (result.length && snapshot.simulationRunId !== result[0].simulationRunId)
        || !snapshot.metrics || !snapshot.energyEmissionModel || !snapshot.thresholds) {
        throw new Error('沙箱评价快照不完整或顺序异常')
      }
      result.push(snapshot)
    }
    const remaining = result.length < to - from + 1
    if (page.hasMore !== remaining) throw new Error('沙箱评价分页不完整')
    if (!remaining) return result
  }
}

function summarize(id: string, first: EvaluationSnapshot, last: EvaluationSnapshot): EvaluationRunSummary {
  return {
    simulationRunId: id, runKind: last.runKind, contractVersion: last.contractVersion,
    firstLoopIndex: first.loopIndex, lastLoopIndex: last.loopIndex, snapshotCount: last.loopIndex - first.loopIndex + 1,
    firstSimTime: first.simTime, lastSimTime: last.simTime, latestStatus: last.snapshotStatus,
  }
}

export function createSandboxWorkspaceEvaluationSource(api: SandboxEvaluationApi, executionId: string): WorkspaceEvaluationSource {
  async function history(id: string, signal: AbortSignal) {
    const summary = await api.executionSummary(id, signal)
    if (summary.executionId !== id) throw new Error('沙箱评价执行编号不匹配')
    return readSandboxEvaluationRange(api, id, 0, summary.lastRecordedLoopIndex ?? -1, signal)
  }

  return {
    preferredRunId: executionId,
    async runs(signal) {
      const [selected, recent] = await Promise.all([api.executionSummary(executionId, signal), api.executions(0, signal)])
      if (selected.executionId !== executionId) throw new Error('沙箱评价执行编号不匹配')
      const rows = new Map(recent.items.filter(row => row.execution_status === 'COMPLETED' && row.completed_loops > 0)
        .map(row => [row.execution_id, row.completed_loops]))
      if (selected.lastRecordedLoopIndex !== null) rows.set(executionId, selected.lastRecordedLoopIndex + 1)
      return [...rows].map(([id, count]) => ({
        simulationRunId: id, firstLoopIndex: 0, lastLoopIndex: count - 1, snapshotCount: count,
      }))
    },
    history,
    async trend(id, metricIds, signal) {
      const snapshots = await history(id, signal)
      if (!snapshots.length) throw new Error('尚无已提交的沙箱评价')
      return {
        run: summarize(id, snapshots[0], snapshots[snapshots.length - 1]),
        metricIds,
        points: snapshots.map(({ loopIndex, snapshotRevision, simTime, snapshotStatus, metrics }) => ({
          loopIndex, snapshotRevision, simTime, snapshotStatus,
          metrics: Object.fromEntries(metricIds.filter(key => metrics[key]).map(key => [key, metrics[key]])),
        })),
      }
    },
    async compare(left, right, signal) {
      const result = await api.compareExecutions(left, right, signal)
      if (result.left.executionId !== left || result.right.executionId !== right) throw new Error('沙箱对比执行编号不匹配')
      const { left: a, right: b } = result
      const sideSummary = ({ executionId: id, finalSnapshot: last, clock }: typeof a) => ({
        ...summarize(id, { ...last, loopIndex: 0, simTime: clock.startLocalDateTime }, last),
      })
      return {
        leftRun: sideSummary(a), rightRun: sideSummary(b), leftFinalSnapshot: a.finalSnapshot, rightFinalSnapshot: b.finalSnapshot,
        metrics: result.metrics.map(metric => ({
          metricId: metric.metricId, displayName: metric.displayName, category: metric.category as EvaluationMetricCategory, unit: metric.unit,
          leftUnit: metric.left?.unit, rightUnit: metric.right?.unit,
          incompatibilityReason: metric.comparable ? undefined : metric.reason || 'NOT_COMPARABLE',
          leftStatus: metric.left?.status ?? 'NOT_AVAILABLE', leftValue: metric.left?.value ?? null,
          rightStatus: metric.right?.status ?? 'NOT_AVAILABLE', rightValue: metric.right?.value ?? null,
          rightMinusLeft: metric.comparable ? metric.rightMinusLeft : null,
          relativeChangeRatio: metric.comparable ? metric.relativeChangeRatio : null,
        })),
      }
    },
  }
}
