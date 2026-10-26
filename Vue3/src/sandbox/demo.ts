import type {EvaluationMetricValue} from '../types/evaluation'
export interface DemoEntry {id:string;title:string;scenarioKey:string;runSpecKey:string;revision:number;status:string;executionId:string;finalMetrics:Record<string,EvaluationMetricValue>}
export interface DemoResults {
  artifactVersion:string;packId:string;baselineId:string;generatedAt:string;source:string;interpretation:string
  fleets:Record<string,{vehicleCount:number;ratedPayloadTonnes:number;cargoVolumeCubicMeters:number}>
  entries:DemoEntry[];comparisons:{title:string;left:string;right:string;leftExecutionId:string;rightExecutionId:string}[]
}
export function parseDemoResults(value:DemoResults):DemoResults {
  if(value.artifactVersion!=='sandbox-demo-results/v1' || value.source!=='REAL_SANDBOX_EXECUTIONS'
    || value.packId!=='developer-demo-v1' || value.entries?.length!==6 || value.comparisons?.length!==5)throw Error('演示目录格式无效')
  const ids=new Set<string>(),executions=new Set<string>()
  for(const entry of value.entries) {
    if(ids.has(entry.id) || executions.has(entry.executionId) || entry.status!=='VERIFIED'
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(entry.executionId)
      || !entry.finalMetrics || !value.fleets[entry.scenarioKey])throw Error('演示执行记录不完整')
    ids.add(entry.id);executions.add(entry.executionId)
  }
  for(const pair of value.comparisons) {
    if(pair.left===pair.right || value.entries.find(e=>e.id===pair.left)?.executionId!==pair.leftExecutionId
      || value.entries.find(e=>e.id===pair.right)?.executionId!==pair.rightExecutionId)throw Error('演示对比身份不一致')
  }
  return value
}
