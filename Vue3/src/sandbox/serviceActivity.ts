import { ref, shallowRef, watch, onScopeDispose } from 'vue'
import type { SandboxRecordedContext, SandboxServiceActivity } from '../types/sandbox-service-activity'
import type { EvaluationSnapshot } from '../types/evaluation'
import { sandboxApi, SandboxApiError } from '../api/sandbox'

export const serviceWindowChoices = [1,5,10,20] as const
export function validateServiceActivity(value:SandboxServiceActivity, context:SandboxRecordedContext, snapshot:EvaluationSnapshot, window:number) {
  const hash=(v:unknown)=>typeof v==='string' && /^[0-9a-f]{64}$/.test(v)
  const valid = value?.artifactVersion==='sandbox-service-activity/v1' && value.executionId===context.executionId
    && value.manifestSha256===context.manifestSha256 && value.loopIndex===snapshot.loopIndex && value.simTime===snapshot.simTime
    && value.windowLoops===window && value.fromLoopIndex===Math.max(0,snapshot.loopIndex-window+1) && value.toLoopIndex===snapshot.loopIndex
    && value.integrityScope==='MANIFEST_RAW_FACTS_EVALUATION_AND_LOCAL_TICK_LINK' && value.ledgerIntegrity==='NOT_CHECKED'
    && value.source==='RECORDED_NODE_SERVICE_EPISODES' && hash(value.tickSha256) && hash(value.factsSha256) && hash(value.evaluationSha256)
    && ['AVAILABLE','NOT_AVAILABLE','NOT_SUPPORTED'].includes(value.status)
    && (value.status==='AVAILABLE' ? value.counts!==null && ['loadStarted','loadCompleted','unloadStarted','unloadCompleted'].every(key=>{
      const count=value.counts?.[key as keyof NonNullable<SandboxServiceActivity['counts']>];return Number.isSafeInteger(count) && count!>=0
    }) : value.counts===null && typeof value.reason==='string' && value.reason.length>0)
  if(!valid) throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false)
}

/** Lazy, cancellable read-only query. No wall-clock window or simulation actions. */
export function useRecordedServiceActivity(options:{enabled:()=>boolean;context:()=>SandboxRecordedContext|undefined;snapshot:()=>EvaluationSnapshot;window:()=>number;onRecordError:(e:unknown)=>void},api= sandboxApi) {
  const result=shallowRef<SandboxServiceActivity|null>(null),loading=ref(false),error=ref('')
  let token=0,disposed=false,controller:AbortController|undefined
  async function refresh() {
    const current=++token;controller?.abort();controller=new AbortController();result.value=null;error.value='';loading.value=false
    const context=options.context(),snapshot=options.snapshot(),window=options.window()
    if(!options.enabled()) return
    if(!context || context.stale) {error.value='结果信息尚未确认或已过期，装卸次数暂不可用。';return}
    loading.value=true
    try {
      const response=await api.serviceActivity(context.executionId,snapshot.loopIndex,window,controller.signal)
      if(disposed || current!==token)return
      validateServiceActivity(response,context,snapshot,window);result.value=response
    } catch(e) {
      if(disposed || current!==token)return
      error.value=e instanceof SandboxApiError ? e.code : '装卸次数读取未确认'
      if(e instanceof SandboxApiError && ['INSPECTION_REQUIRED','EXECUTION_RECORD_CORRUPT','EXECUTION_UNJOURNALED'].includes(e.code)) options.onRecordError(e)
    } finally {if(current===token)loading.value=false}
  }
  watch(()=>[options.enabled(),options.context()?.executionId,options.context()?.manifestSha256,options.context()?.stale,options.snapshot().loopIndex,options.snapshot().simTime,options.window()],()=>void refresh(),{immediate:true})
  onScopeDispose(()=>{disposed=true;token++;controller?.abort()})
  return{result,loading,error,refresh}
}
