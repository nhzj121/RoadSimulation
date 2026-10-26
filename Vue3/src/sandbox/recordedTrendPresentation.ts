import type { EvaluationSnapshot } from '../types/evaluation'

export interface RecordedTrendPoint {
  loopIndex: number; simTime: string; contract: string; snapshotStatus: string
  status: string; reason: string; unit: string; raw: number | null; value: number | null
}
export interface RecordedTrendModel {
  name: string; axisUnit: string; points: RecordedTrendPoint[]; message: string
  range: { min: number; max: number; interval:number }; available: number
  plotStartIndex: number; bands: RecordedTrendBand[]
}
export interface RecordedTrendBand {
  kind: 'high' | 'middle' | 'main'; min: number; max: number; interval: number
}
const objectiveUnits: Record<string,string> = {
  emptyMileageRatio:'ratio', capacityWasteRatio:'ratio', unmetDemandRatio:'ratio', carbonIntensity:'kgCO2e/(t·km)',
}
const escape = (v:string) => v.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#039;')

/** Display bounds only: never change, discard or clamp a recorded value. */
export function recordedTrendRange(values:number[]) {
  if (!values.length) return {min:0,max:1,interval:.25}
  let min=Math.min(...values),max=Math.max(...values)
  const span=max-min
  if (span===0) {
    const padding=Math.abs(min)*.1 || 1
    min=Math.max(0,min-padding);max=max+padding
  } else {
    // Zero is the physical floor of these four objectives. Near-zero data may
    // still be close to the baseline: never invent negative ticks to lift it.
    min=Math.max(0,min-span*.15);max=max+span*.1
  }
  const rawStep=(max-min)/4,power=10**Math.floor(Math.log10(rawStep)),normalized=rawStep/power
  const interval=([1,2,2.5,5,10].find(v=>v>=normalized) || 10)*power
  return {min:Math.max(0,Math.floor(min/interval)*interval),max:Math.ceil(max/interval)*interval,interval}
}
export function recordedAxisNumber(value:number) {
  return value!==0 && (Math.abs(value)<.001 || Math.abs(value)>=1e6)
    ? value.toExponential(2) : new Intl.NumberFormat('zh-CN',{maximumFractionDigits:4}).format(value)
}
export function buildRecordedTrend(snapshots:EvaluationSnapshot[],id:string):RecordedTrendModel {
  const first=snapshots.find(s=>s.metrics[id])?.metrics[id],unit=objectiveUnits[id]
  const compatible=!!unit && snapshots.every(s=>s.contractVersion==='1.9' && (!s.metrics[id] || s.metrics[id].metricId===id && s.metrics[id].unit===unit))
  const points=snapshots.map(s=>{
    const m=s.metrics[id],raw=m?.value ?? null
    const usable=compatible && m?.status==='AVAILABLE' && raw!==null && Number.isFinite(raw) && raw>=0 && (unit!=='ratio' || raw<=1)
    return {loopIndex:s.loopIndex,simTime:s.simTime,contract:s.contractVersion,snapshotStatus:s.snapshotStatus,
      status:m?.status || 'MISSING',reason:m?.reason || '',unit:m?.unit || '',raw,value:usable ? (unit==='ratio'?raw!*100:raw) : null}
  })
  const values=points.flatMap(p=>p.value===null?[]:[p.value])
  // Keep every raw record for inspection/export. Only the plotted leading
  // unavailable prefix is trimmed; real zeroes and interior holes stay put.
  const plotStartIndex=Math.max(0,points.findIndex(p=>p.value!==null))
  const bands:RecordedTrendBand[]=[]
  if(id==='carbonIntensity' && values.some(v=>v>2)) {
    const high=values.filter(v=>v>10),middle=values.filter(v=>v>2 && v<=10)
    if(high.length) {
      const bounds=recordedTrendRange(high)
      bands.push({kind:'high',...bounds,min:Math.max(10,bounds.min)})
    }
    if(middle.length) bands.push({kind:'middle',min:Math.max(2,Math.floor(Math.min(...middle)/2)*2),max:10,interval:2})
    bands.push({kind:'main',min:0,max:2,interval:.5})
  }
  return {name:first?.displayName || id,axisUnit:unit==='ratio'?'%':unit || '',points,range:recordedTrendRange(values),available:values.length,
    plotStartIndex,bands,
    message:!compatible?'历史契约、指标身份或单位与当前绘图规则不兼容；请查看下方记录原值，不共用数值轴。':!values.length?'本区间没有可绘制的有效值；状态和原因保留在记录明细中。':''}
}
export function recordedPointTooltip(model:RecordedTrendModel,index:number) {
  const p=model.points[index];if(!p)return ''
  const display=p.value===null?'不可绘制（不补0）':`${recordedAxisNumber(p.value)} ${model.axisUnit}`
  return [`<strong>${escape(model.name)}</strong>`,`第${p.loopIndex+1}轮（loopIndex=${p.loopIndex}）`,
    `仿真时间：${escape(p.simTime)}`,`显示值：${escape(display)}`,
    `记录原值：${escape(p.raw===null?'—':String(p.raw))} ${escape(p.unit)}`,
    `状态：${escape(p.status)} · 快照 ${escape(p.snapshotStatus)}`,
    ...(p.reason?[`原因：${escape(p.reason)}`]:[])].join('<br/>')
}
export function recordedTrendOption(model:RecordedTrendModel,size:{width?:number;height?:number}={}) {
  const plotted=model.points.slice(model.plotStartIndex),last=plotted.length-1,labelStep=Math.max(1,Math.ceil(plotted.length/8))
  const tooltip={trigger:'axis',confine:true,axisPointer:{type:'line',snap:true},formatter:(params:any)=>{
    const values=Array.isArray(params)?params:[params]
    const p=values.find((p:any)=>p?.value!==null && p?.value!==undefined) || values[0]
    return recordedPointTooltip(model,(p?.dataIndex ?? -1)<0?-1:p.dataIndex+model.plotStartIndex)
  }}
  const xAxis={type:'category',name:'展示轮次',nameLocation:'middle',nameGap:30,boundaryGap:false,
    data:plotted.map(p=>String(p.loopIndex+1)),axisLabel:{hideOverlap:true,showMinLabel:true,showMaxLabel:true,
      interval:(index:number)=>index===0 || index===last || index%labelStep===0 && last-index>=labelStep*.75},
    axisLine:{lineStyle:{color:'#bac8d7'}},axisTick:{alignWithLabel:true}}
  const yAxis={type:'value',name:model.axisUnit,scale:true,min:model.range.min,max:model.range.max,interval:model.range.interval,splitNumber:4,
    axisLabel:{formatter:recordedAxisNumber,color:'#718096'},splitLine:{lineStyle:{color:'#e9eef4'}},axisLine:{show:true,lineStyle:{color:'#bac8d7'}}}
  if(model.bands.length) {
    const three=model.bands.length===3,tops=three?[7,24,44]:[8,35],heights=three?[10,12,46]:[15,55]
    const width=size.width || 640,height=size.height || (three?620:500)
    const caption=(band:RecordedTrendBand)=>band.kind==='main'?'0～2 重点展示区':band.kind==='high'?'高值压缩区':'中间压缩区'
    const matches=(value:number|null,band:RecordedTrendBand)=>value!==null && (band.kind==='main'?value<=2:band.kind==='middle'?value>2 && value<=10:value>10)
    const graphic:any[]=model.bands.map((band,i)=>({type:'text',right:16,top:`${tops[i]-2.5}%`,silent:true,
      style:{text:caption(band),fill:'#7e8fa3',font:'11px Microsoft YaHei, sans-serif'}}))
    model.bands.slice(0,-1).forEach((band,i)=>{
      const next=model.bands[i+1],top=tops[i]+heights[i]+1.5
      graphic.push({type:'polyline',left:48,top:`${top}%`,silent:true,shape:{points:[[0,0],[9,4],[0,8],[9,12]]},style:{stroke:'#8193a7',lineWidth:1.5,fill:null}},
        {type:'text',right:16,top:`${top}%`,silent:true,style:{text:band.min>next.max?`断轴：省略${recordedAxisNumber(next.max)}～${recordedAxisNumber(band.min)}`:'纵轴分段 · 比例不同',fill:'#8193a7',font:'11px Microsoft YaHei, sans-serif'}})
    })
    return {animation:false,aria:{enabled:true,description:`${model.name}，共享真实轮次横轴，纵轴分段展示，跨段斜率不可比较`},color:['#337ecc'],tooltip,
      axisPointer:{link:[{xAxisIndex:'all'}]},graphic,
      grid:model.bands.map((_,i)=>({left:62,right:22,top:`${tops[i]}%`,height:`${heights[i]}%`,containLabel:false})),
      xAxis:model.bands.map((_,i)=>({...xAxis,gridIndex:i,name:i===model.bands.length-1?'展示轮次':'',
        axisLabel:{...xAxis.axisLabel,show:i===model.bands.length-1},axisLine:{...xAxis.axisLine,show:i===model.bands.length-1},axisTick:{show:i===model.bands.length-1}})),
      yAxis:model.bands.map((band,i)=>({...yAxis,gridIndex:i,min:band.min,max:band.max,interval:band.interval,name:i===0?model.axisUnit:''})),
      series:model.bands.map((band,i)=>{
        const ordered=plotted.map((p,index)=>({p,index})).filter(v=>matches(v.p.value,band)).sort((a,b)=>b.p.value!-a.p.value!)
        const labelHeight=Math.max(0,height*heights[i]/100-16)
        const slots=Math.max(1,Math.floor(labelHeight/17)+1),selected=ordered.length<=slots?ordered
          :Array.from({length:slots},(_,n)=>ordered[Math.round(n*(ordered.length-1)/Math.max(1,slots-1))])
        const row=(index:number)=>selected.findIndex(p=>p.index===index)
        return {type:'line',name:model.name,xAxisIndex:i,yAxisIndex:i,smooth:false,connectNulls:false,
        showSymbol:true,symbol:'circle',symbolSize:band.kind==='main'?4:6,
        data:plotted.map(p=>matches(p.value,band)?p.value:null),
        lineStyle:{width:2,type:band.kind==='main'?'solid':'dashed'},
        label:band.kind==='main'?{show:false}:{show:true,position:'right',distance:9,color:'#425970',fontSize:11,
          formatter:(p:any)=>row(p.dataIndex)<0?'':`第${plotted[p.dataIndex].loopIndex+1}轮 ${recordedAxisNumber(plotted[p.dataIndex].value!)}`},
        labelLayout:band.kind==='main'?undefined:(p:any)=>{
          const rank=row(p.dataIndex),px=62+p.dataIndex/Math.max(1,last)*(width-84)
          const py=(tops[i]+(band.max-plotted[p.dataIndex].value!)/(band.max-band.min)*heights[i])/100*height
          const x=Math.min(width-130,Math.max(110,px+24)),y=tops[i]/100*height+8+Math.max(0,rank)*Math.min(20,labelHeight/Math.max(1,selected.length-1))
          return {x,y,align:'left',verticalAlign:'middle',hideOverlap:false,
            labelLinePoints:rank<0?[]:[[px+4,py],[x-8,y],[x-2,y]]}
        },labelLine:{show:true,lineStyle:{color:'#acbac9'}},emphasis:{scale:true}}
      })}
  }
  return {animation:false,aria:{enabled:true,description:model.name},color:['#337ecc'],
    grid:{left:20,right:24,top:40,bottom:48,containLabel:true},
    tooltip,xAxis,yAxis,
    series:[{type:'line',name:model.name,smooth:false,connectNulls:false,showSymbol:true,symbol:'circle',symbolSize:5,
      data:plotted.map(p=>p.value),lineStyle:{width:2},emphasis:{scale:true}}]}
}
