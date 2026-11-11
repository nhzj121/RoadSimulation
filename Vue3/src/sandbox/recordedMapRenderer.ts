import type { InjectionKey } from 'vue'
import type {RecordedPoint,RecordedPoi,RecordedLeg,RecordedVehicle,SandboxMapSnapshot} from '../types/sandbox-map'
import type {ReplayRoad} from './roadReplayGeometry'
import type {ReplayVehicle} from './roadReplayTimeline'
import {createVehicleMapMarker, updateVehicleMapMarker} from '../utils/vehicleMapMarker'
import {vehicleStatusPresentation} from '../utils/vehicleStatusPresentation'
export interface RecordedMapPort {
  clear():void
  poi(poi:RecordedPoi):void
  vehicle(vehicle:RecordedVehicle,selected:boolean,onSelect:()=>void):void
  relation(leg:RecordedLeg,selected:boolean,onSelect:()=>void):void
  replay?(vehicles:readonly ReplayVehicle[], roads:ReadonlyMap<number,ReplayRoad>, pois:RecordedPoi[],
    selected:number|null, onSelect:(id:number)=>void):void
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
  const replayMarkers=new Map<number,{marker:any;position:RecordedPoint|null}>()
  const replayLines=new Map<ReplayRoad,any>()
  const replayPoiMarkers=new Map<number,any>()
  let replayRoads:ReadonlyMap<number,ReplayRoad>|undefined
  let replayPois:RecordedPoi[]|undefined
  const point=(p:RecordedPoint)=>[p.longitude,p.latitude]
  function addPoi(poi:RecordedPoi){
    // Never interpret database names as markup.
    const label=`${poi.name} · POI ${poi.id}`
    const text=document.createElement('span');text.title=label;text.textContent='•'
    text.style.cssText='display:block;padding:3px 5px;border:1px solid white;border-radius:50%;color:white;background:#63768a;font-size:12px;white-space:nowrap'
    const marker=new amap.Marker({position:point(poi),content:text,title:label,anchor:'bottom-center',zIndex:100})
    overlays.push(marker);map.add(marker)
    return marker
  }
  function appearance(vehicle:ReplayVehicle,selected:boolean){
    return {
      status:vehicle.currentStatus??'UNKNOWN', vehicleId:vehicle.id, selected,
      title:`${vehicle.licensePlate||`车辆${vehicle.id}`} - ${vehicleStatusPresentation(vehicle.currentStatus).text}`,
    }
  }
  function addVehicle(vehicle:ReplayVehicle,position:RecordedPoint,selected:boolean,onSelect:()=>void){
    const look=appearance(vehicle,selected)
    const marker=createVehicleMapMarker<any>(amap,look,{position:point(position),title:look.title,anchor:'center',zIndex:120})
    marker.on('click',onSelect);overlays.push(marker);map.add(marker)
    return marker
  }
  const port:RecordedMapPort={
    clear(){map.clearMap();overlays.length=0;replayMarkers.clear();replayLines.clear();replayPoiMarkers.clear();replayRoads=undefined;replayPois=undefined},
    poi:addPoi,
    vehicle(v,selected,click){if(v.position)addVehicle(v,v.position,selected,click)},
    relation(leg,selected,click){if(!leg.from||!leg.to)return;const line=new amap.Polyline({path:[point(leg.from),point(leg.to)],strokeColor:selected?'#b64214':leg.progressStatus==='RUNNING'?'#2473b4':'#8c9caa',strokeWeight:selected?5:2,strokeStyle:'dashed',zIndex:selected?90:50});line.on('click',click);overlays.push(line);map.add(line)},
    replay(vehicles,roads,pois,selected,select){
      if(!replayRoads)port.clear()
      if(replayRoads!==roads){
        replayRoads=roads
        const unique=new Set(roads.values())
        for(const [road,line] of replayLines)if(!unique.has(road)){removeOverlay(line);replayLines.delete(road)}
        for(const road of unique){
          if(replayLines.has(road))continue
          const line=new amap.Polyline({path:road.points.map(point),strokeColor:'#2473b4',strokeWeight:3,strokeOpacity:0.7,zIndex:50})
          replayLines.set(road,line);overlays.push(line);map.add(line)
        }
      }
      if(replayPois!==pois){
        replayPois=pois
        const ids=new Set(pois.map(poi=>poi.id))
        for(const [id,marker] of replayPoiMarkers)if(!ids.has(id)){removeOverlay(marker);replayPoiMarkers.delete(id)}
        for(const poi of pois)if(!replayPoiMarkers.has(poi.id))replayPoiMarkers.set(poi.id,addPoi(poi))
      }
      const vehicleIds=new Set(vehicles.map(vehicle=>vehicle.id))
      for(const [id,entry] of replayMarkers)if(!vehicleIds.has(id)){
        removeOverlay(entry.marker);replayMarkers.delete(id)
      }
      for(const vehicle of vehicles){
        const existing=replayMarkers.get(vehicle.id)
        if(!vehicle.position){
          if(existing?.position){existing.marker.hide();existing.position=null}
          continue
        }
        const active=vehicle.id===selected
        if(!existing){
          replayMarkers.set(vehicle.id,{marker:addVehicle(vehicle,vehicle.position,active,()=>select(vehicle.id)),position:vehicle.position})
          continue
        }
        if(!existing.position||existing.position.longitude!==vehicle.position.longitude||existing.position.latitude!==vehicle.position.latitude){
          existing.marker.setPosition(point(vehicle.position))
          if(!existing.position)existing.marker.show()
          existing.position=vehicle.position
        }
        updateVehicleMapMarker(existing.marker,appearance(vehicle,active))
      }
    },
    fit(){if(overlays.length)map.setFitView(overlays,false,[35,35,35,35])},
    destroy(){map.destroy();overlays.length=0;replayMarkers.clear();replayLines.clear();replayPoiMarkers.clear();replayRoads=undefined;replayPois=undefined}
  }
  return port
  function removeOverlay(overlay:any){
    map.remove(overlay)
    overlays.splice(overlays.indexOf(overlay),1)
  }
}
