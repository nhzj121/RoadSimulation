import { ref, shallowRef, watch, onScopeDispose } from 'vue'
import { sandboxApi, SandboxApiError } from '../api/sandbox'
import type { SandboxRecordedContext } from '../types/sandbox-service-activity'
import type { SandboxVehicleStates, RecordedVehicleCounts, RecordedVehicleGroup } from '../types/sandbox-vehicle-states'
import type { EvaluationSnapshot } from '../types/evaluation'
import type { DomainChartModel } from '../utils/evaluationDomainPresentation'
import { VISIBLE_VEHICLE_GROUPS } from '../utils/simulationWorkspacePresentation'

// Reuse only names/colors, never the ordinary map's vehicle list or wall clock.
export const recordedStateGroups:ReadonlyArray<{id:RecordedVehicleGroup;label:string;color:string}> = [
  {id:'empty',label:'空驶',color:VISIBLE_VEHICLE_GROUPS[0].color},
  {id:'loaded',label:'有载',color:VISIBLE_VEHICLE_GROUPS[1].color},
  {id:'loading',label:'装货',color:VISIBLE_VEHICLE_GROUPS[2].color},
  {id:'unloading',label:'卸货',color:VISIBLE_VEHICLE_GROUPS[3].color},
  {id:'waiting',label:'等待',color:VISIBLE_VEHICLE_GROUPS[4].color},
  {id:'abnormal',label:'异常',color:VISIBLE_VEHICLE_GROUPS[5].color},
  {id:'other',label:'其他',color:VISIBLE_VEHICLE_GROUPS[6].color},
]
const sum=(counts:RecordedVehicleCounts)=>recordedStateGroups.reduce((n,g)=>n+counts[g.id],0)
export function validateRecordedVehicleStates(value:SandboxVehicleStates,context:SandboxRecordedContext,snapshot:EvaluationSnapshot,includeWindow:boolean) {
  const hash=(v:unknown)=>typeof v==='string' && /^[0-9a-f]{64}$/.test(v)
  const count=(v:unknown):v is number=>Number.isSafeInteger(v) && Number(v)>=0
  const total=value?.current?.totalVehicles
  const counts=(v:RecordedVehicleCounts|null|undefined)=>v!=null && recordedStateGroups.every(g=>count(v[g.id]) && v[g.id]<=total)
  const current=value?.current,window=value?.window
  const localTime=(v:unknown)=>typeof v==='string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d(?::\d\d(?:\.\d{1,9})?)?$/.test(v)?Date.parse(`${v}Z`):NaN
  const valid=value?.artifactVersion==='sandbox-vehicle-states/v1' && value.executionId===context.executionId && value.manifestSha256===context.manifestSha256
    && value.loopIndex===snapshot.loopIndex && value.simTime===snapshot.simTime && value.windowLoops===20
    && value.fromLoopIndex===Math.max(0,snapshot.loopIndex-19) && value.toLoopIndex===snapshot.loopIndex
    && Number.isFinite(localTime(value.windowStart)) && localTime(value.windowStart)<localTime(value.windowEnd) && localTime(value.windowEnd)===localTime(snapshot.simTime)
    && value.checkedFromLoopIndex===(includeWindow?Math.max(0,value.fromLoopIndex-1):snapshot.loopIndex)
    && value.source==='FROZEN_VEHICLE_SNAPSHOTS_AND_RECORDED_INTERVALS' && value.boundaryPolicy==='INTERVAL_OVERLAP_WITH_WINDOW_END_INCLUDED'
    && value.integrityScope===(includeWindow?'MANIFEST_WINDOW_FACTS_EVALUATIONS_AND_LOCAL_TICK_LINKS':'MANIFEST_RAW_FACTS_EVALUATION_AND_LOCAL_TICK_LINK')
    && value.ledgerIntegrity==='NOT_CHECKED' && hash(value.tickSha256) && hash(value.factsSha256) && hash(value.evaluationSha256)
    && ['sandbox-business-facts/v1','sandbox-business-facts/v2'].includes(value.factVersion)
    && current?.status==='AVAILABLE' && current.reason===null && count(total) && count(current.nonIdleVehicles)
    && counts(current.counts) && current.nonIdleVehicles===sum(current.counts) && current.nonIdleVehicles<=total
    && (includeWindow
      ? window?.status==='AVAILABLE'
        ? window.reason===null && counts(window.counts) && count(window.distinctNonIdleVehicles) && window.distinctNonIdleVehicles<=total
          && window.distinctNonIdleVehicles<=sum(window.counts!) && recordedStateGroups.every(g=>window.counts![g.id]<=window.distinctNonIdleVehicles!)
          && window.distinctNonIdleVehicles>=current.nonIdleVehicles && recordedStateGroups.every(g=>window.counts![g.id]>=current.counts[g.id])
        : ['NOT_AVAILABLE','NOT_SUPPORTED'].includes(window?.status) && window.counts===null && window.distinctNonIdleVehicles===null && typeof window.reason==='string' && !!window.reason
      : window?.status==='NOT_REQUESTED' && window.counts===null && window.distinctNonIdleVehicles===null && window.reason===null)
  if(!valid) throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false)
}

