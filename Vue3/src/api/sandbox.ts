import axios from 'axios'
import type { SandboxMapContext, SandboxMapSnapshot } from '../types/sandbox-map'
import type { SandboxExecutionSummary, SandboxExecutionConfiguration, SandboxEvaluationPage } from '../types/sandbox'
import type { SandboxCatalogPage, SandboxDraftView, SandboxRunDefinition, SandboxRunPreview, SandboxRunRevision, SandboxSaved, SandboxScenarioDefinition, SandboxScenarioPreview, SandboxScenarioRevision, SandboxStartRequest, SandboxStartReceipt, SandboxJobView, SandboxJobList } from '../types/sandbox'

// Separate client: never installs interceptors/headers on ordinary simulation requests.
const client = axios.create({ baseURL: 'http://localhost:8080/api/sandbox', timeout: 30000 })
export class SandboxApiError extends Error {
  constructor(public code: string, public status: number | null, public uncertain: boolean, public jobId?: string, public reason?: string) {
    super(code)
  }
}
async function call<T>(method: 'get' | 'post', url: string, data?: unknown, options: {signal?: AbortSignal; timeout?: number} = {}): Promise<T> {
  try {
    return (await client.request<T>({ method, url, data, ...options,
      headers: method === 'post' ? { 'X-Sandbox-Request': '1', 'Content-Type': 'application/json' } : undefined })).data
  } catch (failure) {
    const error = failure as { response?: { status: number; data?: { errorCode?: string; jobId?: string; reason?: string } } }
    throw new SandboxApiError(error.response?.data?.errorCode || 'SANDBOX_UNAVAILABLE',
      error.response?.status ?? null, method === 'post' && !error.response, error.response?.data?.jobId, error.response?.data?.reason)
  }
}
const keyPath = (key: string) => encodeURIComponent(key)
export const sandboxApi = {
  mapContext: (id:string,signal?:AbortSignal) => call<SandboxMapContext>('get',`/executions/${keyPath(id)}/map-context`,undefined,{signal}),
  mapSnapshot: (id:string,index:number,signal?:AbortSignal) => call<SandboxMapSnapshot>('get',`/executions/${keyPath(id)}/ticks/${index}/map`,undefined,{signal}),
  resultDownloadUrl: (id: string, format: 'json' | 'csv') => client.getUri({url:`/executions/${keyPath(id)}/export?format=${format}`}),
  executionSummary: (id: string, signal?: AbortSignal) => call<SandboxExecutionSummary>('get', `/executions/${keyPath(id)}/summary`, undefined, {signal}),
  executionConfiguration: (id: string, signal?: AbortSignal) => call<SandboxExecutionConfiguration>('get', `/executions/${keyPath(id)}/configuration`, undefined, {signal}),
  evaluations: (id: string, from: number, to: number, offset = 0, signal?: AbortSignal) => call<SandboxEvaluationPage>('get', `/executions/${keyPath(id)}/evaluations?fromLoopIndex=${from}&toLoopIndex=${to}&offset=${offset}&limit=100`, undefined, {signal}),
  verifyExecution: (id: string) => call<Record<string, unknown>>('get', `/executions/${keyPath(id)}/verify`, undefined, {timeout:300000}),
  exportResult: async (id: string, format: 'json' | 'csv'): Promise<Blob> => {
    try { return (await client.get(`/executions/${keyPath(id)}/export?format=${format}`, {responseType:'blob', timeout:300000})).data }
    catch (failure) {
      const error = failure as {response?:{status:number;data:Blob}}, data = error.response?.data
      let body: {errorCode?:string;jobId?:string;reason?:string} = {}
      try { if(data) body=JSON.parse(await data.text()) } catch {}
      throw new SandboxApiError(body.errorCode || 'RESULT_DOWNLOAD_UNCONFIRMED', error.response?.status ?? null, false, body.jobId, body.reason)
    }
  },
  start: (request: SandboxStartRequest) => call<SandboxStartReceipt>('post', '/start-requests', request),
  startRequest: (id: string) => call<SandboxStartReceipt>('get', `/start-requests/${keyPath(id)}`),
  jobs: (offset = 0) => call<SandboxJobList>('get', `/jobs?offset=${offset}&limit=20`),
  jobView: (id: string) => call<SandboxJobView>('get', `/jobs/${keyPath(id)}/view`),
  cancelJob: (id: string) => call<{jobId: string; status: string}>('post', `/jobs/${keyPath(id)}/cancel`),
  baseline: () => call<{ baselineId: string; catalogFilters: {vehicleTypes: string[]; modelTypes: string[]; poiTypes: string[]; categories: string[]} }>('get', '/baseline'),
  workspace: () => call<Record<string, unknown>>('get', '/workspace'),
  catalog: (type: string, offset = 0) => call<SandboxCatalogPage>('get', `/baseline/${type}?offset=${offset}&limit=50`),
  scenarioTemplate: () => call<SandboxScenarioDefinition>('get', '/templates/scenario'),
  runTemplate: (key: string, revision: number) => call<SandboxRunDefinition>('get', `/templates/run?scenarioKey=${keyPath(key)}&revision=${revision}`),
  families: (kind: string, offset = 0) => call<{items: Array<Record<string, unknown>>; hasMore: boolean}>('get', `/${kind}?offset=${offset}&limit=50`),
  draft: <T>(kind: string, key: string) => call<SandboxDraftView<T>>('get', `/${kind}/${keyPath(key)}/draft-view`),
  scenarioRevision: (key: string, revision: number) => { if (!Number.isSafeInteger(revision) || revision < 1) throw new Error('Revision必须是正整数'); return call<SandboxScenarioRevision>('get', `/scenarios/${keyPath(key)}/revisions/${revision}`) },
  runRevision: (key: string, revision: number) => { if (!Number.isSafeInteger(revision) || revision < 1) throw new Error('Revision必须是正整数'); return call<SandboxRunRevision>('get', `/runs/${keyPath(key)}/revisions/${revision}`) },
  compileScenario: (definition: SandboxScenarioDefinition) => call<SandboxScenarioPreview>('post', '/scenarios/compile', definition),
  compileRun: (definition: SandboxRunDefinition) => call<SandboxRunPreview>('post', '/runs/compile', definition),
  compileScenarioText: (text: string) => call<SandboxScenarioPreview>('post', '/scenarios/compile', text),
  compileRunText: (text: string) => call<SandboxRunPreview>('post', '/runs/compile', text),
  saveScenario: (definition: SandboxScenarioDefinition, expectedRowVersion: number) => call<SandboxSaved<SandboxScenarioPreview>>('post', '/scenarios/guarded-draft', { definition, expectedRowVersion }),
  saveRun: (definition: SandboxRunDefinition, expectedRowVersion: number) => call<SandboxSaved<SandboxRunPreview>>('post', '/runs/guarded-draft', { definition, expectedRowVersion }),
  publishScenario: (key: string, expectedRowVersion: number, expectedDefinitionSha256: string) => call<SandboxScenarioRevision>('post', `/scenarios/${keyPath(key)}/guarded-publish`, { expectedRowVersion, expectedDefinitionSha256 }),
  publishRun: (key: string, expectedRowVersion: number, expectedDefinitionSha256: string) => call<SandboxRunRevision>('post', `/runs/${keyPath(key)}/guarded-publish`, { expectedRowVersion, expectedDefinitionSha256 }),
}
