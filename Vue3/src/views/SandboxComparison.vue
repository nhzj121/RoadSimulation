<template>
  <main class="sandbox-comparison">
    <header><div><span>HISTORICAL EXECUTION COMPARISON</span><h1>数据沙箱 · 历史结果对比</h1><p>对比两次已完整完成的历史运行，查看四目标和全部评价项。</p></div><router-link to="/sandbox/tasks">返回任务列表</router-link></header>
    <section class="selection-card"><h2>选择两次执行</h2><p>双方开始时间、每轮时长和总轮数需一致；允许策略、基础数据、天气和事件设置不同。</p>
      <div class="selectors"><label>A · 参考方案<select v-model="leftId"><option value="">请选择</option><option v-for="item in options" :key="item.execution_id" :value="item.execution_id" :disabled="item.execution_status!=='COMPLETED'">{{ item.run_spec_key }} / r{{ item.run_spec_revision }} · {{ item.completed_loops }}轮 · {{ item.execution_status }} · {{ item.execution_id }}</option><option v-if="leftId && !options.some(v=>v.execution_id===leftId)" :value="leftId">{{ leftId }}</option></select></label>
      <label>B · 对照方案<select v-model="rightId"><option value="">请选择</option><option v-for="item in options" :key="item.execution_id" :value="item.execution_id" :disabled="item.execution_status!=='COMPLETED'">{{ item.run_spec_key }} / r{{ item.run_spec_revision }} · {{ item.completed_loops }}轮 · {{ item.execution_status }} · {{ item.execution_id }}</option><option v-if="rightId && !options.some(v=>v.execution_id===rightId)" :value="rightId">{{ rightId }}</option></select></label></div>
      <div class="buttons"><button :disabled="!leftId || !rightId || leftId===rightId || loading" @click="load">{{ loading?'正在读取…':'加载对比' }}</button><button :disabled="!leftId || !rightId" @click="swap">交换A/B</button><button :disabled="listLoading || !hasMore" @click="loadOptions">{{ listLoading?'读取列表…':'加载更多历史执行' }}</button></div>
      <p v-if="listError" role="alert">历史列表读取失败：{{ comparisonReason(listError) }} <button @click="loadOptions">重试</button></p>
      <p v-else-if="!listLoading && !options.length">暂无历史执行。</p>
    </section>
    <p v-if="error" class="error" role="alert">{{ comparisonReason(error) }}；当前没有已确认的对比结果，请重新选择或重试。</p>
    <SandboxComparisonSummary v-if="comparison" :comparison="comparison" />
    <section v-if="histories" class="objective-trends"><h2>四目标 · 完整趋势对比</h2><p>各点采用该轮结束时的运行累计评价，最终点与上方最终结果一致。悬停联动查看同一轮，缩放可查看局部。</p><div class="trend-grid"><SandboxComparisonTrend v-for="id in objectiveIds" :key="id" :left="histories.left" :right="histories.right" :metric-id="id" /></div></section>
    <section v-if="comparison" class="selection-card"><h2>其它评价项与单指标分析</h2><label>评价项 <select v-model="metricId"><optgroup v-for="group in metricGroups" :key="group.id" :label="group.name"><option v-for="m in group.metrics" :key="m.metricId" :value="m.metricId">{{ m.displayName }}（{{ m.unit }}）</option></optgroup></select></label><SandboxComparisonBars v-if="selectedMetric" :metric="selectedMetric" /><SandboxComparisonTrend v-if="histories && metricId" :left="histories.left" :right="histories.right" :metric-id="metricId" /></section>
  </main>
