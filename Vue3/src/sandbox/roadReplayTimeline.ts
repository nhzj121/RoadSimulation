import type { RecordedPositionSource, RecordedVehicle } from '../types/sandbox-map'
import type { RoadReplayFragment, RoadReplayTick } from '../types/sandbox-road-replay'
import { replayRoadPosition, roadDistance } from './roadReplayGeometry'
import type { ReplayRoad } from './roadReplayGeometry'
import { recordedReplayInterruptions } from './roadReplayInterruptions'

export type ReplayVehicle = RecordedVehicle<RecordedPositionSource | 'GAODE_REPLAY'> & {
  replayAssignmentId?: number
  replayLegId?: number
}

interface Phase {
  from: number
  vehicle: ReplayVehicle
  motion?: { road: ReplayRoad; until: number; startMeters: number; endMeters: number }
}

interface Track {
  initial: ReplayVehicle
  phases: Phase[]
}

export interface RoadReplayTimeline {
  durationSeconds: number
  availableSeconds: number
  missingRoads: number[]
  disconnectedRoads: number[]
  simTimeAt(seconds: number): string
  frameAt(seconds: number): ReplayVehicle[]
  preservesPrefix(previous: RoadReplayTimeline, through: number): boolean
}

export function replayTime(value: string): number {
  // Use UTC for arithmetic on zone-free simulation timestamps.
  const seconds = Date.parse(value + 'Z') / 1000
  if (!Number.isFinite(seconds)) throw new Error('回放记录的仿真时间无效')
  return seconds
}

function frameAt(track: Track, seconds: number): ReplayVehicle {
  let lower = 0, upper = track.phases.length
  while (lower < upper) {
    const middle = Math.floor((lower + upper) / 2)
    if (track.phases[middle].from <= seconds) lower = middle + 1
    else upper = middle
  }
  const phase = track.phases[lower - 1]
  if (!phase) return track.initial
  const motion = phase.motion
  if (!motion) return phase.vehicle
  const fraction = Math.min(1, (seconds - phase.from) / (motion.until - phase.from))
  const meters = motion.startMeters + (motion.endMeters - motion.startMeters) * fraction
  return { ...phase.vehicle, position: replayRoadPosition(motion.road, meters) }
}

const replayTracks = new WeakMap<RoadReplayTimeline, ReadonlyMap<number, Track>>()

