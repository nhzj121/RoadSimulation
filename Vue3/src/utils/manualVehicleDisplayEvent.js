export function createDisplayEvent({ vehicleId, assignmentId, licensePlate, repair, minutes }) {
  const maxMinutes = repair ? 180 : 240
  if (typeof minutes !== 'number' || !Number.isInteger(minutes) || minutes < 30 || minutes > maxMinutes) {
    throw new Error(`持续时长须为 30–${maxMinutes} 的整数分钟`)
  }
  const durationSeconds = minutes * 60
  return {
    eventId: crypto.randomUUID(), vehicleId, assignmentId, licensePlate, displayOnly: true,
    eventType: repair ? 'VEHICLE_BREAKDOWN' : 'TRAFFIC_CONGESTION',
    eventTypeText: repair ? '原地维修' : '拥堵',
    breakdownLevel: repair ? 'MINOR' : null, breakdownPhase: repair ? 'REPAIRING' : null,
    status: 'ACTIVE', speedFactor: repair ? 0 : 0.4,
    durationSeconds, elapsedSeconds: 0, remainingSeconds: durationSeconds
  }
}

// Animation time advances even when this individual vehicle is held at speed zero.
export function advanceDisplayEvent(event, playbackSeconds) {
  const affected = Math.min(playbackSeconds, event.durationSeconds - event.elapsedSeconds)
  event.elapsedSeconds += affected
  event.remainingSeconds = Math.ceil(event.durationSeconds - event.elapsedSeconds)
  return playbackSeconds - affected * (1 - event.speedFactor)
}

export function displayEventDuration(seconds) {
  const remaining = Math.max(0, Math.ceil(seconds || 0))
  return `${Math.floor(remaining / 60)} 分 ${remaining % 60} 秒`
}