</template>
<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { sandboxApi } from '../api/sandbox'
import type { SandboxExecutionList } from '../types/sandbox-comparison'
import { createComparisonState, comparisonReason, comparisonCategories } from '../sandbox/comparison'
import SandboxComparisonSummary from '../components/sandbox/SandboxComparisonSummary.vue'
import SandboxComparisonBars from '../components/sandbox/SandboxComparisonBars.vue'
import SandboxComparisonTrend from '../components/sandbox/SandboxComparisonTrend.vue'
import {objectiveIds} from '../sandbox/results'
const route=useRoute(),leftId=ref(String(route.query.left||'')),rightId=ref(String(route.query.right||''))
const session=createComparisonState(),{comparison,histories,loading,error}=session
const metricId=ref('emptyMileageRatio'),selectedMetric=computed(()=>comparison.value?.metrics.find(m=>m.metricId===metricId.value))
const metricGroups=computed(()=>Array.from(new Set(comparison.value?.metrics.map(m=>m.category)||[])).map(id=>({id,name:comparisonCategories[id]||id,metrics:comparison.value!.metrics.filter(m=>m.category===id)})))
watch(comparison,value=>{if(value && !value.metrics.some(m=>m.metricId===metricId.value))metricId.value=value.metrics[0]?.metricId||''})
const options=ref<SandboxExecutionList['items']>([]),hasMore=ref(true),listLoading=ref(false),listError=ref('')
let offset=0,disposed=false,listController:AbortController|undefined
async function loadOptions(){
  if(listLoading.value || !hasMore.value)return
  listLoading.value=true;listError.value='';listController=new AbortController()
  try {
    const page=await sandboxApi.executions(offset,listController.signal);if(disposed)return
    options.value.push(...page.items);offset+=page.items.length;hasMore.value=page.hasMore
    const complete=options.value.filter(v=>v.execution_status==='COMPLETED')
    if(!leftId.value)leftId.value=complete[0]?.execution_id||''
    if(!rightId.value)rightId.value=complete.find(v=>v.execution_id!==leftId.value)?.execution_id||''
  }catch(e){if(!disposed)listError.value=(e as Error).message}finally{if(!disposed)listLoading.value=false}
}
async function load(){await session.load(leftId.value,rightId.value)}
function swap(){const id=leftId.value;leftId.value=rightId.value;rightId.value=id;session.invalidate()}
watch([leftId,rightId],session.invalidate,{flush:'sync'})
watch(()=>[route.query.left,route.query.right],()=>{leftId.value=String(route.query.left||'');rightId.value=String(route.query.right||'')})
onMounted(loadOptions)
onBeforeUnmount(()=>{disposed=true;listController?.abort();session.invalidate()})
</script>
<style scoped>
.sandbox-comparison{max-width:1664px;margin:auto;padding:28px;background:#f6f8fb;color:#29405a;min-height:100vh}header{display:flex;justify-content:space-between;gap:20px;align-items:center;margin-bottom:22px}header span{font-size:11px;letter-spacing:1px;color:#7990a9}h1{font-size:25px}h2{font-size:18px}p{font-size:13px;line-height:1.8;color:#64788f}.selection-card{padding:22px;background:white;border:1px solid #e1e8ef;border-radius:8px;margin-bottom:24px}.selectors{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.selectors label{display:flex;flex-direction:column;gap:9px;font-size:13px}select{width:100%;padding:10px;border:1px solid #c9d7e4;border-radius:5px;background:white}.buttons{display:flex;flex-wrap:wrap;gap:12px;margin-top:18px}button{padding:9px 14px;background:white;color:#276da9;border:1px solid #c4d5e5;border-radius:5px;cursor:pointer}button:disabled{opacity:.5;cursor:default}button:focus-visible,select:focus-visible{outline:2px solid #2878ba;outline-offset:3px}.error{padding:18px;border:1px solid #e7a6a6;background:#fff2f2;color:#a12f2f}@media(max-width:760px){.sandbox-comparison{padding:18px 12px}header{align-items:flex-start;flex-direction:column}.selectors{grid-template-columns:1fr}}
</style>
<style scoped>.objective-trends{margin:24px 0}.trend-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}@media(max-width:900px){.trend-grid{grid-template-columns:1fr}}</style>