export function buildRoadReplayTimeline(
  first: RoadReplayTick, last: RoadReplayTick, roads: ReadonlyMap<number, ReplayRoad>, terminal: boolean,
): RoadReplayTimeline {
  if (first.loopIndex !== 0 || !first.initialVehicles || first.executionId !== last.executionId
    || first.manifestSha256 !== last.manifestSha256 || last.loopIndex < 0) throw new Error('回放记录与执行身份不一致')
  if (!last.facts.fragments) throw new Error('执行缺少驾驶片段，只能查看逐轮记录。')

  const zero = replayTime(first.tickStart), horizon = replayTime(last.tickEnd)
  const durationSeconds = horizon - zero
  if (durationSeconds <= 0) throw new Error('回放记录时间范围无效')
  const tracks = new Map<number, Track>(first.initialVehicles.map(vehicle => [vehicle.id, { initial: vehicle, phases: [] }]))
  for (const vehicle of last.facts.vehicles) {
    if (!tracks.has(vehicle.id)) tracks.set(vehicle.id, {
      initial: { ...vehicle, currentStatus: null, currentLoad: null, currentVolumn: null, position: null, positionSource: 'UNKNOWN' },
      phases: [],
    })
  }

  const nodes = new Map(last.facts.nodes.map(node => [node.id, node]))
  const legs = new Map(last.facts.legs.map(leg => [leg.id, leg]))
  const fragments = new Map<number, RoadReplayFragment[]>()
  for (const fragment of last.facts.fragments) {
    if (!legs.has(fragment.legId)) throw new Error('驾驶片段缺少关联运输段，请通过逐轮地图查看。')
    const group = fragments.get(fragment.legId) ?? []
    group.push(fragment)
    fragments.set(fragment.legId, group)
  }

  const missingRoads = new Set<number>(), disconnectedRoads = new Set<number>()
  const interruptions = recordedReplayInterruptions(last.facts)
  if (interruptions.unavailable.length) throw new Error('故障记录缺少可核实的道路停点，请通过逐轮地图查看。')
  const actions: Array<{ at: number; priority: number; apply: () => void }> = []
  function at(time: number, priority: number, apply: () => void) {
    if (time < zero || time > horizon) throw new Error('回放事件超出已提交时间范围')
    actions.push({ at: time, priority, apply })
  }
  function update(id: number, time: number, change: (vehicle: ReplayVehicle) => ReplayVehicle, motion?: Phase['motion']) {
    const track = tracks.get(id)
    if (!track) throw new Error('回放事件缺少车辆记录')
    track.phases.push({ from: time - zero, vehicle: change(frameAt(track, time - zero)), motion })
  }

  for (const leg of legs.values()) {
    const pieces = (fragments.get(leg.id) ?? []).map(fragment => ({
      fragment, from: replayTime(fragment.fromSimTime), to: replayTime(fragment.toSimTime),
    })).sort((a, b) => a.from - b.from || a.to - b.to)
    if (!leg.startedSimTime || leg.progressStatus === 'PENDING') {
      if (pieces.length) throw new Error('未开始的运输段存在驾驶片段')
      continue
    }
    const started = replayTime(leg.startedSimTime)
    const completed = leg.completedSimTime ? replayTime(leg.completedSimTime) : null
    const planned = leg.distanceMeters, executed = leg.executedDistanceMeters
    if (planned === null || executed === null || !Number.isFinite(planned) || !Number.isFinite(executed)
      || planned < 0 || executed < 0 || executed > planned + 1e-6
      || (leg.progressStatus === 'COMPLETED' && (completed === null || Math.abs(executed - planned) > 1e-6))) {
      throw new Error('运输段距离或完成时间无效，请通过逐轮地图查看。')
    }
    const road = roads.get(leg.id)
    if (planned > 0 && !road) missingRoads.add(leg.id)
    const meters = (distance: number) => road && planned > 0 ? road.lengthMeters * Math.min(1, distance / planned) : 0
    let distance = 0, lastEnd = started
    for (const [index, { fragment, from, to }] of pieces.entries()) {
      if (from < lastEnd || to <= from || !Number.isFinite(fragment.distanceMeters) || fragment.distanceMeters < 0
        || (completed !== null && to > completed)) throw new Error('驾驶片段存在重叠或无效时间，请通过逐轮地图查看。')
      const before = distance
      distance += fragment.distanceMeters
      lastEnd = to
      const startMeters = meters(before), endMeters = meters(distance)
      const owner = fragment.vehicleId
      at(from, 30, () => update(owner, from, previous => {
        const position = road ? replayRoadPosition(road, startMeters) : planned === 0 ? previous.position ?? leg.from : null
        if (road && previous.position && roadDistance(previous.position, position!) > 2) disconnectedRoads.add(leg.id)
        return {
          ...previous, position, positionSource: road ? 'GAODE_REPLAY' : planned === 0 ? previous.positionSource : 'UNKNOWN',
          currentStatus: leg.loadState === 'LOADED' ? 'TRANSPORT_DRIVING' : 'ORDER_DRIVING',
          currentLoad: leg.currentLoadTonnes, replayAssignmentId: leg.assignmentId, replayLegId: leg.id,
        }
      }, road ? { road, until: to - zero, startMeters, endMeters } : undefined))
      if (to < horizon && to !== completed && pieces[index + 1]?.from !== to) {
        at(to, 0, () => update(owner, to, previous => ({ ...previous, currentStatus: 'WAITING' })))
      }
    }
    if (Math.abs(distance - executed) > 1e-6) throw new Error('驾驶片段未覆盖已记录距离，只能查看逐轮记录。')
    const lastOwner = pieces[pieces.length - 1]?.fragment.vehicleId ?? leg.vehicleId
    if (completed !== null && lastOwner !== null) {
      at(completed, 0, () => update(lastOwner, completed, previous => ({
        ...previous, position: road?.points[road.points.length - 1] ?? leg.to ?? previous.position,
        positionSource: road ? 'GAODE_REPLAY' : leg.to ? 'POI_LINK' : previous.positionSource,
        currentPoiId: leg.toPoiId, currentStatus: 'WAITING', replayAssignmentId: leg.assignmentId, replayLegId: undefined,
      })))
    }
    for (const interruption of interruptions.byLeg.get(leg.id) ?? []) {
      const event = interruption.event, start = replayTime(event.startTime)
      const point = road ? replayRoadPosition(road, interruption.fraction * road.lengthMeters) : null
      const stopped = { position: point, positionSource: road ? 'GAODE_REPLAY' : 'UNKNOWN', replayAssignmentId: leg.assignmentId,
        replayLegId: leg.id } as const
      at(start, 20, () => update(event.vehicleId, start, previous => ({ ...previous, ...stopped, currentStatus: 'BREAKDOWN' })))
      if (interruption.resumedAt) {
        const resumed = replayTime(interruption.resumedAt), replacement = interruption.nextVehicleId
        at(resumed, 10, () => {
          if (replacement === null) {
            update(event.vehicleId, resumed, previous => ({ ...previous, currentStatus: 'WAITING' }))
            return
          }
          const retired = tracks.get(event.vehicleId)
          if (!retired) throw new Error('换车记录缺少原车辆')
          const load = frameAt(retired, resumed - zero)
          update(event.vehicleId, resumed, previous => ({
            ...previous, currentStatus: 'SCRAPPED', position: null, positionSource: 'UNKNOWN',
            replayAssignmentId: undefined, replayLegId: undefined,
          }))
          update(replacement, resumed, previous => ({
            ...previous, ...stopped, currentStatus: 'WAITING', currentLoad: load.currentLoad, currentVolumn: load.currentVolumn,
          }))
        })
      }
    }
  }

  for (const service of last.facts.services ?? []) {
    const start = replayTime(service.serviceStartedAt)
    at(start, 21, () => update(service.vehicleId, start, previous => ({
      ...previous, currentStatus: service.actionType === 'LOAD' ? 'LOADING' : 'UNLOADING',
      replayAssignmentId: service.assignmentId, replayLegId: undefined,
    })))
    if (service.serviceCompletedAt) {
      const end = replayTime(service.serviceCompletedAt)
      const node = service.assignmentNodeId === null ? undefined : nodes.get(service.assignmentNodeId)
      const adjust = (value: number | null, delta: unknown) =>
        value !== null && typeof delta === 'number' && Number.isFinite(delta) ? Math.max(0, value + delta) : null
      at(end, 1, () => update(service.vehicleId, end, previous => ({
        ...previous, currentStatus: 'WAITING', currentLoad: adjust(previous.currentLoad, node?.weightDelta),
        currentVolumn: adjust(previous.currentVolumn, node?.volumeDelta),
      })))
    }
  }

  actions.sort((a, b) => a.at - b.at || a.priority - b.priority).forEach(action => action.apply())
  if (terminal) for (const vehicle of last.facts.vehicles) {
    update(vehicle.id, horizon, previous => ({
      ...previous, ...vehicle, position: previous.position, positionSource: previous.positionSource,
    }))
  }

  const clamp = (seconds: number) => Math.max(0, Math.min(seconds, durationSeconds))
  const timeline: RoadReplayTimeline = {
    durationSeconds, availableSeconds: durationSeconds, missingRoads: [...missingRoads], disconnectedRoads: [...disconnectedRoads],
    simTimeAt: seconds => new Date((zero + clamp(seconds)) * 1000).toISOString().slice(0, 19),
    frameAt: seconds => [...tracks.values()].map(track => frameAt(track, clamp(seconds))),
    preservesPrefix(previous, through) {
      const old = replayTracks.get(previous)
      return !!old && sameReplayPrefix(old, tracks, through)
    },
  }
  replayTracks.set(timeline, tracks)
  return timeline
}

function sameReplayPrefix(before: ReadonlyMap<number, Track>, after: ReadonlyMap<number, Track>, through: number) {
  for (const [id, old] of before) {
    const next = after.get(id)
    if (!next) return false
    const cuts = [...new Set([0, through, ...[...old.phases, ...next.phases]
      .map(phase => phase.from).filter(time => time < through)])].sort((a, b) => a - b)
    const state = (vehicle: ReplayVehicle) => [vehicle.currentStatus, vehicle.currentLoad, vehicle.currentVolumn,
      vehicle.replayAssignmentId, vehicle.replayLegId]
    for (let index = 0; index + 1 < cuts.length; index++) {
      const time = cuts[index], end = cuts[index + 1]
      for (const sample of [time, (time + end) / 2, end - Math.min((end - time) / 2, 1e-5)]) {
        const previous = frameAt(old, sample), current = frameAt(next, sample)
        if (JSON.stringify(state(previous)) !== JSON.stringify(state(current))) return false
        if (previous.position && (!current.position || roadDistance(previous.position, current.position) > 1e-4)) return false
      }
    }
  }
  return true
}
