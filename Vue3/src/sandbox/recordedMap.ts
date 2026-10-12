import { ref, shallowRef } from 'vue'
import type { SandboxExecutionSummary } from '../types/sandbox'
import type { SandboxMapContext, SandboxMapSnapshot, RecordedVehicle, RecordedPoi } from '../types/sandbox-map'
import {SandboxApiError} from '../api/sandbox'
import {terminalExecution} from './results'

export const mapPositionPolicy='POI_LINK_OR_UNKNOWN_NO_INTERPOLATION_V1'
export function involvedPois(snapshot:SandboxMapSnapshot,vehicleId:number|null=null):RecordedPoi[] {
  const ids=new Set<number>(),p=snapshot.projection
  for(const v of p.vehicles) if((vehicleId===null||v.id===vehicleId)&&v.positionSource==='POI_LINK'&&v.currentPoiId!==null)ids.add(v.currentPoiId)
  for(const leg of p.legs)if(vehicleId===null||leg.vehicleId===vehicleId){if(leg.fromPoiId!==null)ids.add(leg.fromPoiId);if(leg.toPoiId!==null)ids.add(leg.toPoiId)}
  for(const node of p.nodes)if(vehicleId===null||p.assignments.some(a=>a.id===node.assignmentId&&a.vehicleId===vehicleId))if(typeof node.poiId==='number')ids.add(node.poiId)
  if(vehicleId===null)for(const s of p.shipments)for(const k of ['originPoiId','destPoiId'])if(typeof s[k]==='number')ids.add(s[k] as number)
  return p.pois.filter(poi=>ids.has(poi.id))
}
export function vehicleDetails(snapshot:SandboxMapSnapshot,vehicle:RecordedVehicle) {
  const p=snapshot.projection,assignments=p.assignments.filter(a=>a.vehicleId===vehicle.id||p.legs.some(l=>l.assignmentId===a.id&&l.vehicleId===vehicle.id)),ids=new Set(assignments.map(a=>a.id))
  const cargo=p.cargo.filter(c=>['assignmentId','inboundAssignmentId','outboundAssignmentId'].some(k=>ids.has(c[k] as number)))
  return{assignments,legs:p.legs.filter(l=>l.vehicleId===vehicle.id),cargo,shipments:p.shipments.filter(s=>cargo.some(c=>c.shipmentId===s.id)),events:p.events.filter(e=>e.vehicleId===vehicle.id||e.replacementVehicleId===vehicle.id),drivingProgress:p.drivingProgress.filter(d=>d.vehicleId===vehicle.id)}
}
export interface RecordedMapApi {executionSummary(id:string,signal?:AbortSignal):Promise<SandboxExecutionSummary>;mapContext(id:string,signal?:AbortSignal):Promise<SandboxMapContext>;mapSnapshot(id:string,index:number,signal?:AbortSignal):Promise<SandboxMapSnapshot>}
/** Read-only manual navigation. Page commits map/details/evaluation as one snapshot, never independently. */
export function createRecordedMapController(api:RecordedMapApi,initialId:string) {
  const executionId=ref(initialId),summary=shallowRef<SandboxExecutionSummary|null>(null),context=shallowRef<SandboxMapContext|null>(null),snapshot=shallowRef<SandboxMapSnapshot|null>(null)
  const stale=ref(true),loading=ref(false),selecting=ref(false),following=ref(true),error=ref(''),blocked=ref(false),inspection=ref<{jobId:string;reason:string}|null>(null)
  let generation=0,selection=0,disposed=false,snapshotUnconfirmed=false,poll:AbortController|undefined,query:AbortController|undefined
  const cache=new Map<number,SandboxMapSnapshot>();let ahead:AbortController|undefined
  function clearCache(){cache.clear();ahead?.abort();ahead=undefined}
  function validate(next:SandboxMapSnapshot,id:string,index:number){
    if(next.artifactVersion!=='sandbox-map-snapshot/v1'||next.executionId!==id||next.manifestSha256!==context.value?.manifestSha256||next.loopIndex!==index||next.evaluation.loopIndex!==index||next.positionPolicy!==mapPositionPolicy)throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false)
  }
  function remember(next:SandboxMapSnapshot){cache.delete(next.loopIndex);cache.set(next.loopIndex,next);while(cache.size>5)cache.delete(cache.keys().next().value as number)}
  function fail(value:unknown){const e=value instanceof SandboxApiError?value:null;error.value=e?.code||(value as Error).message;stale.value=true
    clearCache()
    if(e?.code==='INSPECTION_REQUIRED'){inspection.value={jobId:e.jobId||'未知任务',reason:e.reason||e.code};summary.value=null;context.value=null;snapshot.value=null}
    if(['EXECUTION_RECORD_CORRUPT','EXECUTION_UNJOURNALED','UNSUPPORTED_FACT_VERSION'].includes(e?.code||'')){blocked.value=true;snapshot.value=null}
  }
  async function select(index:number,manual=true){
    const count=summary.value?.progress.recordedLoops||0
    if(disposed||blocked.value||inspection.value||!Number.isSafeInteger(index)||index<0||index>=count){error.value='INVALID_RANGE：只能选择完整记录轮次';return false}
    if(manual)following.value=false
    query?.abort();query=new AbortController();const token=++selection,current=generation,id=executionId.value;selecting.value=true
    try{const next=(!manual&&!stale.value?cache.get(index):undefined)||await api.mapSnapshot(id,index,query.signal)
      if(disposed||current!==generation||token!==selection)return false
      validate(next,id,index);remember(next)
      snapshot.value=next;snapshotUnconfirmed=false;stale.value=false;error.value='';return true
    }catch(value){if(!disposed&&current===generation&&token===selection){snapshotUnconfirmed=true;fail(value)}return false}
    finally{if(current===generation&&token===selection)selecting.value=false}
  }
  async function refresh(){
    if(disposed||loading.value||blocked.value||inspection.value)return
    poll=new AbortController();const current=generation,id=executionId.value;loading.value=true
    try{const s=await api.executionSummary(id,poll.signal);if(disposed||current!==generation)return
      let c=context.value;if(!c)c=await api.mapContext(id,poll.signal);if(disposed||current!==generation)return
      if(s.executionId!==id||c.executionId!==id||c.artifactVersion!=='sandbox-map-context/v1'||s.manifestSha256!==c.manifestSha256||c.positionPolicy!==mapPositionPolicy)throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false)
      summary.value=s;context.value=c
      const count=s.progress.recordedLoops||0
      if(following.value&&count>0&&(snapshot.value?.loopIndex!==count-1||stale.value))await select(count-1,false)
      else if(count===0){snapshot.value=null;snapshotUnconfirmed=false;stale.value=false;error.value=''}
      else if(!snapshotUnconfirmed){stale.value=false;error.value=''}
    }catch(value){if(!disposed&&current===generation)fail(value)}finally{if(current===generation)loading.value=false}
  }
  async function latest(){following.value=true;const count=summary.value?.progress.recordedLoops||0;if(count)await select(count-1,false)}
  async function prefetch(index:number){
    if(disposed||stale.value||blocked.value||inspection.value||cache.has(index)||index<0||index>=(summary.value?.progress.recordedLoops||0))return
    ahead?.abort();const request=new AbortController();ahead=request;const current=generation,id=executionId.value
    try{const next=await api.mapSnapshot(id,index,request.signal);if(disposed||request.signal.aborted||current!==generation)return;validate(next,id,index);remember(next)}
    catch(value){if(disposed||request.signal.aborted||current!==generation)return;const code=value instanceof SandboxApiError?value.code:'';if(['EXECUTION_RECORD_CORRUPT','EXECUTION_UNJOURNALED','UNSUPPORTED_FACT_VERSION','INSPECTION_REQUIRED'].includes(code))fail(value)}
  }
  function stopPrefetch(){ahead?.abort();ahead=undefined}
  function cancelSelection(){selection++;query?.abort();selecting.value=false}
  async function switchExecution(id:string){generation++;selection++;poll?.abort();query?.abort();clearCache();executionId.value=id;summary.value=null;context.value=null;snapshot.value=null;snapshotUnconfirmed=false;stale.value=true;blocked.value=false;inspection.value=null;error.value='';following.value=true;loading.value=false;selecting.value=false;await refresh()}
  function dispose(){disposed=true;generation++;selection++;poll?.abort();query?.abort();clearCache()}
  return{executionId,summary,context,snapshot,stale,loading,selecting,following,error,blocked,inspection,refresh,select,latest,switchExecution,dispose,fail,prefetch,stopPrefetch,cancelSelection,cachedLoops:()=>[...cache.keys()],isTerminal:()=>!!summary.value&&terminalExecution(summary.value.status)}
}
