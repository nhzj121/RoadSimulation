<template>
  <main class="sandbox-demos">
    <header><div><h1>数据沙箱 · 预置场景演示</h1><p>六组已完成的真实历史运行，统一48轮、每轮30分钟、根种子20260927。</p></div><router-link to="/sandbox/tasks">返回任务列表</router-link></header>
    <button :disabled="loading" @click="load">{{ loading?'读取中…':'刷新演示目录' }}</button>
    <p v-if="error" role="alert">{{ error }}</p>
    <template v-if="data">
      <p>基线：{{ data.baselineId }} · 目录生成时间：{{ data.generatedAt }}</p>
      <section class="pairs"><article v-for="pair in data.comparisons" :key="pair.title"><h2>{{ pair.title }}</h2><p>{{ title(pair.left) }} ↔ {{ title(pair.right) }}</p><router-link :to="{path:'/sandbox/comparison',query:{left:pair.leftExecutionId,right:pair.rightExecutionId}}">查看完整趋势与最终结果</router-link></article></section>
      <h2>运行记录</h2><p>四目标为最终轮记录值；缺失状态保留。单个种子的结果用于展示，不代表算法统计优劣。</p>
      <section class="runs"><article v-for="entry in data.entries" :key="entry.id"><h3>{{ entry.title }}</h3>
        <p>{{ data.fleets[entry.scenarioKey].vehicleCount }}辆 · 总额定载重{{ data.fleets[entry.scenarioKey].ratedPayloadTonnes }}吨 · 容积{{ data.fleets[entry.scenarioKey].cargoVolumeCubicMeters }}m³</p>
        <dl><template v-for="id in objectiveIds" :key="id"><dt>{{ entry.finalMetrics[id]?.displayName || id }}</dt><dd>{{ entry.finalMetrics[id] ? formatRecordedMetric(entry.finalMetrics[id]) : '未记录' }}</dd></template></dl>
        <p>{{ entry.runSpecKey }} / Revision {{ entry.revision }}</p><router-link :to="`/sandbox/executions/${entry.executionId}/results`">查看该次结果</router-link>
      </article></section>
      <p class="note">{{ data.interpretation }} 相同随机种子不保证策略变化后的实际事件轨迹完全相同。</p>
    </template>
  </main>
</template>
<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount} from 'vue'
import {parseDemoResults} from '../sandbox/demo'
import type {DemoResults} from '../sandbox/demo'
import {objectiveIds,formatRecordedMetric} from '../sandbox/results'
const data=ref<DemoResults|null>(null),error=ref(''),loading=ref(false)
let controller:AbortController|undefined,generation=0
const title=(id:string)=>data.value?.entries.find(e=>e.id===id)?.title||id
async function load(){
  const current=++generation;controller?.abort();controller=new AbortController();loading.value=true;error.value='';data.value=null
  try {
    const response=await fetch('/sandbox-demo-v1-results.json',{signal:controller.signal,cache:'no-store'})
    if(!response.ok)throw Error(response.status===404?'暂无已完成的场景演示':'演示目录暂时无法读取，请重试')
    const value=parseDemoResults(await response.json());if(current===generation)data.value=value
  }catch(e){if(current===generation)error.value=(e as Error).message}
  finally{if(current===generation)loading.value=false}
}
onMounted(load);onBeforeUnmount(()=>{generation++;controller?.abort()})
</script>
<style scoped>
.sandbox-demos{max-width:1400px;margin:auto;padding:28px;color:#29405a;background:#f6f8fb;min-height:100vh}header{display:flex;justify-content:space-between;align-items:center;gap:20px}h1{font-size:25px}h2{font-size:18px}h3{font-size:16px}p{font-size:13px;line-height:1.8;overflow-wrap:anywhere;color:#60758c}.pairs,.runs{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:24px 0}article{padding:20px;background:white;border:1px solid #dce5ed;border-radius:8px;min-width:0}a{color:#276da9}button{padding:10px 16px;border:1px solid #c4d5e5;color:#276da9;background:white;border-radius:5px;cursor:pointer}button:disabled{opacity:.5}dl{font-size:12px;line-height:1.8}dt{color:#60758c}dd{margin-left:0;font-weight:600;margin-bottom:8px}.note{padding:18px;background:#edf3f9}@media(max-width:1000px){.pairs,.runs{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:650px){.sandbox-demos{padding:18px 12px}header{align-items:flex-start;flex-direction:column}.pairs,.runs{grid-template-columns:1fr}}
</style>
