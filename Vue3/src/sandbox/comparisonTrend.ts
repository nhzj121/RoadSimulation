import type { EvaluationSnapshot } from '../types/evaluation'
import {comparisonColors} from './comparison'

export interface ComparisonPoint {loopIndex:number;simTime:string;raw:number|null;value:number|null;unit:string;status:string;reason:string;contract:string;snapshotStatus:string}
export interface ComparisonTrend {name:string;unit:string;boolean:boolean;left:ComparisonPoint[];right:ComparisonPoint[];message:string}
const modelMetrics=new Set(['carbonIntensity','vehicleTotalEnergy','vehicleTotalEmissionKg','vehicleEmissionIntensity','taskEmissionKg','taskEmissionIntensity','environmentEnergyFactor'])
const threshold:Record<string,string>={vehicleLowLoadRatio:'lowLoadRatioThreshold',taskLowLoadCount:'lowLoadRatioThreshold',vehicleFullLoadRatio:'fullLoadRatioThreshold',waitingServiceCompliant:'maxServiceWaitSeconds'}
const escape=(text:string)=>text.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#039;')
const stable=(value:unknown):string=>JSON.stringify(value,(_key,v)=>v && typeof v==='object' && !Array.isArray(v)?Object.fromEntries(Object.keys(v).sort().map(k=>[k,v[k]])):v)
export function buildComparisonTrend(left:EvaluationSnapshot[],right:EvaluationSnapshot[],id:string):ComparisonTrend {
  const all=[...left,...right],metric=all.find(s=>s.metrics[id])?.metrics[id],unit=metric?.unit||''
  const anchor=all[0],field=threshold[id]
  const compatible=!!metric && left.length===right.length && left.length>0 && all.every(s=>s.contractVersion==='1.9'
    && (!s.metrics[id] || s.metrics[id].metricId===id && s.metrics[id].unit===unit && s.metrics[id].category===metric.category)
    && (!modelMetrics.has(id) || !!s.energyEmissionModel && stable(s.energyEmissionModel)===stable(anchor.energyEmissionModel))
    && (!field || typeof s.thresholds?.[field as keyof typeof s.thresholds]==='number' && s.thresholds[field as keyof typeof s.thresholds]===anchor.thresholds?.[field as keyof typeof s.thresholds]))
  const points=(items:EvaluationSnapshot[])=>items.map(s=>{
    const m=s.metrics[id],raw=m?.value??null
    const usable=compatible && m?.status==='AVAILABLE' && raw!==null && Number.isFinite(raw) && (unit!=='boolean' || raw===0 || raw===1)
    return {loopIndex:s.loopIndex,simTime:s.simTime,raw,value:usable?(unit==='ratio'?raw!*100:raw):null,unit:m?.unit||'',
      status:m?.status||'MISSING',reason:m?.reason||'',contract:s.contractVersion,snapshotStatus:s.snapshotStatus}
  })
  return {name:metric?.displayName||id,unit:unit==='ratio'?'%':unit,boolean:unit==='boolean',left:points(left),right:points(right),message:compatible?'':'指标契约、单位、阈值或模型不兼容；仅保留原始记录，不共用数值轴。'}
}
export function comparisonTooltip(model:ComparisonTrend,index:number) {
  const a=model.left[index],b=model.right[index];if(!a || !b)return ''
  const lines=[`<strong>${escape(model.name)}</strong>`,`第${a.loopIndex+1}轮 · ${escape(a.simTime)}`]
  for(const [label,p] of [['A',a],['B',b]] as const)lines.push(`${label}：${p.raw===null?'—':escape(String(p.raw))} ${escape(p.unit)} · ${escape(p.status)} · ${escape(p.snapshotStatus)}${p.reason?` · ${escape(p.reason)}`:''}`)
  if(!model.boolean && a.value!==null && b.value!==null){const delta=b.value-a.value;lines.push(`差值B−A：${escape(String(Number(delta.toPrecision(10))))} ${model.unit==='%'?'个百分点':escape(model.unit)}`)}
  return lines.join('<br/>')
}
export function comparisonTrendOption(model:ComparisonTrend,group='sandbox-comparison') {
  return {animation:false,aria:{enabled:true,description:`${model.name}，A与B完整逐轮对比`},color:comparisonColors,
    grid:{left:18,right:25,top:45,bottom:70,containLabel:true},legend:{data:['A · 参考方案','B · 对照方案']},
    tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line',snap:true},formatter:(params:any)=>comparisonTooltip(model,(Array.isArray(params)?params[0]:params)?.dataIndex??-1)},
    xAxis:{type:'category',boundaryGap:false,name:'展示轮次',nameLocation:'middle',nameGap:28,data:model.left.map(p=>String(p.loopIndex+1)),axisLabel:{showMinLabel:true,showMaxLabel:true,hideOverlap:true}},
    yAxis:{type:'value',name:model.unit,scale:true},
    dataZoom:[{type:'inside',start:0,end:100},{type:'slider',start:0,end:100,bottom:6}],
    series:[model.left,model.right].map((points,i)=>({name:i===0?'A · 参考方案':'B · 对照方案',type:'line',smooth:false,connectNulls:false,
      showSymbol:points.length<=100,symbolSize:5,data:points.map(p=>p.value),lineStyle:{width:2,type:i===0?'solid':'dashed'}})),group}
}
