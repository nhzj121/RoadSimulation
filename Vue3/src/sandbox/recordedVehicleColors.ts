import type {DomainChartModel} from '../utils/evaluationDomainPresentation'
import {VISIBLE_VEHICLE_GROUPS} from '../utils/simulationWorkspacePresentation'

// Reuse only the real-time palette, never its visible-vehicle data or clock.
const groupColor=(status:string)=>VISIBLE_VEHICLE_GROUPS.find(g=>g.statuses.includes(status))!.color
export const recordedVehicleColors={
  empty:groupColor('ORDER_DRIVING'),loaded:groupColor('TRANSPORT_DRIVING'),
  loading:groupColor('LOADING'),unloading:groupColor('UNLOADING'),
  other:VISIBLE_VEHICLE_GROUPS.find(g=>g.label==='其他')!.color,inactive:'#94A3B8',active:'#335A7E',
  loadCompleted:'#C2410C',unloadCompleted:'#A16207',
}
export function colorRecordedVehicleState(model:DomainChartModel):DomainChartModel {
  const c=recordedVehicleColors
  const colors:Record<string,string>={'执行中':c.active,'非执行中':c.inactive,'空载行驶':c.empty,'有载行驶':c.loaded,'执行中其他':c.other}
  return {...model,inner:model.inner.map(p=>({...p,color:colors[p.label] || p.color})),items:model.items.map(p=>({...p,color:colors[p.label] || p.color}))}
}
