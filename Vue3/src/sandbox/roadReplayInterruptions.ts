import type { RoadReplayEvent, RoadReplayTick } from '../types/sandbox-road-replay'

export interface ReplayInterruption {
  event: RoadReplayEvent
  fraction: number
  resumedAt: string | null
  nextVehicleId: number | null
}

/** Joins recorded interruptions to a particular road leg without guessing a roadside location. */
export function recordedReplayInterruptions(facts: RoadReplayTick['facts']) {
  const byLeg = new Map<number, ReplayInterruption[]>()
  const unavailable: number[] = []
  const legs = new Map(facts.legs.map(leg => [`${leg.assignmentId}:${leg.sequenceIndex}`, leg]))
  const fragments = new Map<number, NonNullable<typeof facts.fragments>>()
  for (const fragment of facts.fragments ?? []) {
    const entries = fragments.get(fragment.legId) ?? []
    entries.push(fragment)
    fragments.set(fragment.legId, entries)
  }
  const handoffs = new Map((facts.handoffs ?? []).filter(handoff =>
    handoff.status === 'COMPLETED' && handoff.handoffTime).map(handoff => [handoff.eventId, handoff]))
  const breakdowns = (facts.events ?? []).filter(event => event.eventType === 'VEHICLE_BREAKDOWN')
    .sort((a, b) => a.startTime.localeCompare(b.startTime) || a.id - b.id)

  for (const event of breakdowns) {
    const leg = legs.get(`${event.assignmentId}:${event.originalLegIndex}`)
    const handoff = handoffs.get(event.id)
    if (!leg || !leg.startedSimTime || !leg.distanceMeters || leg.executedDistanceMeters === null
      || (event.replacementOutcome === 'REPLACED' && (!handoff || handoff.originalVehicleId !== event.vehicleId
        || handoff.assignmentId !== event.assignmentId))) {
      unavailable.push(event.id)
      continue
    }
    let total = 0
    let before = 0
    let straddles = false
    for (const fragment of fragments.get(leg.id) ?? []) {
      total += fragment.distanceMeters
      if (fragment.toSimTime <= event.startTime) before += fragment.distanceMeters
      else if (fragment.fromSimTime < event.startTime && fragment.distanceMeters > 0) straddles = true
    }
    if (straddles || Math.abs(total - leg.executedDistanceMeters) > 1e-6
      || before < 0 || before > leg.distanceMeters + 1e-6) {
      unavailable.push(event.id)
      continue
    }
    const entries = byLeg.get(leg.id) ?? []
    entries.push({
      event,
      fraction: Math.min(1, before / leg.distanceMeters),
      resumedAt: handoff?.handoffTime ?? (event.recoveryOutcome === 'RESTORED' ? event.resolvedTime : null),
      nextVehicleId: handoff?.replacementVehicleId ?? null,
    })
    byLeg.set(leg.id, entries)
  }
  return { byLeg, unavailable }
}
