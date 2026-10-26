<template>
  <section class="comparison-trend" :aria-label="`${model.name}完整趋势`"><h3>{{ model.name }}</h3>
    <p>完整记录 {{ model.left.length }}轮 · A蓝色／B橙色 · {{ model.boolean?'逐轮状态':'共同线性纵轴；不平滑、不补零' }}</p>
    <p v-if="model.message" role="status">{{ model.message }}</p>
    <template v-else-if="model.boolean"><div v-for="(points,i) in [model.left,model.right]" :key="i" class="state-row"><strong>{{ i===0?'A':'B' }}</strong><div class="state-strip"><span v-for="p in points" :key="p.loopIndex" :class="p.value===null?'missing':p.value===1?'met':'unmet'" :title="`第${p.loopIndex+1}轮 ${p.value===null?'不可用':p.value===1?'满足':'不满足'} · ${p.status} · ${p.reason}`">{{ p.loopIndex+1 }}</span></div></div><p>绿色：满足；红色：不满足；灰色：不可用／未记录。</p></template>
    <template v-else><p v-if="warning" role="status">{{ warning }}；逐轮原值仍可查看。<button @click="renderChart">重试绘图</button></p><div v-show="!warning" ref="chartHost" class="chart" role="img" :aria-label="`${model.name}，A与B完整折线图`" /></template>
    <details><summary>双方逐轮原值与状态（{{ model.left.length }}轮）</summary><div class="raw-table"><table><thead><tr><th>轮次／时间</th><th>A原值／单位</th><th>A状态／原因</th><th>B原值／单位</th><th>B状态／原因</th></tr></thead><tbody><tr v-for="(p,i) in model.left" :key="p.loopIndex"><td>{{ p.loopIndex+1 }}<small>{{ p.simTime }}</small></td><td>{{ p.raw??'—' }} {{ p.unit }}</td><td>{{ p.status }}<small>{{ p.reason }} · {{ p.contract }} · {{ p.snapshotStatus }}</small></td><td>{{ model.right[i]?.raw??'—' }} {{ model.right[i]?.unit }}</td><td>{{ model.right[i]?.status }}<small>{{ model.right[i]?.reason }} · {{ model.right[i]?.snapshotStatus }}</small></td></tr></tbody></table></div></details>
  </section>
</template>
<script setup lang="ts">
import {computed,ref,nextTick,watch,onMounted,onBeforeUnmount} from 'vue'
import type {EvaluationSnapshot} from '../../types/evaluation'
import {buildComparisonTrend,comparisonTrendOption} from '../../sandbox/comparisonTrend'
const props=defineProps<{left:EvaluationSnapshot[];right:EvaluationSnapshot[];metricId:string;group?:string}>()
const model=computed(()=>buildComparisonTrend(props.left,props.right,props.metricId)),chartHost=ref<HTMLElement|null>(null),warning=ref('')
interface Chart{group:string;setOption:(option:unknown,replace:boolean)=>void;resize:()=>void;dispose:()=>void}
let chart:Chart|null=null,observer:ResizeObserver|null=null,mounted=false,token=0
function dispose(){observer?.disconnect();observer=null;chart?.dispose();chart=null}
async function renderChart(){
  const current=++token;await nextTick();if(!mounted || current!==token)return
  if(model.value.message || model.value.boolean || !chartHost.value){dispose();warning.value='';return}
  const runtime=(window as Window & {echarts?:{init:(el:HTMLElement)=>Chart;connect?:(group:string)=>void}}).echarts
  if(!runtime){dispose();warning.value='图表运行库不可用';return}
  try{
    warning.value='';await nextTick();if(!mounted || current!==token)return
    if(!chart)chart=runtime.init(chartHost.value)
    chart.group=props.group||'sandbox-comparison';chart.setOption(comparisonTrendOption(model.value),true);chart.resize();runtime.connect?.(chart.group)
    observer?.disconnect();if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(()=>{try{chart?.resize()}catch{dispose();warning.value='当前图表无法绘制'}});observer.observe(chartHost.value)}
  }catch{dispose();warning.value='当前图表无法绘制'}
}
watch(model,()=>void renderChart())
onMounted(()=>{mounted=true;void renderChart()})
onBeforeUnmount(()=>{mounted=false;token++;dispose()})
</script>
<style scoped>.comparison-trend{min-width:0;padding:18px;background:white;border:1px solid #dce5ed;border-radius:8px}h3{font-size:15px;margin:0}p,summary{font-size:12px;color:#6c8196;line-height:1.8}summary,button{cursor:pointer}.chart{height:330px;width:100%}.raw-table{overflow:auto;max-height:340px}table{min-width:660px;width:100%;border-collapse:collapse;font-size:11px}th,td{text-align:left;padding:9px;border-bottom:1px solid #e5ecf3}small{display:block;color:#7d8fa1;line-height:1.7}.state-row{display:flex;gap:10px;margin:12px 0;align-items:center}.state-strip{display:flex;flex:1;gap:2px;overflow:auto}.state-strip span{flex:1;min-width:16px;text-align:center;padding:7px 2px;font-size:10px}.met{background:#d5f0df;color:#216443}.unmet{background:#f6dada;color:#953737}.missing{background:#e5e9ee;color:#5f6e80}</style>
