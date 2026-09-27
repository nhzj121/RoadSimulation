const STAGE_MIN = 30
const STAGE_MAX = 180
const TOTAL_MAX = 240

export function validateRandomEventInput(eventType, values) {
  const fields = eventType === 'VEHICLE_BREAKDOWN'
    ? values.breakdownLevel === 'REPLACEMENT_REQUIRED'
      ? [['换车等待', values.replacementWaitMinutes ?? 60]]
      : values.breakdownLevel === 'MINOR' ? [['维修时长', values.repairMinutes]] : [['救援等待', values.rescueWaitMinutes], ['维修时长', values.repairMinutes]]
    : [['持续时长', values.durationMinutes]]
  if (eventType === 'VEHICLE_BREAKDOWN' && values.breakdownLevel === 'MINOR' && Number(values.rescueWaitMinutes) !== 0) return '轻微故障无需等待救援'
  for (const [label, value] of fields) {
    const number = Number(value)
    if (!Number.isInteger(number)) return `${label}必须为整数分钟`
    if (number < STAGE_MIN || number > (eventType === 'TRAFFIC_CONGESTION' ? TOTAL_MAX : STAGE_MAX)) return `${label}必须在 ${STAGE_MIN}–${eventType === 'TRAFFIC_CONGESTION' ? TOTAL_MAX : STAGE_MAX} 分钟内`
  }
  if (eventType === 'VEHICLE_BREAKDOWN' && Number(values.rescueWaitMinutes || 0) + Number(values.repairMinutes) > TOTAL_MAX) return `救援等待与维修总时长不能超过 ${TOTAL_MAX} 分钟`
  return null
}

export function buildRandomEventPayload(eventType, vehicleId, values) {
  if (eventType !== 'VEHICLE_BREAKDOWN') return { eventType, vehicleId, durationMinutes: Number(values.durationMinutes) }
  if (values.breakdownLevel === 'REPLACEMENT_REQUIRED') return {
    eventType,
    vehicleId,
    breakdownLevel: values.breakdownLevel,
    replacementWaitMinutes: Number(values.replacementWaitMinutes ?? 60)
  }
  return {
    eventType,
    vehicleId,
    breakdownLevel: values.breakdownLevel,
    rescueWaitMinutes: values.breakdownLevel === 'MINOR' ? 0 : Number(values.rescueWaitMinutes),
    repairMinutes: Number(values.repairMinutes)
  }
}

const outcomes = {
  RESTORED: '原运输任务已恢复', VEHICLE_MISSING: '车辆已不存在，未自动恢复',
  VEHICLE_STATUS_CHANGED: '车辆状态已变化，未自动恢复', ASSIGNMENT_CHANGED: '车辆任务已变化，未自动恢复',
  ASSIGNMENT_STATUS_CHANGED: '任务状态已变化，未自动恢复', ASSIGNMENT_STAGE_CHANGED: '任务阶段已变化，未自动恢复',
  DRIVING_PHASE_CHANGED: '驾驶阶段已变化，未自动恢复'
}
const replacementOutcomes = {
  REPLACED: '换车成功',
  ORIGINAL_VEHICLE_MISSING: '原车辆已不存在',
  ORIGINAL_STATUS_CHANGED: '原车状态已变化',
  REPLACEMENT_UNAVAILABLE: '替换车辆已不可用',
  ASSIGNMENT_CHANGED: '任务已变化',
  ASSIGNMENT_STATUS_CHANGED: '任务状态已变化',
  DRIVING_PHASE_CHANGED: '驾驶阶段已变化'
}

const replacementVehicleText = event => {
  const plate = event?.replacementLicensePlate || '替换车辆'
  return event?.replacementVehicleId == null ? plate : `${plate}（车辆 ${event.replacementVehicleId}）`
}

export function describeBreakdown(event) {
  if (event?.breakdownLevel === 'REPLACEMENT_REQUIRED') {
    if (event.replacementOutcome && event.replacementOutcome !== 'REPLACED') {
      return `换车未完成 · ${replacementOutcomes[event.replacementOutcome] || event.replacementOutcome}`
    }
    if (event.breakdownPhase === 'WAITING_REPLACEMENT') return '车辆已报废 · 等待兼容的空闲替换车辆'
    if (event.breakdownPhase === 'REPLACEMENT_PREPARING') return `车辆已报废 · ${replacementVehicleText(event)}准备中 · 就绪 ${formatTime(event.replacementReadyTime || event.plannedEndTime)}`
    if (event.breakdownPhase === 'REPLACED' || event.replacementOutcome === 'REPLACED') return `换车成功 · 新承运车辆 ${replacementVehicleText(event)}`
    return '车辆已报废 · 换车状态待更新'
  }
  if (event?.breakdownPhase === 'RECOVERED') return `维修已完成 · ${outcomes[event.recoveryOutcome] || event.recoveryOutcome || '恢复结果待更新'}`
  if (!event?.breakdownLevel) return '原版故障维修'
  const level = event.breakdownLevel === 'MINOR' ? '轻微故障' : '需救援故障'
  if (event.breakdownPhase === 'WAITING_RESCUE') return `${level} · 等待模拟救援（${event.rescueWaitMinutes} 分钟）`
  if (event.breakdownPhase === 'REPAIRING') return `${level} · 维修中（${event.repairMinutes} 分钟）${event.repairStartTime ? ` · 开始 ${formatTime(event.repairStartTime)}` : ''}`
  return `${level} · 故障阶段待更新`
}

export function describeBreakdownPolicy(policy, legacy = {}) {
  if (policy?.version === 'breakdown-v3') {
    const replacement = Math.round(Number(policy.replacementProbability || 0) * 100)
    const minor = Math.round(Number(policy.minorProbability || 0) * 100)
    const assistance = Math.max(0, 100 - minor - replacement)
    return `分级故障：轻微 ${minor}%（维修 ${policy.minorRepairMin}–${policy.minorRepairMax} 分钟）；需救援 ${assistance}%（救援等待 ${policy.rescueWaitMin}–${policy.rescueWaitMax} 分钟，维修 ${policy.assistanceRepairMin}–${policy.assistanceRepairMax} 分钟）；报废换车 ${replacement}%（换车等待 ${policy.replacementWaitMin}–${policy.replacementWaitMax} 分钟）`
  }
  if (policy?.version === 'breakdown-v2') return `分级故障：轻微 ${Math.round(policy.minorProbability * 100)}%（维修 ${policy.minorRepairMin}–${policy.minorRepairMax} 分钟）；需救援 ${Math.round((1 - policy.minorProbability) * 100)}%（救援等待 ${policy.rescueWaitMin}–${policy.rescueWaitMax} 分钟，维修 ${policy.assistanceRepairMin}–${policy.assistanceRepairMax} 分钟）`
  return `原版故障维修 ${legacy.minDurationMinutes ?? 60}–${legacy.maxDurationMinutes ?? 120} 分钟`
}

export const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '等待更新'

export const recentBreakdownRows = history => (history || [])
  .filter(event => event.eventType === 'VEHICLE_BREAKDOWN' && event.status === 'RESOLVED')
  .map(event => ({ ...event, description: describeBreakdown(event) }))

export const activeEventSignature = events => (events || [])
  .map(event => String(event.eventId))
  .sort()
  .join(',')

export const createEventHistoryTransitionHandler = refresh => async (current, previous) => {
  if (previous !== undefined && current !== previous) await refresh()
}
