import { ref, shallowRef } from 'vue'
import { sandboxApi } from '../api/sandbox'
import type { SandboxComparison, SandboxMetricComparison } from '../types/sandbox-comparison'
import type { EvaluationMetricValue, EvaluationSnapshot } from '../types/evaluation'
import type { SandboxComparisonSide } from '../types/sandbox-comparison'
import { formatRecordedMetric } from './results'

export const comparisonColors = ['#2878ba', '#d67428']
export const comparisonCategories: Record<string,string> = {GLOBAL_OBJECTIVE:'四个优化目标',SERVICE_CONSTRAINT:'服务约束',VEHICLE:'车辆',CARGO:'货物',TASK:'运输任务',ENVIRONMENT:'外部环境'}
const reasons:Record<string,string>={COMPARISON_REQUIRES_COMPLETED:'请选择两次完整完成的执行',COMPARISON_TIME_MISMATCH:'双方开始时间、步长和总轮数必须一致',COMPARISON_NO_EVALUATION:'运行没有评价记录',INVALID_ARGUMENT:'请选择两次不同的执行',CONTRACT_MISMATCH:'评价契约不同',UNSUPPORTED_CONTRACT:'历史契约仅展示原始记录',METRIC_MISSING:'一侧未记录该指标',METRIC_SEMANTICS_MISMATCH:'单位或指标分类不同',MODEL_MISMATCH:'排放／能耗模型不同',THRESHOLD_MISMATCH:'相关评价阈值不同',VALUE_UNAVAILABLE:'一侧指标不可用',INVALID_VALUE:'指标数值无效',EXECUTION_RECORD_CORRUPT:'历史记录校验失败',INSPECTION_REQUIRED:'执行记录需要人工检查',SANDBOX_UNAVAILABLE:'暂时无法读取沙箱，请重试'}
export const comparisonReason=(reason:string|null)=>reason ? reasons[reason] || reason : ''
export function comparisonValue(value:EvaluationMetricValue|null,raw=false) {
  if(value?.unit==='boolean' && value.status==='AVAILABLE' && value.value!==0 && value.value!==1)return `${String(value.value)} boolean（无效状态值）`
  return value ? `${formatRecordedMetric(value,raw)}${value.status==='AVAILABLE'?'':`（${value.status}）`}` : '未记录'
}
const number=(value:number)=>new Intl.NumberFormat('zh-CN',{maximumFractionDigits:6}).format(value)
export function comparisonDelta(metric:SandboxMetricComparison) {
  if(metric.rightMinusLeft===null)return '—'
  const value=metric.rightMinusLeft*(metric.unit==='ratio'?100:1)
  return `${value>0?'+':''}${number(value)} ${metric.unit==='ratio'?'个百分点':metric.unit}`
}
export function comparisonRelative(metric:SandboxMetricComparison) {
  const value=metric.relativeChangeRatio
  return value===null?'—':`${value>0?'+':''}${number(value*100)}%`
}
export function validateComparison(value:SandboxComparison,left:string,right:string) {
  if(value.artifactVersion!=='sandbox-execution-comparison/v1' || value.left.executionId!==left || value.right.executionId!==right
    || value.left.clock.totalLoops<1 || ['startLocalDateTime','tickDurationSeconds','totalLoops'].some(key=>value.left.clock[key as keyof typeof value.left.clock]!==value.right.clock[key as keyof typeof value.right.clock])
    || [value.left,value.right].some(s=>s.finalSnapshot.loopIndex!==s.clock.totalLoops-1)) throw new Error('COMPARISON_RESPONSE_INVALID')
}
/** Each selection owns its response generation. Changing either side invalidates all presented results immediately. */
export async function readComparisonHistory(side:SandboxComparisonSide,api:Pick<typeof sandboxApi,'evaluations'>,signal:AbortSignal) {
  const count=side.clock.totalLoops,points:EvaluationSnapshot[]=[]
  const time=(text:string)=>Date.parse(`${text}Z`)
  for(let offset=0;offset<count;offset+=100) {
    if(signal.aborted)throw new Error('REQUEST_ABORTED')
    const page=await api.evaluations(side.executionId,0,count-1,offset,signal)
    const size=Math.min(100,count-offset)
    if(page.executionId!==side.executionId || page.recordedLoops!==count || page.offset!==offset || page.fromLoopIndex!==0 || page.toLoopIndex!==count-1
      || page.items.length!==size || page.hasMore!==(offset+size<count))throw new Error('COMPARISON_HISTORY_INVALID')
    for(const [index,item] of page.items.entries()) {
      const expected=offset+index,s=item.evaluation as unknown as EvaluationSnapshot
      if(item.loopIndex!==expected || s.loopIndex!==expected || s.simulationRunId!==side.finalSnapshot.simulationRunId
        || !s.metrics || time(s.simTime)!==time(side.clock.startLocalDateTime)+(expected+1)*side.clock.tickDurationSeconds*1000
        || time(item.tickEnd)!==time(s.simTime) || time(item.tickStart)!==time(side.clock.startLocalDateTime)+expected*side.clock.tickDurationSeconds*1000)throw new Error('COMPARISON_HISTORY_INVALID')
      points.push(s)
      if(expected===count-1 && item.evaluationSha256!==side.finalEvaluationSha256)throw new Error('COMPARISON_HISTORY_CHANGED')
    }
  }
  return points
}
export function createComparisonState(api:Pick<typeof sandboxApi,'compareExecutions'|'evaluations'>=sandboxApi) {
  const comparison=shallowRef<SandboxComparison|null>(null),loading=ref(false),error=ref('')
  const histories=shallowRef<{left:EvaluationSnapshot[];right:EvaluationSnapshot[]}|null>(null)
  let generation=0,controller:AbortController|undefined
  function invalidate(){generation++;controller?.abort();comparison.value=null;histories.value=null;loading.value=false;error.value=''}
  async function load(left:string,right:string) {
    invalidate()
    if(!left || !right || left===right){error.value='INVALID_ARGUMENT';return}
    const current=generation;controller=new AbortController();loading.value=true
    try {
      const value=await api.compareExecutions(left,right,controller.signal)
      if(current!==generation)return
      validateComparison(value,left,right)
      const signal=controller.signal
      const [a,b]=await Promise.all([readComparisonHistory(value.left,api,signal),readComparisonHistory(value.right,api,signal)])
      if(current!==generation)return
      histories.value={left:a,right:b};comparison.value=value
    } catch(e) {if(current===generation){controller?.abort();histories.value=null;comparison.value=null;error.value=(e as Error).message || 'SANDBOX_UNAVAILABLE'}}
    finally{if(current===generation)loading.value=false}
  }
  return {comparison,histories,loading,error,invalidate,load}
}
