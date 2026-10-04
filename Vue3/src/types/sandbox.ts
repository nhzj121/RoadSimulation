/** 5B-0 additive read contracts. No ordinary-simulation callbacks or mutable database settings. */
export type SandboxJobStatus = 'ACCEPTED' | 'PREPARING' | 'RUNNING' | 'CANCEL_REQUESTED'
  | 'FINALIZING' | 'SUCCEEDED' | 'CANCELLED' | 'FAILED' | 'INTERRUPTED' | 'ABORTED'
export type SandboxExecutionStatus = 'CREATED' | 'RUNNING' | 'COMPLETED' | 'CANCELLED' | 'FAILED' | 'INTERRUPTED'
export type SandboxFailurePhase = 'STARTUP' | 'TICK' | 'FINALIZATION' | 'INTERRUPTION'
export type SandboxJson = null | boolean | number | string | SandboxJson[] | { [key: string]: SandboxJson }

/** Authoring uses baseline catalogs, never ordinary database CRUD or runtime state. */
export interface SandboxSelection {
  mode: 'ALL_ELIGIBLE' | 'EXPLICIT_IDS'
  includeIds: number[]
  excludeIds: number[]
}
export interface SandboxScenarioDefinition {
  artifactVersion: 'sandbox-scenario-definition/v1'
  scenarioKey: string
  displayName: string | null
  description: string | null
  baseline: { baselineId: string; restorationPayloadSha256: string; simulationFactsSha256: string; effectiveBaseDataSha256: string }
  selection: {
    vehicles: SandboxSelection & { minimumLoadCapacityTonnes: number | null; maximumLoadCapacityTonnes: number | null; minimumCargoVolumeCubicMeters: number | null; maximumCargoVolumeCubicMeters: number | null; vehicleTypes: string[]; modelTypes: string[] }
    pois: SandboxSelection & { poiTypes: string[] }
    goods: SandboxSelection & { categories: string[] }
    processingChains: SandboxSelection
  }
  overrides: {
    stageOutputRatios: Array<{stageId: number; outputWeightRatio: number}>
    processingInputShares: Array<{inputId: number; inputShare: number}>
    initialInventories: Array<{poiId: number; goodsId: number; quantity: number}>
    vehicleInitialPois: Array<{vehicleId: number; poiId: number; initializationPolicy: 'FIXED_POI'}>
  }
}
export interface SandboxCatalogItem {
  id: number
  name?: string
  licensePlate?: string
  poiType?: string
  category?: string
  maxLoadCapacityTonnes?: number
  cargoVolumeCubicMeters?: number
  vehicleType?: string
  modelType?: string
  chainName?: string
  status?: string
  stages?: Array<{id: number; stageName: string; processingPoiId: number; outputGoodsId: number | null; outputWeightRatio: number; inputs: Array<{id: number; goodsId: number; inputShare: number}>}>
}
export interface SandboxCatalogPage { baselineId: string; total: number; offset: number; limit: number; items: SandboxCatalogItem[] }
export interface SandboxScenarioPreview {
  scenarioKey: string; baselineId: string; scenarioDefinitionSha256: string; effectiveScenarioDataSha256: string
  baseDataProjectionSha256: string; rowCounts: Record<string, number>; fixedVehicleCount: number
  resolvedSelection: {vehicleIds: number[]; poiIds: number[]; goodsIds: number[]; processingChainIds: number[]}
}
export interface SandboxScenarioRevision {
  scenarioKey: string; revision: number; definition: SandboxScenarioDefinition
  fingerprints: {scenarioDefinitionSha256: string; effectiveScenarioDataSha256: string}
}
export interface SandboxRunDefinition {
  artifactVersion: 'sandbox-run-specification/v2'
  runSpecKey: string; displayName: string | null; description: string | null
  scenario: {scenarioKey: string; revision: number; scenarioDefinitionSha256: string; effectiveScenarioDataSha256: string}
  simulationClock: {startLocalDateTime: string; tickDurationSeconds: number; totalLoops: number}
  demand: {mode: string; generationIntervalLoops: number; startupPreGenerationEnabled: boolean}
  dispatch: {strategy: 'ORIGINAL' | 'HEURISTIC'; dispatchIntervalLoops: number; algorithmProfileId: string}
  random: {protocolId: string; rootSeed: string}
  vehicleInitialization: {defaultPolicy: string; eligiblePoiTypes: string[]; coordinateAuthority: string}
  driverBehavior: {enabled: boolean; transitionPolicy: string; idleToRejecting: number; idleToMaintenance: number; rejectingToIdle: number; maintenanceToIdle: number; seedPolicy: string}
  weather: {
    sourceMode: 'SEEDED_GENERATION' | 'EXPLICIT_TIMELINE'
    generatorVersion: string | null; intervalMinutes: number | null; weights: number[] | null
    speedFactors: Record<string, number> | null
    timeSlices: Array<{startMinute: number; endMinute: number; weatherType: string; speedFactor: number}>
  }
  events: {
    ruleVersion: string; enabled: boolean; autoEnabled: boolean; seedPolicy: string
    congestion: {hourlyProbability: number; minDurationMinutes: number; maxDurationMinutes: number; speedFactor: number}
    breakdown: {hourlyProbability: number; minDurationMinutes: number; maxDurationMinutes: number; speedFactor: number}
    breakdownPolicy: {version: string; minorProbability: number; minorRepairMin: number; minorRepairMax: number; rescueWaitMin: number; rescueWaitMax: number; assistanceRepairMin: number; assistanceRepairMax: number; replacementProbability: number; replacementWaitMin: number; replacementWaitMax: number}
  }
}
export interface SandboxRunPreview {
  normalizedSpecification: SandboxRunDefinition
  runSpecificationSha256: string; resolvedVehicleInitialStateSha256: string; preparedRunFactsSha256: string
  weatherTimelineSha256: string; eventConfigurationSha256: string
  eligibleVehicleInitialPoiCount: number
  vehicleInitialStates: Array<{vehicleId: number; poiId: number; initializationPolicy: string; decisionDomain: string | null; decisionKey: string | null; derivedSeedHex: string | null}>
  weatherTimeline: SandboxRunDefinition['weather']['timeSlices']
}
export interface SandboxRunRevision { runSpecKey: string; revision: number; specification: SandboxRunDefinition; fingerprints: {runSpecificationSha256: string} }
export interface SandboxDraftView<T> { definition: T; rowVersion: number }
export interface SandboxSaved<T> { compilation: T; rowVersion: number }
export interface SandboxStartRequest { clientRequestId: string; runSpecKey: string; revision: number }
export interface SandboxStartReceipt extends SandboxStartRequest { jobId: string; reused: boolean }
export interface SandboxJobList { items: Array<{job_id: string; run_spec_key: string; run_spec_revision: number; job_status: SandboxJobStatus; execution_id: string | null; created_at: string}>; hasMore: boolean; offset: number; limit: number }
/** Durable loop count, not an animation counter; 100% is not a terminal outcome. */
export interface SandboxProgress {
  requestedLoops: number | null
  recordedLoops: number | null
  percent: number | null
}
export interface SandboxJobView {
  artifactVersion: 'sandbox-job-view/v1'
  jobId: string
  runSpecKey: string
  revision: number
  status: SandboxJobStatus
  phase: 'ACCEPTED' | 'PREPARING' | 'RUNNING' | 'FINALIZING' | 'ATTENTION_REQUIRED' | 'TERMINAL'
  executionId: string | null
  workerObservation: 'NOT_STARTED' | 'ALIVE' | 'NOT_OBSERVED' | 'TERMINAL'
  attentionRequired: boolean
  cancellationRequested: boolean
  progress: SandboxProgress
  actions: {
    canCancel: boolean
    cancelDisabledReason: 'INSPECTION_REQUIRED' | 'REQUEST_ALREADY_ACCEPTED' | 'PHASE_NOT_CANCELLABLE' | 'TASK_TERMINAL' | null
    canPause: false
    canResume: false
  }
  failureCode: string | null
  failurePhase: SandboxFailurePhase | null
}
export interface SandboxExecutionSummary {
  artifactVersion: 'sandbox-execution-summary/v1'
  executionId: string
  runSpecKey: string
  revision: number
  baselineId: string
  status: SandboxExecutionStatus
  resultKind: 'FULL_COMPLETION' | 'CANCELLED_PREFIX' | 'FAILED_PREFIX' | 'INTERRUPTED_PREFIX' | 'IN_PROGRESS_PREFIX'
  progress: SandboxProgress
  lastRecordedLoopIndex: number | null
  failedLoopIndex: number | null
  failurePhase: SandboxFailurePhase | null
  failureCode: string | null
  manifestSha256: string
  lastTickSha256: string | null
  /** Existing recorded EvaluationSnapshot, not recalculated against current business tables. */
  lastEvaluation: SandboxJson
  evaluationIntegrityScope: 'NONE' | 'PAYLOAD_ONLY'
  ledgerIntegrity: 'NOT_CHECKED'
}
export interface SandboxEvaluationPage {
  artifactVersion: 'sandbox-evaluation-page/v1'
  executionId: string
  offset: number
  limit: number
  hasMore: boolean
  integrityScope: 'PAYLOAD_ONLY'
  fromLoopIndex?: number
  toLoopIndex?: number
  recordedLoops?: number
  items: Array<{
    loopIndex: number
    tickStart: string
    tickEnd: string
    evaluation: SandboxJson
    evaluationSha256: string
  }>
}
export interface SandboxExecutionConfiguration {
  artifactVersion: 'sandbox-execution-configuration/v1'
  executionId: string; manifestSha256: string; javaVersion: string
  baseline: SandboxJson; preparation: SandboxJson; scenarioRevision: SandboxJson; runRevision: SandboxJson
  integrityScope: 'MANIFEST_PAYLOAD'
}
