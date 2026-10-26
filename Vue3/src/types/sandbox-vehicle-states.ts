export type RecordedVehicleGroup = 'empty' | 'loaded' | 'loading' | 'unloading' | 'waiting' | 'abnormal' | 'other'
export type RecordedVehicleCounts = Record<RecordedVehicleGroup,number>
export interface SandboxVehicleStates {
  artifactVersion:'sandbox-vehicle-states/v1'; executionId:string; manifestSha256:string;
  loopIndex:number; simTime:string; windowLoops:20; fromLoopIndex:number; toLoopIndex:number;
  windowStart:string; windowEnd:string; checkedFromLoopIndex:number;
  tickSha256:string; factsSha256:string; evaluationSha256:string; factVersion:string;
  source:'FROZEN_VEHICLE_SNAPSHOTS_AND_RECORDED_INTERVALS'; boundaryPolicy:'INTERVAL_OVERLAP_WITH_WINDOW_END_INCLUDED';
  integrityScope:'MANIFEST_WINDOW_FACTS_EVALUATIONS_AND_LOCAL_TICK_LINKS'|'MANIFEST_RAW_FACTS_EVALUATION_AND_LOCAL_TICK_LINK'; ledgerIntegrity:'NOT_CHECKED';
  current:{status:'AVAILABLE';reason:null;totalVehicles:number;nonIdleVehicles:number;counts:RecordedVehicleCounts};
  window:{status:'AVAILABLE'|'NOT_AVAILABLE'|'NOT_SUPPORTED'|'NOT_REQUESTED';reason:string|null;counts:RecordedVehicleCounts|null;distinctNonIdleVehicles:number|null};
}
