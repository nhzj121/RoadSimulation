<template>
  <p class="domain-caption">图表与明细均来自当前所选完整轮次；构成占比及“其他”差值仅用于展示，不重算评价指标。</p>
  <div class="recorded-domain-layout">
    <nav class="recorded-domain-navigation" aria-label="记录指标分类">
      <button v-for="group in domains" :key="group.category" type="button"
        :class="{ active: group.category === activeGroup.category }" :aria-pressed="group.category === activeGroup.category"
        @click="selectedCategory = group.category">
        <span>{{ group.title }}</span><strong>{{ group.metrics.length }}<small> 项记录</small></strong>
      </button>
    </nav>
    <article :key="activeGroup.category" class="recorded-domain-content" :aria-label="`${activeGroup.title}记录指标`">
      <header class="recorded-domain-heading"><div><h3>{{ activeGroup.title }}</h3><p>{{ activeGroup.category }}</p></div><span>{{ activeGroup.metrics.filter(metric => metric.status === 'AVAILABLE').length }} / {{ activeGroup.metrics.length }} 项标记可用</span></header>
      <p v-if="!activeGroup.metrics.length" class="recorded-domain-notice" role="status">本轮未记录此分类的指标，不补零、不查询当前业务数据。</p>
      <template v-else>
        <p v-if="compatibilityMessage" class="recorded-domain-notice" role="status">{{ compatibilityMessage }}</p>
        <EvaluationDomainMetrics v-if="activeGroup.chartKey && !compatibilityMessage" :snapshot="snapshot" :group-key="activeGroup.chartKey">
          <template v-if="activeGroup.chartKey==='vehicle'" #vehicle-state><SandboxRecordedVehicleCharts :snapshot="snapshot" :record-context="recordContext" @record-error="emit('record-error',$event)" /></template>
        </EvaluationDomainMetrics>
        <div v-else class="recorded-value-grid">
          <article v-for="metric in activeGroup.metrics" :key="metric.metricId" class="recorded-value-card">
            <h4>{{ metric.displayName }}</h4><strong>{{ formatRecordedMetric(metric, unknownContract) }}</strong>
            <span class="recorded-metric-state" :class="`state-${metric.status}`">{{ metric.status }}</span><p v-if="metric.reason">{{ metric.reason }}</p>
          </article>
        </div>
        <details class="recorded-domain-details"><summary>完整指标明细（{{ activeGroup.metrics.length }} 项）</summary><slot name="details" :group="activeGroup" /></details>
      </template>
    </article>
  </div>
  <details v-if="supplementary.length" class="recorded-domain-details"><summary>其他原始记录（{{ supplementary.reduce((sum,group)=>sum+group.metrics.length,0) }} 项，含未单列指标）</summary><template v-for="group in supplementary" :key="group.category"><slot name="details" :group="group" /></template></details>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EvaluationSnapshot } from '../../types/evaluation'
import type { RecordedMetricGroup } from '../../sandbox/recordedMetricDomains'
import { recordedMetricDomains, recordedDomainCompatibility, supplementaryRecordedGroups } from '../../sandbox/recordedMetricDomains'
import { formatRecordedMetric } from '../../sandbox/results'
import EvaluationDomainMetrics from '../EvaluationDomainMetrics.vue'
import SandboxRecordedVehicleCharts from './SandboxRecordedVehicleCharts.vue'
import type { SandboxRecordedContext } from '../../types/sandbox-service-activity'

const props = defineProps<{ snapshot: EvaluationSnapshot; groups: RecordedMetricGroup[]; recordContext?: SandboxRecordedContext }>()
const emit = defineEmits<{(event:'record-error',error:unknown):void}>()
// Local reading state only. It survives loop changes but owns no run/request state.
const selectedCategory = ref('VEHICLE')
const domains = computed(() => recordedMetricDomains(props.groups))
const supplementary = computed(() => supplementaryRecordedGroups(props.groups))
const activeGroup = computed(() => domains.value.find(group => group.category === selectedCategory.value) || domains.value[0])
const unknownContract = computed(() => props.snapshot.contractVersion !== '1.9')
const compatibilityMessage = computed(() => recordedDomainCompatibility(props.snapshot, activeGroup.value.category))
</script>
<style scoped>
.domain-caption { color: #7a8797; font-size: 12px; margin: 8px 0 20px; line-height: 1.8; }
.recorded-domain-layout { display: grid; grid-template-columns: 150px minmax(0,1fr); gap: 22px; align-items: start; }
.recorded-domain-navigation { display: grid; gap: 8px; }
.recorded-domain-navigation button { appearance: none; width: 100%; text-align: left; border: 1px solid #e5eaf0; border-radius: 7px; padding: 12px 14px; background: #f8fafc; color: #61748a; font: inherit; cursor: pointer; overflow-wrap: anywhere; }
.recorded-domain-navigation button:hover { border-color: #b3d3f3; background: #f1f7fd; }
.recorded-domain-navigation button.active { border-color: #a6cbf1; background: #ecf5ff; color: #286aab; }
.recorded-domain-navigation button:focus-visible { outline: 2px solid #409eff; outline-offset: 2px; }
.recorded-domain-navigation button > span { display: block; font-size: 13px; font-weight: 600; }
.recorded-domain-navigation strong { display: block; margin-top: 5px; font-size: 18px; font-variant-numeric: tabular-nums; }
.recorded-domain-navigation small { font-size: 10px; font-weight: 400; color: #7d8ca0; }
.recorded-domain-content { min-width: 0; }
.recorded-domain-heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; border-bottom: 1px solid #edf0f4; padding-bottom: 12px; }
.recorded-domain-heading h3 { color: #2d4661; font-size: 16px; font-weight: 600; }
.recorded-domain-heading p, .recorded-domain-heading > span { color: #8290a1; font-size: 11px; }
.recorded-domain-notice { margin-top: 14px; padding: 12px 14px; border-radius: 6px; background: #fff9ec; color: #856529 !important; font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
.recorded-value-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; margin-top: 16px; }
.recorded-value-card { min-width: 0; padding: 16px; border: 1px solid #e5eaf0; border-radius: 8px; background: #fbfcfe; }
.recorded-value-card h4 { color: #64778b; font-size: 12px; }
.recorded-value-card > strong { display: block; font-size: 22px; color: #2d4661; margin-top: 10px; line-height: 1.5; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.recorded-value-card p { margin-top: 8px; font-size: 11px; color: #7a8797; line-height: 1.8; overflow-wrap: anywhere; }
.recorded-metric-state { display: inline-block; font-size: 10px; margin-top: 10px; padding: 2px 6px; border-radius: 4px; background: #eef1f5; color: #697888; overflow-wrap: anywhere; }
.state-AVAILABLE { background: #edf7f0; color: #35774c; }.state-INVALID { background: #fff0ef; color: #a44b41; }.state-NOT_AVAILABLE { background: #fff7e6; color: #91652b; }.state-NOT_APPLICABLE { background: #f3f0f7; color: #79618f; }
.recorded-domain-details { border-top: 1px solid #edf0f4; margin-top: 20px; padding-top: 16px; min-width: 0; }
.recorded-domain-details > summary { cursor: pointer; font-size: 13px; color: #52677e; line-height: 1.8; }
@media (max-width: 1100px) {
  .recorded-domain-layout { grid-template-columns: 1fr; gap: 16px; }
  .recorded-domain-navigation { grid-template-columns: repeat(3,minmax(0,1fr)); }
}
@media (max-width: 720px) {
  .recorded-domain-navigation { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .recorded-value-grid { grid-template-columns: 1fr; }
}
</style>
