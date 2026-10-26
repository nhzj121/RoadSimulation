export interface SandboxRecordedContext { executionId: string; manifestSha256: string; stale: boolean }
export interface SandboxServiceActivity {
  artifactVersion: 'sandbox-service-activity/v1'; executionId: string; manifestSha256: string;
  loopIndex: number; simTime: string; windowLoops: number; fromLoopIndex: number; toLoopIndex: number;
  windowStart: string; windowEnd: string; tickSha256: string; factsSha256: string; evaluationSha256: string;
  integrityScope: 'MANIFEST_RAW_FACTS_EVALUATION_AND_LOCAL_TICK_LINK'; ledgerIntegrity: 'NOT_CHECKED';
  source: 'RECORDED_NODE_SERVICE_EPISODES'; boundaryPolicy: string; coverage?: string;
  status: 'AVAILABLE' | 'NOT_AVAILABLE' | 'NOT_SUPPORTED'; reason: string | null;
  counts: { loadStarted: number; loadCompleted: number; unloadStarted: number; unloadCompleted: number } | null;
}
