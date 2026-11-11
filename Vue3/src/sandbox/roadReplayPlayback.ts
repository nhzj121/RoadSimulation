import { computed, ref, shallowRef } from 'vue'
import type { SandboxExecutionSummary } from '../types/sandbox'
import type { RoadReplayTick } from '../types/sandbox-road-replay'
import { SandboxApiError } from '../api/sandbox'
import type { sandboxApi } from '../api/sandbox'
import { measureReplayRoad } from './roadReplayGeometry'
import type { ReplayRoad } from './roadReplayGeometry'
import { buildRoadReplayTimeline } from './roadReplayTimeline'
import { closedExecution } from './results'
import { buildReplayOwnership, extendAssignmentHistory } from './replayAssignments'
import type { AssignmentHistory } from './replayAssignments'

type ReplayApi = Pick<typeof sandboxApi, 'roadReplayTick' | 'prepareRoadReplayRoute' | 'roadReplayAssignments'>

export function createRoadReplayPlayback(api: ReplayApi) {
  const timeline = shallowRef<ReturnType<typeof buildRoadReplayTimeline> | null>(null)
  const roads = shallowRef<ReadonlyMap<number, ReplayRoad>>(new Map())
  const replayRecord = shallowRef<RoadReplayTick | null>(null)
  const seconds = ref(0), speed = ref(1), playing = ref(false), preparing = ref(false), error = ref('')
  const active = ref(false), terminal = ref(false)
  const progress = ref('')
  const ownership = shallowRef<ReturnType<typeof buildReplayOwnership> | null>(null)
  const frame = computed(() => timeline.value && ownership.value
    ? ownership.value(seconds.value, timeline.value.frameAt(seconds.value)) : null)
  const activeLegs = computed(() => [...new Set((frame.value ?? []).flatMap(vehicle =>
    vehicle.replayLegId === undefined ? [] : [vehicle.replayLegId]))].sort((a,b)=>a-b).join(','))
  const visibleRoads = computed(() => new Map(activeLegs.value.split(',').filter(Boolean).flatMap(id => {
    const legId = Number(id), road = roads.value.get(legId)
    return road ? [[legId, road] as const] : []
  })))
  const duration = computed(() => timeline.value?.availableSeconds ?? 0)
  const waiting = computed(() => active.value && !terminal.value && playing.value && seconds.value >= duration.value)
  let pending: AbortController | undefined
  let queued: SandboxExecutionSummary | undefined
  let first: RoadReplayTick | undefined
  let assignmentHistory: AssignmentHistory | null = null
  let identity: string | undefined
  const byEndpoints = new Map<string, ReplayRoad>()
  let animation: number | undefined
  let lastTime: number | undefined
  let viewedThrough = 0

  function pause() {
    playing.value = false
    if (animation !== undefined) cancelAnimationFrame(animation)
    animation = lastTime = undefined
  }

  function reset() {
    pause()
    pending?.abort()
    pending = undefined
    queued = first = identity = undefined
    byEndpoints.clear()
    active.value = terminal.value = false
    timeline.value = null
    replayRecord.value = null
    ownership.value = assignmentHistory = null
    roads.value = new Map()
    seconds.value = viewedThrough = 0
    error.value = progress.value = ''
    preparing.value = false
  }

  function advance(now: number) {
    animation = undefined
    if (!playing.value) return
    // Returning from a suspended tab should resume at the previous position.
    if (lastTime !== undefined && now - lastTime < 250) {
      seconds.value = Math.min(duration.value, seconds.value + (now - lastTime) * speed.value / 1000)
      viewedThrough = Math.max(viewedThrough, seconds.value)
    }
    lastTime = now
    schedule()
  }

  function schedule() {
    if (!playing.value || animation !== undefined) return
    if (seconds.value < duration.value) animation = requestAnimationFrame(advance)
    else if (terminal.value) pause()
    else lastTime = undefined
  }

  function play() {
    if (!timeline.value || playing.value || (terminal.value && duration.value === 0)) return
    if (terminal.value && seconds.value >= duration.value) seconds.value = 0
    playing.value = true
    schedule()
  }

  function seek(value: number) {
    if (!Number.isFinite(value)) return
    seconds.value = Math.max(0, Math.min(duration.value, value))
    viewedThrough = Math.max(viewedThrough, seconds.value)
    schedule()
  }

  async function prepare(summary: SandboxExecutionSummary) {
    reset()
    identity = JSON.stringify([summary.executionId, summary.manifestSha256])
    active.value = true
    await refresh(summary)
  }

  async function refresh(summary: SandboxExecutionSummary) {
    if (!active.value || summary.lastRecordedLoopIndex === null || summary.status === 'INTERRUPTED'
      || identity !== JSON.stringify([summary.executionId, summary.manifestSha256])) return
    const closed = closedExecution(summary.status)
    if (!error.value && replayRecord.value?.loopIndex === summary.lastRecordedLoopIndex
      && replayRecord.value.tickSha256 === summary.lastTickSha256 && terminal.value === closed
      && !timeline.value?.missingRoads.length) return
    if (pending) {
      queued = summary
      return
    }
    const request = new AbortController()
    const signal = request.signal
    pending = request
    preparing.value = true
    try {
      const { executionId, manifestSha256, lastRecordedLoopIndex, lastTickSha256 } = summary
      const initial = first ?? await api.roadReplayTick(executionId, 0, signal)
      const last = lastRecordedLoopIndex === 0 ? initial : await api.roadReplayTick(executionId, lastRecordedLoopIndex, signal)
      if (signal.aborted) return
      if ([initial, last].some(tick => tick.artifactVersion !== 'sandbox-road-replay-tick/v1'
        || tick.executionId !== executionId || tick.manifestSha256 !== manifestSha256)
        || initial.loopIndex !== 0 || last.loopIndex !== lastRecordedLoopIndex || last.tickSha256 !== lastTickSha256
        || (replayRecord.value && (last.loopIndex < replayRecord.value.loopIndex
          || (last.loopIndex === replayRecord.value.loopIndex && last.tickSha256 !== replayRecord.value.tickSha256)))) {
        throw new Error('回放记录与执行身份不一致')
      }
      if (!last.facts.fragments) throw new Error('执行缺少驾驶片段，只能查看逐轮记录。')
      const history = await extendAssignmentHistory(api, initial, last, assignmentHistory, signal)
      const prepared = new Map<number, ReplayRoad>()
      first = initial
      const legs = last.facts.legs.filter(leg => leg.startedSimTime && leg.distanceMeters !== null
        && leg.distanceMeters > 0 && leg.from && leg.to)
      for (const [index, leg] of legs.entries()) {
        if (signal.aborted) return
        const endpoints = [leg.from?.longitude, leg.from?.latitude, leg.to?.longitude, leg.to?.latitude].join(',')
        try {
          let road = byEndpoints.get(endpoints)
          if (!road) {
            progress.value = `正在准备路线 ${index + 1} / ${legs.length}`
            const response = await api.prepareRoadReplayRoute(executionId, last.loopIndex, leg.id, signal)
            if (signal.aborted) return
            if (response.artifactVersion !== 'sandbox-gaode-road/v1' || !/^[a-f0-9]{64}$/.test(response.routeKey)) {
              throw new Error('道路回放路线身份无效')
            }
            road = measureReplayRoad(response.points)
            byEndpoints.set(endpoints, road)
          }
          prepared.set(leg.id, road)
        } catch (failure) {
          if (signal.aborted) return
          if (!(failure instanceof SandboxApiError) || !['REPLAY_ROAD_UNAVAILABLE', 'REPLAY_ROAD_NOT_FOUND'].includes(failure.code)) throw failure
        }
      }
      if (signal.aborted) return
      const next = buildRoadReplayTimeline(initial, last, prepared, closed)
      const nextOwnership = buildReplayOwnership(history, initial, last)
      if (timeline.value && (next.availableSeconds < viewedThrough || !next.preservesPrefix(timeline.value, viewedThrough))) {
        throw new Error('新增记录与已展示过程不一致。已保留原画面，可返回逐轮地图核对。')
      }
      roads.value = prepared
      replayRecord.value = last
      assignmentHistory = history
      ownership.value = nextOwnership
      timeline.value = next
      terminal.value = closed
      error.value = ''
      schedule()
    } catch (failure) {
      if (!signal.aborted) error.value = failure instanceof Error ? failure.message : '无法准备道路回放'
    } finally {
      if (pending === request) {
        preparing.value = false
        progress.value = ''
        pending = undefined
        const next = queued
        queued = undefined
        if (next && (next.lastTickSha256 !== summary.lastTickSha256 || next.status !== summary.status)) void refresh(next)
      }
    }
  }

  return { timeline, roads, visibleRoads, replayRecord, frame, seconds, speed, playing, preparing, error, progress, duration,
    active, terminal, waiting, play, pause, seek, prepare, refresh, reset }
}
