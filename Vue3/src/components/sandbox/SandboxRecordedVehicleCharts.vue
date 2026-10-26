<template>
  <section class="recorded-vehicle-charts" aria-label="记录车辆状态与20轮去重车辆数">
    <p>所选完整轮次的冻结记录 · 不是普通地图的实时观察</p>
    <nav aria-label="车辆图表模式"><button type="button" :aria-pressed="mode==='current'" @click="mode='current'">所选轮次状态</button><button type="button" :aria-pressed="mode==='window'" @click="mode='window'">最近20轮各状态车辆数</button></nav>
    <p v-if="state.loading.value" role="status">正在读取冻结车辆记录，不推进仿真。</p>
    <p v-else-if="state.error.value" class="activity-notice" role="status">{{ state.error.value }}；不以旧值或0代替。<button type="button" :disabled="!recordContext || recordContext.stale" @click="state.refresh">重新读取</button></p>
    <p v-if="mode==='window' && state.result.value">第{{ state.result.value.fromLoopIndex+1 }}～{{ state.result.value.toLoopIndex+1 }}轮 · 固定最近20轮，不足20轮使用实际已记录轮次。</p>
    <EvaluationDomainChart :model="chartModel" :show-zero-legend="mode==='current'" :center-label="mode==='current'?'本轮非空闲车辆':''" />
    <p v-if="mode==='window'">同一状态内按车辆ID去重；跨轮持续或反复进入只计1辆。窗口开始前进入、窗口内仍持续的装卸也计入。同车可计入多个状态，各项不可相加当作车队总数。</p>
    <p v-else>空闲分类不展示；占比仅针对本轮非空闲车辆，不是全部车辆的占比。</p>
    <p v-if="state.result.value">核对范围：{{ mode==='window'?'冻结清单、窗口冻结载荷及本地账本链接':'冻结清单、所选轮载荷及本地账本链接' }}；不代表此次已完成全账本核验。</p>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EvaluationSnapshot } from '../../types/evaluation'
import type { SandboxRecordedContext } from '../../types/sandbox-service-activity'
import { useRecordedVehicleStates, recordedVehicleStateChart } from '../../sandbox/recordedVehicleStates'
import EvaluationDomainChart from '../EvaluationDomainChart.vue'
const props=defineProps<{snapshot:EvaluationSnapshot;recordContext?:SandboxRecordedContext}>()
const emit=defineEmits<{(event:'record-error',error:unknown):void}>()
const mode=ref<'current'|'window'>('current')
const state=useRecordedVehicleStates({context:()=>props.recordContext,snapshot:()=>props.snapshot,includeWindow:()=>mode.value==='window',onRecordError:e=>emit('record-error',e)})
const chartModel=computed(()=>recordedVehicleStateChart(state.result.value,mode.value==='window',state.loading.value?'正在读取冻结车辆状态':state.error.value || undefined))
</script>
<style scoped>
.recorded-vehicle-charts { border:1px solid #e3eaf2;border-radius:10px;background:#fff;overflow:hidden;min-width:0; }
.recorded-vehicle-charts > p {font-size:11px;line-height:1.8;padding:12px 14px 0;color:#718096;overflow-wrap:anywhere;}
nav{display:flex;flex-wrap:wrap;gap:6px;padding:10px 14px 0;}
button{font:inherit;font-size:12px;padding:5px 9px;border:1px solid #dfe7f1;border-radius:5px;background:#fff;color:#657b98;cursor:pointer;}
button[aria-pressed="true"]{background:#ecf5ff;color:#337ecc;border-color:#a6cef4;}
button:focus-visible,select:focus-visible{outline:2px solid #409eff;outline-offset:2px;}
.activity-notice{padding-bottom:14px!important;color:#946825!important;}
.recorded-vehicle-charts :deep(.domain-chart){border:0;padding:14px;}
.recorded-vehicle-charts :deep(.domain-chart__canvas--bars){height:230px;}
</style>
