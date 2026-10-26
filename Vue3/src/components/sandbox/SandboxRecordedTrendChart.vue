<template>
  <section class="recorded-trend" :aria-label="model.name">
    <h3>{{ model.name }}</h3>
    <p v-if="model.available" class="recorded-trend__caption">{{ model.bands.length ? '纵轴分段压缩，重点展示0～2；各段比例不同，不跨断轴比较斜率' : '线性自适应纵轴' }} · {{ model.axisUnit }} · 悬停查看逐轮原值</p>
    <p v-if="model.available && model.plotStartIndex" class="recorded-trend__caption">从第{{ model.points[model.plotStartIndex].loopIndex+1 }}轮首个可用记录起绘制；前置不可用记录仍保留在明细中，不补0。</p>
    <p v-if="model.message" class="recorded-trend__notice" role="status">{{ model.message }}</p>
    <p v-else-if="warning" class="recorded-trend__notice" role="status">{{ warning }}；下方原始记录仍可查看。<button type="button" @click="renderChart">重试绘图</button></p>
    <div v-show="model.available && !warning" ref="chartHost" class="recorded-trend__canvas" :class="{'recorded-trend__canvas--segmented':model.bands.length>0,'recorded-trend__canvas--three':model.bands.length===3}" role="img" :aria-label="`${model.name}逐轮趋势，单位${model.axisUnit}`" />
    <details v-if="model.points.length"><summary>逐轮记录明细（{{ model.points.length }}轮，含不可用记录）</summary>
      <div class="recorded-trend__table"><table><thead><tr><th>轮次</th><th>仿真时间</th><th>记录原值／单位</th><th>状态／原因</th></tr></thead><tbody>
        <tr v-for="p in model.points" :key="p.loopIndex"><td>第{{ p.loopIndex+1 }}轮<small>loopIndex={{ p.loopIndex }}</small></td><td>{{ p.simTime }}<small>{{ p.contract }} · {{ p.snapshotStatus }}</small></td><td>{{ p.raw === null ? '—' : String(p.raw) }} {{ p.unit }}</td><td>{{ p.status }}<small>{{ p.reason || '无额外原因' }}{{ p.value === null ? '；不绘制，不补0' : '' }}</small></td></tr>
      </tbody></table></div>
    </details>
  </section>
</template>
<script setup lang="ts">
import {computed,nextTick,onMounted,onBeforeUnmount,ref,watch} from 'vue'
import type {EvaluationSnapshot} from '../../types/evaluation'
import {buildRecordedTrend,recordedTrendOption} from '../../sandbox/recordedTrendPresentation'
const props=defineProps<{snapshots:EvaluationSnapshot[];metricId:string}>()
const model=computed(()=>buildRecordedTrend(props.snapshots,props.metricId))
const chartHost=ref<HTMLElement|null>(null),warning=ref('')
interface Chart {setOption:(option:unknown,replace:boolean)=>void;resize:()=>void;dispose:()=>void}
let chart:Chart|null=null,observer:ResizeObserver|null=null,mounted=false,token=0
function dispose(){observer?.disconnect();observer=null;chart?.dispose();chart=null}
async function renderChart(){
  const current=++token;await nextTick();if(!mounted || current!==token)return
  if(!model.value.available || !chartHost.value){dispose();warning.value='';return}
  const runtime=(window as Window & {echarts?:{init:(el:HTMLElement)=>Chart}}).echarts
  if(!runtime){dispose();warning.value='图表运行库不可用';return}
  try{
    warning.value='';await nextTick();if(!mounted || current!==token)return
    if(!chart)chart=runtime.init(chartHost.value)
    chart.setOption(recordedTrendOption(model.value,{width:chartHost.value.clientWidth,height:chartHost.value.clientHeight}),true);chart.resize()
    observer?.disconnect()
    if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(()=>{
      if(!mounted)return
      try{
        chart?.resize()
        if(model.value.bands.length && chartHost.value)chart?.setOption(recordedTrendOption(model.value,{width:chartHost.value.clientWidth,height:chartHost.value.clientHeight}),true)
      }catch{dispose();warning.value='当前图表无法绘制'}
    });observer.observe(chartHost.value)}
  }catch{dispose();warning.value='当前图表无法绘制'}
}
watch(model,()=>void renderChart())
onMounted(()=>{mounted=true;void renderChart()})
onBeforeUnmount(()=>{mounted=false;token++;dispose()})
</script>
<style scoped>
.recorded-trend{min-width:0;}.recorded-trend h3{font-size:14px;line-height:1.7;color:#2d4661;}
.recorded-trend__caption{font-size:11px;color:#8290a1;line-height:1.8;margin:6px 0;}
.recorded-trend__canvas{width:100%;height:280px;margin-bottom:16px;}
.recorded-trend__canvas--segmented{height:500px;}.recorded-trend__canvas--three{height:620px;}
.recorded-trend__notice{min-height:160px;display:grid;place-content:center;padding:20px;border:1px dashed #dce5ee;border-radius:7px;color:#856529;font-size:12px;line-height:1.8;overflow-wrap:anywhere;}
button{font:inherit;color:#337ecc;border:1px solid #dce5ee;border-radius:5px;background:#fff;cursor:pointer;padding:4px 8px;}
summary{font-size:12px;color:#60758c;cursor:pointer;line-height:1.8;}summary:focus-visible,button:focus-visible{outline:2px solid #409eff;outline-offset:2px;}
.recorded-trend__table{overflow-x:auto;margin-top:10px;border:1px solid #e4eaf1;border-radius:5px;max-height:340px;}
table{width:100%;min-width:580px;border-collapse:collapse;font-size:11px;}td,th{padding:9px 10px;text-align:left;border-bottom:1px solid #edf0f4;overflow-wrap:anywhere;vertical-align:top;}th{color:#718096;background:#f7f9fc;}small{display:block;color:#8290a1;margin-top:4px;}
@media(max-width:720px){.recorded-trend__canvas{height:250px;}.recorded-trend__canvas--segmented{height:480px;}.recorded-trend__canvas--three{height:590px;}}
</style>
