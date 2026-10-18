<template>
  <section v-if="snapshot" class="snapshot-display">
    <el-alert v-if="unknownContract" :title="`历史评价契约 ${snapshot.contractVersion}：只显示记录原值、单位与状态，不套用当前公式或补项。`" type="warning" :closable="false" />
    <section class="evaluation-card core-evaluation" aria-label="本轮核心评价">
      <div class="metric-section-heading"><span>RECORDED EVALUATION</span><h2>四个全局目标</h2><p class="snapshot-meta">第{{ snapshot.loopIndex + 1 }}轮（loopIndex={{ snapshot.loopIndex }}） · {{ snapshot.simTime }} · 快照 {{ snapshot.snapshotStatus }}</p></div>
      <div class="objectives"><article v-for="id in displayedObjectives" :key="id" :class="{ 'is-service': id === 'waitingServiceCompliant' }"><h3>{{ snapshot.metrics[id].displayName }}</h3><strong>{{ formatRecordedMetric(snapshot.metrics[id], unknownContract) }}</strong><p>{{ snapshot.metrics[id].status }} · {{ snapshot.metrics[id].reason || '无额外原因' }}</p></article></div>
      <p class="metric-caption">累计值与比例直接采用所选轮次记录，不重新计算或补值。</p>
    </section>
    <!-- Presentation slot only: the parent retains every trend request and control. -->
    <slot name="trends" />
    <section class="evaluation-card all-metrics" aria-label="全部记录指标">
      <div class="metric-section-heading"><span>METRIC DETAILS</span><h2>全部记录指标</h2></div>
      <SandboxRecordedMetricDomains :snapshot="snapshot" :groups="groups" :record-context="recordContext" @record-error="emit('record-error',$event)"><template #details="{ group }">
      <section class="metric-group"><h3>{{ group.category }}</h3>
        <div class="metric-table"><table><thead><tr><th>指标 / ID</th><th>值</th><th>单位</th><th>状态</th><th>原因</th></tr></thead><tbody><tr v-for="m in group.metrics" :key="m.metricId"><td>{{ m.displayName }}<small>{{ m.metricId }}</small></td><td>{{ formatRecordedMetric(m, unknownContract) }}</td><td>{{ m.unit }}</td><td><span class="metric-status">{{ m.status }}</span></td><td>{{ m.reason || '—' }}</td></tr></tbody></table></div>
      </section>
      </template></SandboxRecordedMetricDomains>
      <details><summary>本轮冻结的阈值与排放模型</summary><pre>{{ JSON.stringify({thresholds:snapshot.thresholds, energyEmissionModel:snapshot.energyEmissionModel}, null, 2) }}</pre></details>
    </section>
  </section><section v-else class="snapshot-empty"><p>暂无评价</p><slot name="trends" /></section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { EvaluationSnapshot, EvaluationMetricValue } from '../../types/evaluation'
