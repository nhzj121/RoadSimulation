import type { RecordedPoint } from '../types/sandbox-map'

export interface ReplayRoad {
  points: readonly RecordedPoint[]
  cumulativeMeters: readonly number[]
  lengthMeters: number
}

export function roadDistance(from: RecordedPoint, to: RecordedPoint): number {
  const radians = Math.PI / 180
  const a = Math.sin((to.latitude - from.latitude) * radians / 2) ** 2
    + Math.cos(from.latitude * radians) * Math.cos(to.latitude * radians)
    * Math.sin((to.longitude - from.longitude) * radians / 2) ** 2
  return 12_742_000 * Math.asin(Math.sqrt(Math.min(1, a)))
}

export function measureReplayRoad(points: readonly RecordedPoint[]): ReplayRoad {
  if (points.length < 2 || points.some(({ longitude, latitude }) =>
    !Number.isFinite(longitude) || !Number.isFinite(latitude) || Math.abs(longitude) > 180 || Math.abs(latitude) > 90)) {
    throw new Error('INVALID_REPLAY_ROAD')
  }
  const cumulativeMeters = [0]
  for (let index = 1; index < points.length; index++) {
    cumulativeMeters.push(cumulativeMeters[index - 1] + roadDistance(points[index - 1], points[index]))
  }
  const lengthMeters = cumulativeMeters[cumulativeMeters.length - 1]
  if (lengthMeters <= 0) throw new Error('INVALID_REPLAY_ROAD')
  return { points, cumulativeMeters, lengthMeters }
}

export function replayRoadPosition(road: ReplayRoad, meters: number): RecordedPoint {
  if (meters <= 0) return road.points[0]
  if (meters >= road.lengthMeters) return road.points[road.points.length - 1]

  let lower = 0
  let upper = road.points.length - 1
  while (upper - lower > 1) {
    const middle = Math.floor((lower + upper) / 2)
    if (road.cumulativeMeters[middle] <= meters) lower = middle
    else upper = middle
  }
  const ratio = (meters - road.cumulativeMeters[lower]) / (road.cumulativeMeters[upper] - road.cumulativeMeters[lower])
  const from = road.points[lower]
  const to = road.points[upper]
  return {
    longitude: from.longitude + (to.longitude - from.longitude) * ratio,
    latitude: from.latitude + (to.latitude - from.latitude) * ratio,
  }
}
