<template>
  <section v-if="snapshot">
    <el-alert v-if="unknownContract" :title="`历史评价契约 ${snapshot.contractVersion}：只显示记录原值、单位与状态，不套用当前公式或补项。`" type="warning" :closable="false" />
    <p>第{{ snapshot.loopIndex + 1 }}轮（loopIndex={{ snapshot.loopIndex }}） · {{ snapshot.simTime }} · 快照 {{ snapshot.snapshotStatus }}</p>
    <h2>四目标与服务约束</h2>
    <div class="objectives"><article v-for="id in displayedObjectives" :key="id"><h3>{{ snapshot.metrics[id].displayName }}</h3><strong>{{ formatRecordedMetric(snapshot.metrics[id], unknownContract) }}</strong><p>{{ snapshot.metrics[id].status }} · {{ snapshot.metrics[id].reason || '无额外原因' }}</p></article></div>
    <p>正常完成不表示服务约束满足；累计值与比例直接采用所选轮次记录。</p>
    <h2>全部记录指标</h2>
    <section v-for="group in groups" :key="group.category"><h3>{{ group.category }}</h3>
      <div class="metric-table"><table><thead><tr><th>指标 / ID</th><th>值</th><th>单位</th><th>状态</th><th>原因</th></tr></thead><tbody><tr v-for="m in group.metrics" :key="m.metricId"><td>{{ m.displayName }}<small>{{ m.metricId }}</small></td><td>{{ formatRecordedMetric(m, unknownContract) }}</td><td>{{ m.unit }}</td><td>{{ m.status }}</td><td>{{ m.reason || '—' }}</td></tr></tbody></table></div>
    </section>
    <details><summary>本轮冻结的阈值与排放模型</summary><pre>{{ JSON.stringify({thresholds:snapshot.thresholds, energyEmissionModel:snapshot.energyEmissionModel}, null, 2) }}</pre></details>
  </section><p v-else>暂无评价</p>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { EvaluationSnapshot, EvaluationMetricValue } from '../../types/evaluation'
import { objectiveIds, formatRecordedMetric } from '../../sandbox/results'
const props = defineProps<{snapshot: EvaluationSnapshot | null}>()
const unknownContract = computed(() => props.snapshot?.contractVersion !== '1.9')
const displayedObjectives = computed(() => [...objectiveIds, 'waitingServiceCompliant'].filter(id => props.snapshot?.metrics[id]))
const groups = computed(() => {
  const grouped = new Map<string, EvaluationMetricValue[]>()
  Object.values(props.snapshot?.metrics || {}).forEach(m => { const list = grouped.get(m.category) || []; list.push(m); grouped.set(m.category, list) })
  return [...grouped].map(([category, metrics]) => ({category, metrics}))
})
</script>
<style scoped>.objectives{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px}.objectives article{padding:12px;background:#f2f6fc;border-radius:8px}.objectives strong{font-size:22px}.metric-table{overflow-x:auto}table{width:100%;min-width:680px;border-collapse:collapse}td,th{padding:8px;border-bottom:1px solid #ddd;text-align:left;overflow-wrap:anywhere}small{display:block;color:#666}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style>
