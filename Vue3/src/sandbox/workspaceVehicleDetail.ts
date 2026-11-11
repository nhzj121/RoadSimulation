import type { SandboxMapSnapshot } from '../types/sandbox-map'
import type { RoadReplayTick } from '../types/sandbox-road-replay'
import { vehicleStatusPresentation } from '../utils/vehicleStatusPresentation'
import type { ReplayVehicle } from './roadReplayTimeline'

function number(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function meter(value: unknown, capacity: unknown, unit: 't' | 'm³') {
  const current = number(value), limit = number(capacity)
  return {
    current: current === null ? '—' : current.toFixed(1),
    limit: limit === null ? '—' : limit.toFixed(1),
    unit,
    percent: current !== null && limit !== null && limit > 0 ? Math.max(0, Math.min(100, current / limit * 100)) : null,
  }
}

export function presentSandboxWorkspaceVehicle(
  snapshot: SandboxMapSnapshot, vehicleId: number,
  frame: ReplayVehicle[] | null, replayRecord: RoadReplayTick | null,
) {
  if (frame && (replayRecord?.executionId !== snapshot.executionId || replayRecord.manifestSha256 !== snapshot.manifestSha256)) return null
  const movingVehicle = frame?.find(row => row.id === vehicleId)
  const vehicle = frame ? movingVehicle : snapshot.projection.vehicles.find(row => row.id === vehicleId)
  if (!vehicle) return null
  const replayFacts = replayRecord?.facts
  const facts = frame ? replayFacts : snapshot.projection
  const active = facts?.assignments.filter(row => row.vehicleId === vehicleId
    && (row.status === 'ASSIGNED' || row.status === 'IN_PROGRESS' || row.status === 'DELAYED')) ?? []
  const assignment = frame
    ? facts?.assignments.find(row => row.id === movingVehicle?.replayAssignmentId)
    : active.length === 1 ? active[0] : undefined
  const leg = facts?.legs.find(row => row.id === movingVehicle?.replayLegId && row.assignmentId === assignment?.id)
  const drivers = new Set(leg ? replayFacts?.fragments?.filter(row => row.legId === leg.id && row.vehicleId === vehicleId)
    .map(row => number(row.driverId)).filter(id => id !== null) : [])
  const driver = frame ? drivers.size === 1 ? [...drivers][0] : null : number(assignment?.driverId)
  const names = new Map(snapshot.projection.pois.map(poi => [poi.id, poi.name]))
  const poiName = (id: unknown) => typeof id === 'number' ? names.get(id) || `POI ${id}` : null
  const cargo = assignment ? facts?.cargo.filter(item => [item.assignmentId, item.inboundAssignmentId, item.outboundAssignmentId]
    .includes(assignment.id)) ?? [] : []
  const goods = [...new Set(cargo.flatMap(row => typeof row.name === 'string' && row.name ? [row.name] : []))].join('、')
  return {
    title: vehicle.licensePlate || `车辆 ${vehicleId}`,
    status: vehicleStatusPresentation(vehicle.currentStatus),
    action: frame ? '道路回放状态' : `第 ${snapshot.loopIndex + 1} 轮记录状态`,
    task: [
      { label: 'Assignment', value: assignment?.id },
      { label: 'Driver', value: driver },
      { label: 'Route', value: leg ? `运输段 ${leg.sequenceIndex + 1}` : null },
      { label: 'From', value: poiName(leg?.fromPoiId ?? assignment?.originPoiId) },
      { label: 'To', value: poiName(leg?.toPoiId ?? assignment?.destPoiId) },
      { label: 'Goods', value: goods || null },
      { label: 'Quantity', value: cargo.length === 1 ? number(cargo[0].qty) : null },
    ],
    load: [meter(vehicle.currentLoad, vehicle.maxLoadCapacity, 't'), meter(vehicle.currentVolumn, vehicle.cargoVolume, 'm³')],
  }
}
