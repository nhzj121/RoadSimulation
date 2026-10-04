import type { InjectionKey } from 'vue'
import type {RecordedPoint,RecordedPoi,RecordedLeg,RecordedVehicle,SandboxMapSnapshot} from '../types/sandbox-map'
export interface RecordedMapPort {
  clear():void
  poi(poi:RecordedPoi):void
  vehicle(vehicle:RecordedVehicle,selected:boolean,onSelect:()=>void):void
  relation(leg:RecordedLeg,selected:boolean,onSelect:()=>void):void
  fit():void
  destroy():void
}
export type RecordedMapFactory=(element:HTMLElement)=>Promise<RecordedMapPort>
export const recordedMapFactoryKey:InjectionKey<RecordedMapFactory>=Symbol('sandbox-readonly-map-factory')
/** A display-only port: no arriving/loading callbacks, route planning or simulation services. */
export function drawRecordedMap(port:RecordedMapPort,snapshot:SandboxMapSnapshot,pois:RecordedPoi[],selectedVehicle:number|null,onSelect:(id:number)=>void,fit=false){
  port.clear()
  for(const poi of pois)port.poi(poi)
  for(const leg of snapshot.projection.legs)if(leg.from&&leg.to)port.relation(leg,leg.vehicleId===selectedVehicle,()=>{if(leg.vehicleId!==null)onSelect(leg.vehicleId)})
  for(const vehicle of snapshot.projection.vehicles)if(vehicle.positionSource==='POI_LINK'&&vehicle.position)port.vehicle(vehicle,vehicle.id===selectedVehicle,()=>onSelect(vehicle.id))
  if(fit)port.fit()
}
export function makeAmapPort(amap:any,element:HTMLElement):RecordedMapPort {
  const map=new amap.Map(element,{viewMode:'2D',zoom:11}),overlays:any[]=[]
  const point=(p:RecordedPoint)=>[p.longitude,p.latitude]
  function marker(position:RecordedPoint,label:string,color:string,click?:()=>void,expanded=false){
    // Never interpret database names as markup.
    const text=document.createElement('span');text.title=label;text.textContent=expanded?label:click?'车':'•';text.style.cssText=`display:block;padding:3px 5px;border:1px solid white;border-radius:${expanded?'4px':'50%'};color:white;background:${color};font-size:12px;white-space:nowrap`
    const marker=new amap.Marker({position:point(position),content:text,title:label,anchor:'bottom-center',zIndex:expanded?150:click?120:100});if(click)marker.on('click',click);overlays.push(marker);map.add(marker)
  }
  return{
    clear(){map.clearMap();overlays.length=0},
    poi(p){marker(p,`${p.name} · POI ${p.id}`,'#63768a')},
    vehicle(v,selected,click){if(v.position)marker(v.position,`${v.licensePlate||`车辆${v.id}`} · ${v.currentStatus||'未知'}`,selected?'#b64214':'#147862',click,selected)},
    relation(leg,selected,click){if(!leg.from||!leg.to)return;const line=new amap.Polyline({path:[point(leg.from),point(leg.to)],strokeColor:selected?'#b64214':leg.progressStatus==='RUNNING'?'#2473b4':'#8c9caa',strokeWeight:selected?5:2,strokeStyle:'dashed',zIndex:selected?90:50});line.on('click',click);overlays.push(line);map.add(line)},
    fit(){if(overlays.length)map.setFitView(overlays,false,[35,35,35,35])},
    destroy(){map.destroy();overlays.length=0}
  }
}
