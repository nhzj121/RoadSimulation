<template>
  <section class="recorded-map">
    <p>虚线仅表示记录的运输端点关系，不是实际道路路线。行驶／未知位置车辆不绘制推算定位点，可从下方车辆列表查看。</p>
    <p>灰点为POI，绿“车”为停靠车辆，橙色为选中车辆及其运输段；悬停标记可查看名称。全部POI模式不展开常驻文字标签。</p>
    <el-alert v-if="mapError" :title="mapError" type="warning" :closable="false" />
    <p v-if="loading">正在加载底图；已记录的车辆详情和评价仍可查看。</p>
    <el-button v-if="mapError" :disabled="loading" @click="reload">重新加载底图</el-button>
    <div :style="{visibility: mapError || loading ? 'hidden' : 'visible'}" ref="element" class="map-canvas" role="img" aria-label="沙箱已记录轮次地图" />
    <el-button :disabled="!ready" @click="fit">定位全部已绘制记录</el-button>
  </section>
</template>
<script setup lang="ts">
import {ref,inject,onMounted,onBeforeUnmount,watch} from 'vue'
import type {SandboxMapSnapshot,RecordedPoi} from '../../types/sandbox-map'
import {recordedMapFactoryKey,drawRecordedMap} from '../../sandbox/recordedMapRenderer'
import type {RecordedMapPort} from '../../sandbox/recordedMapRenderer'
const props=defineProps<{snapshot:SandboxMapSnapshot;pois:RecordedPoi[];selectedVehicle:number|null}>()
const emit=defineEmits<{(e:'select-vehicle',id:number):void}>()
const factory=inject(recordedMapFactoryKey,null),element=ref<HTMLElement|null>(null),mapError=ref(''),ready=ref(false),loading=ref(false)
let port:RecordedMapPort|undefined,disposed=false,generation=0
function release(value:RecordedMapPort|undefined){try{value?.destroy()}catch{/* Cleanup failure cannot start business work or conceal a loader error. */}}
function render(initial=false){if(port&&!disposed){const active=port,current=generation;try{mapError.value='';drawRecordedMap(active,props.snapshot,props.pois,props.selectedVehicle,id=>{if(!disposed&&port===active&&generation===current)emit('select-vehicle',id)},initial);ready.value=true}catch{mapError.value='本轮地图未完整绘制，隐藏不完整画面；该轮记录详情仍可查看，不改变后台实验。';ready.value=false;try{active.clear()}catch{}}}}
function fit(){try{port?.fit()}catch{mapError.value='底图定位失败；不完整画面已隐藏，记录详情不受影响。';ready.value=false}}
async function reload(){
  if(disposed||loading.value)return
  if(!factory){mapError.value='底图配置尚未接入；记录详情可查看，不会回退到普通仿真地图。';return}
  const current=++generation;loading.value=true;ready.value=false
  const previous=port;port=undefined;release(previous)
  try{const loaded=await factory(element.value!);if(disposed||current!==generation){release(loaded);return}port=loaded;render(true)}
  catch{if(!disposed&&current===generation)mapError.value='底图加载失败；可单独重新加载底图，不会改变后台实验或历史记录。'}
  finally{if(!disposed&&current===generation)loading.value=false}
}
onMounted(reload)
watch(()=>[props.snapshot,props.pois,props.selectedVehicle],()=>render())
onBeforeUnmount(()=>{disposed=true;generation++;ready.value=false;loading.value=false;const previous=port;port=undefined;release(previous)})
</script>
<style scoped>.map-canvas{height:480px;min-height:320px;border:1px solid #cbd5e1;border-radius:8px;background:#eef3f8}.recorded-map p{color:#526274;font-size:13px}</style>