export function recordedVehicleStateChart(value:SandboxVehicleStates|null,window:boolean,message='车辆状态记录尚未确认'):DomainChartModel {
  const current=value?.current,period=value?.window,counts=window?period?.counts:current?.counts
  const available=!!value && (!window || period?.status==='AVAILABLE') && !!counts
  const nonIdle=current?.nonIdleVehicles || 0
  return {id:window?'recorded-vehicle-window':'recorded-vehicle-current',title:window?'近20轮各状态车辆数':'本轮非空闲车辆状态',
    description:window?'窗口内曾处于该状态的车辆，按车辆ID去重；同车可以计入多个状态，不是进入次数或各轮车辆数之和。':'所选轮次冻结车辆状态；空闲不展示、不并入其他，占比以本轮非空闲车辆为分母。',
    kind:window?'bars':'ring',unit:'vehicle',state:!available?'unavailable':!window && nonIdle===0?'empty':'ready',
    message:!available?(window && period?.reason || message):!window && nonIdle===0?'本轮暂无非空闲车辆':'',notices:[],total:window?0:nonIdle,inner:[],
    items:available?recordedStateGroups.map(g=>({label:g.label,value:counts![g.id],color:g.color})):[]}
}

/** Single-tick mode stays cheap; the 20-tick read is lazy and never polls on its own. */
export function useRecordedVehicleStates(options:{context:()=>SandboxRecordedContext|undefined;snapshot:()=>EvaluationSnapshot;includeWindow:()=>boolean;onRecordError:(e:unknown)=>void},api=sandboxApi) {
  const result=shallowRef<SandboxVehicleStates|null>(null),loading=ref(false),error=ref('')
  let token=0,disposed=false,controller:AbortController|undefined
  async function refresh(){
    const current=++token;controller?.abort();controller=new AbortController();result.value=null;loading.value=false;error.value=''
    const context=options.context(),snapshot=options.snapshot(),includeWindow=options.includeWindow()
    if(!context || context.stale){error.value='结果信息尚未确认或已过期，车辆状态暂不可用。';return}
    loading.value=true
    try{
      const value=await api.vehicleStates(context.executionId,snapshot.loopIndex,includeWindow,controller.signal)
      if(disposed || token!==current)return
      validateRecordedVehicleStates(value,context,snapshot,includeWindow);result.value=value
    }catch(e){
      if(disposed || token!==current)return
      error.value=e instanceof SandboxApiError?e.code:'车辆状态读取未确认'
      if(e instanceof SandboxApiError && ['INSPECTION_REQUIRED','EXECUTION_RECORD_CORRUPT','EXECUTION_UNJOURNALED'].includes(e.code))options.onRecordError(e)
    }finally{if(token===current)loading.value=false}
  }
  watch(()=>[options.context()?.executionId,options.context()?.manifestSha256,options.context()?.stale,options.snapshot().loopIndex,options.snapshot().simTime,options.includeWindow()],()=>void refresh(),{immediate:true})
  onScopeDispose(()=>{disposed=true;token++;controller?.abort()})
  return {result,loading,error,refresh}
}
