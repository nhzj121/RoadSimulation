import type { sandboxApi } from '../api/sandbox'
import type { RecordedRow, SandboxMapSnapshot } from '../types/sandbox-map'
import type { RoadReplayAssignment, RoadReplayAssignmentTick, RoadReplayTick } from '../types/sandbox-road-replay'
import { replayTime } from './roadReplayTimeline'
import type { ReplayVehicle } from './roadReplayTimeline'

export function hasAssignedVehicle<T extends RecordedRow>(row: T): row is T & { vehicleId: number } {
  return typeof row.vehicleId === 'number' && ['ASSIGNED', 'IN_PROGRESS', 'DELAYED'].includes(String(row.status))
}

export function assignedMapSnapshot(snapshot: SandboxMapSnapshot): SandboxMapSnapshot {
  const assignments = new Map(snapshot.projection.assignments.filter(hasAssignedVehicle).map(row => [row.id, row.vehicleId]))
  const vehicles = new Set(assignments.values())
  return { ...snapshot, projection: { ...snapshot.projection,
    vehicles: snapshot.projection.vehicles.filter(vehicle => vehicles.has(vehicle.id)),
    legs: snapshot.projection.legs.filter(leg => leg.vehicleId !== null && assignments.get(leg.assignmentId) === leg.vehicleId
      && leg.startedSimTime && !['COMPLETED', 'PENDING'].includes(leg.progressStatus ?? '')),
  } }
}

export interface AssignmentHistory {
  initial: RoadReplayAssignment[]
  ticks: RoadReplayAssignmentTick[]
}

export async function extendAssignmentHistory(
  api: Pick<typeof sandboxApi, 'roadReplayAssignments'>, first: RoadReplayTick, last: RoadReplayTick,
  previous: AssignmentHistory | null, signal: AbortSignal,
): Promise<AssignmentHistory> {
  const ticks = [...(previous?.ticks ?? [])]
  let initial = previous?.initial
  const from = ticks.length, to = last.loopIndex
  while (ticks.length <= to) {
    const offset = ticks.length - from
    const page = await api.roadReplayAssignments(last.executionId, from, to, offset, signal)
    signal.throwIfAborted()
    if (page.artifactVersion !== 'sandbox-road-replay-assignments/v1' || page.executionId !== last.executionId
      || page.manifestSha256 !== last.manifestSha256 || page.fromLoopIndex !== from || page.toLoopIndex !== to
      || page.offset !== offset || !page.items.length || ticks.length + page.items.length > to + 1) {
      throw new Error('沙箱任务归属记录与执行或轮次不匹配')
    }
    if (from === 0 && offset === 0 && Array.isArray(page.initialAssignments)) initial = page.initialAssignments
    for (const item of page.items) {
      const previousEnd = ticks[ticks.length - 1]?.tickEnd ?? first.tickStart
      if (item.loopIndex !== ticks.length || !Array.isArray(item.assignments)
        || replayTime(item.tickStart) !== replayTime(previousEnd) || replayTime(item.tickEnd) <= replayTime(item.tickStart)) {
        throw new Error('沙箱任务归属记录缺失或轮次不连续，请通过逐轮地图查看。')
      }
      ticks.push(item)
    }
    if (page.hasMore !== (ticks.length <= to)) throw new Error('沙箱任务归属分页不完整')
  }
  if (!initial || ticks.length !== to + 1 || ticks[0]?.tickSha256 !== first.tickSha256 || ticks[to]?.tickSha256 !== last.tickSha256
    || replayTime(ticks[to].tickEnd) !== replayTime(last.tickEnd)) throw new Error('沙箱任务归属与回放账本不一致')
  return { initial, ticks }
}

interface Ownership { assignmentId: number; vehicleId: number; from: number; to: number }

