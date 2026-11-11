import type { RecordedLeg, RecordedRow, RecordedVehicle } from './sandbox-map'

export interface RoadReplayLeg extends RecordedLeg {
  startedSimTime: string | null
  completedSimTime: string | null
  loadState: 'EMPTY' | 'LOADED'
  currentLoadTonnes: number | null
}

export interface RoadReplayService extends RecordedRow {
  vehicleId: number
  assignmentId: number
  assignmentNodeId: number | null
  actionType: 'LOAD' | 'UNLOAD'
  serviceStartedAt: string
  serviceCompletedAt: string | null
}

export interface RoadReplayEvent extends RecordedRow {
  vehicleId: number
  assignmentId: number
  eventType: string
  status: string
  startTime: string
  resolvedTime: string | null
  originalLegIndex: number | null
  recoveryOutcome: string | null
  replacementOutcome: string | null
}

export interface RoadReplayHandoff extends RecordedRow {
  eventId: number
  assignmentId: number
  originalVehicleId: number
  replacementVehicleId: number
  handoffTime: string | null
  status: string
}

export interface RoadReplayFragment extends RecordedRow {
  legId: number
  vehicleId: number
  fromSimTime: string
  toSimTime: string
  distanceMeters: number
}

export interface RoadReplayTick {
  artifactVersion: 'sandbox-road-replay-tick/v1'
  executionId: string
  manifestSha256: string
  loopIndex: number
  tickSha256: string
  tickStart: string
  tickEnd: string
  initialVehicles?: RecordedVehicle[]
  facts: {
    vehicles: RecordedVehicle[]
    legs: RoadReplayLeg[]
    assignments: RecordedRow[]
    nodes: RecordedRow[]
    cargo: RecordedRow[]
    services: RoadReplayService[] | null
    events: RoadReplayEvent[] | null
    handoffs: RoadReplayHandoff[] | null
    fragments: RoadReplayFragment[] | null
  }
}

export interface RoadReplayAssignment extends RecordedRow {
  vehicleId: number | null
  status: string | null
  startTime: string | null
  endTime: string | null
}

export interface RoadReplayAssignmentTick {
  loopIndex: number
  tickSha256: string
  tickStart: string
  tickEnd: string
  assignments: RoadReplayAssignment[] | null
}

export interface RoadReplayAssignmentPage {
  artifactVersion: 'sandbox-road-replay-assignments/v1'
  executionId: string
  manifestSha256: string
  fromLoopIndex: number
  toLoopIndex: number
  offset: number
  hasMore: boolean
  initialAssignments?: RoadReplayAssignment[] | null
  items: RoadReplayAssignmentTick[]
}