import { objectiveIds, formatRecordedMetric } from '../../sandbox/results'
import SandboxRecordedMetricDomains from './SandboxRecordedMetricDomains.vue'
import type { SandboxRecordedContext } from '../../types/sandbox-service-activity'
const props = defineProps<{snapshot: EvaluationSnapshot | null;recordContext?:SandboxRecordedContext}>()
const emit = defineEmits<{(event:'record-error',error:unknown):void}>()
const unknownContract = computed(() => props.snapshot?.contractVersion !== '1.9')
const displayedObjectives = computed(() => objectiveIds.filter(id => props.snapshot?.metrics[id]))
const groups = computed(() => {
  const grouped = new Map<string, EvaluationMetricValue[]>()
  Object.values(props.snapshot?.metrics || {}).forEach(m => { const list = grouped.get(m.category) || []; list.push(m); grouped.set(m.category, list) })
  return [...grouped].map(([category, metrics]) => ({category, metrics}))
})
</script>
<style scoped>
.snapshot-display, .snapshot-empty { min-width: 0; }
.snapshot-display > .el-alert { margin-bottom: 16px; }
.evaluation-card { padding: 22px 24px; background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; margin-bottom: 20px; box-shadow: 0 2px 6px rgba(31,45,61,.025); min-width: 0; }
.metric-section-heading > span { display: block; color: #77889a; font-size: 10px; font-weight: 600; letter-spacing: 1.4px; margin-bottom: 5px; }
.metric-section-heading h2 { color: #303133; font-size: 18px; font-weight: 600; line-height: 1.5; }
.snapshot-meta { font-size: 12px; margin-top: 8px; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
.objectives { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 14px; margin-top: 18px; }
.objectives article { min-width: 0; padding: 18px 20px 16px; border: 1px solid #e1e9f2; border-top: 3px solid #409eff; border-radius: 8px; background: #fbfcfe; }
.objectives h3 { font-size: 13px; font-weight: 500; color: #52677e; line-height: 1.6; }
.objectives strong { display: block; margin-top: 14px; color: #24394f; font-size: 29px; line-height: 1.4; font-weight: 600; letter-spacing: -.4px; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.objectives article p { margin-top: 12px; font-size: 11px; line-height: 1.7; overflow-wrap: anywhere; }
.objectives .is-service { grid-column: 1 / -1; display: grid; grid-template-columns: minmax(170px,1fr) minmax(100px,.6fr) minmax(0,2fr); align-items: center; gap: 18px; border-top: 1px solid #e1e9f2; border-left: 3px solid #8b9fb5; padding: 15px 20px; background: #f7f9fc; }
.objectives .is-service strong { margin-top: 0; font-size: 23px; }
.objectives .is-service p { margin-top: 0; }
.metric-caption { font-size: 12px; line-height: 1.8; margin-top: 14px; }
.metric-group { margin-top: 22px; min-width: 0; }
.metric-group > h3 { padding-left: 10px; border-left: 3px solid #a6bdd6; margin-bottom: 10px; color: #52677e; font-size: 12px; font-weight: 600; letter-spacing: .6px; overflow-wrap: anywhere; }
.metric-table { overflow-x: auto; border: 1px solid #e5eaf0; border-radius: 7px; }
table { width: 100%; min-width: 800px; border-collapse: collapse; table-layout: fixed; font-size: 12px; }
td, th { padding: 13px 16px; border-bottom: 1px solid #edf0f4; text-align: left; vertical-align: top; overflow-wrap: anywhere; line-height: 1.7; }
th { font-size: 11px; font-weight: 500; color: #738194; }
th:nth-child(1) { width: 28%; } th:nth-child(2) { width: 19%; } th:nth-child(3) { width: 14%; } th:nth-child(4) { width: 16%; } th:nth-child(5) { width: 23%; }
td:nth-child(2) { color: #2d4661; font-weight: 500; font-variant-numeric: tabular-nums; }
tbody tr:nth-child(even) { background: #fbfcfe; } tbody tr:last-child td { border-bottom: 0; }
small { display: block; font-size: 10px; color: #8a96a4; margin-top: 3px; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
.metric-status { display: inline-block; background: #f0f4f8; color: #566d85; border-radius: 4px; padding: 2px 6px; font-size: 10px; }
details { border-top: 1px solid #edf0f4; padding-top: 16px; margin-top: 22px; }
summary { cursor: pointer; color: #52677e; font-size: 13px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; padding: 16px; margin-top: 12px; border-radius: 6px; background: #f7f8fa; color: #606266; font-size: 12px; line-height: 1.8; }
.snapshot-empty > p { padding: 20px; margin-bottom: 20px; border: 1px solid #e4e7ed; border-radius: 8px; background: #fff; }
@media (max-width: 1100px) {
  .objectives { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .objectives .is-service { grid-template-columns: minmax(0,1fr) minmax(0,1fr); }
  .objectives .is-service p { grid-column: 1 / -1; }
}
@media (max-width: 720px) {
  .evaluation-card { padding: 18px 16px; }
  .objectives { grid-template-columns: 1fr; }
  .objectives .is-service { grid-template-columns: 1fr; gap: 8px; }
  .objectives strong { font-size: 26px; }
}
</style>