export function buildReplayOwnership(history: AssignmentHistory, first: RoadReplayTick, last: RoadReplayTick) {
  const zero = replayTime(first.tickStart)
  const open = new Map<number, Ownership>()
  const intervals: Ownership[] = []
  for (const assignment of history.initial.filter(hasAssignedVehicle)) {
    open.set(assignment.id, { assignmentId: assignment.id, vehicleId: assignment.vehicleId, from: zero, to: Infinity })
  }

  const handoffs = new Map<number, Array<{ from: number; to: number; time: number }>>()
  for (const handoff of last.facts.handoffs ?? []) {
    if (handoff.status !== 'COMPLETED' || !handoff.handoffTime) continue
    const entries = handoffs.get(handoff.assignmentId) ?? []
    entries.push({ from: handoff.originalVehicleId, to: handoff.replacementVehicleId, time: replayTime(handoff.handoffTime) })
    handoffs.set(handoff.assignmentId, entries)
  }
  const starts = new Map<number, Map<number, number>>()
  function rememberStart(assignmentId: number, vehicleId: number, value: string) {
    const byVehicle = starts.get(assignmentId) ?? new Map<number, number>()
    const time = replayTime(value)
    byVehicle.set(vehicleId, Math.min(time, byVehicle.get(vehicleId) ?? Infinity))
    starts.set(assignmentId, byVehicle)
  }
  for (const leg of last.facts.legs) if (leg.startedSimTime && leg.vehicleId !== null) rememberStart(leg.assignmentId, leg.vehicleId, leg.startedSimTime)
  for (const service of last.facts.services ?? []) rememberStart(service.assignmentId, service.vehicleId, service.serviceStartedAt)
  const assignmentByLeg = new Map(last.facts.legs.map(leg => [leg.id, leg.assignmentId]))
  for (const fragment of last.facts.fragments ?? []) {
    const id = assignmentByLeg.get(fragment.legId)
    if (id !== undefined) rememberStart(id, fragment.vehicleId, fragment.fromSimTime)
  }

  for (const tick of history.ticks) {
    const start = replayTime(tick.tickStart), end = replayTime(tick.tickEnd)
    const known = (value: string | null | undefined) => value ? replayTime(value) : NaN
    const inTick = (time: number) => time >= start && time <= end
    const rows = new Map((tick.assignments ?? []).map(row => [row.id, row]))
    const ids = new Set([...open.keys(), ...rows.keys()])
    for (const id of ids) {
      const row = rows.get(id)
      const current = row && hasAssignedVehicle(row) ? row.vehicleId : null
      let prior = open.get(id)
      const terminal = row && ['COMPLETED', 'FAILED', 'CANCELLED'].includes(String(row.status))
      const transfers = (handoffs.get(id) ?? []).filter(event => inTick(event.time)).sort((a, b) => a.time - b.time)
      const initialOwner = transfers[0]?.from ?? row?.vehicleId
      const recordedStart = Math.min(...[known(row?.startTime),
        starts.get(id)?.get(initialOwner ?? -1) ?? NaN].filter(inTick))
      const recordedEnd = known(row?.endTime)
      if (!prior && row?.vehicleId != null && (current !== null || terminal && inTick(recordedStart) && inTick(recordedEnd) && recordedStart < recordedEnd)) {
        const from = Math.min(recordedStart, transfers[0]?.time ?? Infinity)
        prior = { assignmentId: id, vehicleId: initialOwner ?? row.vehicleId, from: inTick(from) ? from : end, to: Infinity }
      }
      if (prior) {
        for (const handoff of transfers) {
          if (prior.vehicleId !== handoff.from || handoff.time < prior.from) continue
          intervals.push({ ...prior, to: handoff.time })
          prior = { assignmentId: id, vehicleId: handoff.to, from: handoff.time, to: Infinity }
        }
        if (prior.vehicleId !== current) {
          const closeAt = terminal && inTick(recordedEnd) ? Math.max(prior.from, recordedEnd) : end
          intervals.push({ ...prior, to: closeAt })
          prior = current === null ? undefined : { assignmentId: id, vehicleId: current, from: end, to: Infinity }
        }
      }
      if (prior) open.set(id, prior)
      else open.delete(id)
    }
  }
  intervals.push(...open.values())
  const byVehicle = new Map<number, Ownership[]>()
  for (const interval of intervals) {
    const entries = byVehicle.get(interval.vehicleId) ?? []
    entries.push(interval)
    byVehicle.set(interval.vehicleId, entries)
  }
  return (seconds: number, vehicles: readonly ReplayVehicle[]) => vehicles.flatMap(vehicle => {
    const time = zero + seconds
    const active = (byVehicle.get(vehicle.id) ?? []).filter(item => item.from <= time && time < item.to)
    if (!active.length) return []
    const selected = active.find(item => item.assignmentId === vehicle.replayAssignmentId)
    return [{
      ...vehicle, replayAssignmentId: (selected ?? active[0]).assignmentId, replayLegId: selected ? vehicle.replayLegId : undefined,
      currentStatus: vehicle.currentStatus === 'IDLE' ? 'WAITING' : vehicle.currentStatus,
    }]
  })
}
