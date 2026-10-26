<template>
  <section class="comparison-summary">
    <div class="side-grid"><article v-for="(side,index) in [comparison.left,comparison.right]" :key="side.executionId" :class="`side-${index}`">
      <h2>{{ index===0?'A · 参考方案':'B · 对照方案' }}：{{ side.displayName }}</h2>
      <p>{{ side.strategy }} · {{ side.algorithmProfileId }}</p><p>场景：{{ side.scenarioName }} · 基线：{{ side.baselineId }}</p>
      <p>完整完成 {{ side.clock.totalLoops }}轮 · 每轮{{ side.clock.tickDurationSeconds }}秒 · 开始{{ side.clock.startLocalDateTime }}</p>
      <p>{{ side.runSpecKey }} / Revision {{ side.revision }}</p><router-link :to="`/sandbox/executions/${side.executionId}/results`">{{ side.executionId }}</router-link>
      <p>最终快照：{{ side.finalSnapshot.snapshotStatus }}<span v-if="side.finalSnapshot.errorCodes?.length"> · {{ side.finalSnapshot.errorCodes.join('、') }}</span></p>
    </article></div>
    <h2>最终结果 · 第{{ comparison.left.clock.totalLoops }}轮结束</h2><p>差值为B−A；比例差使用百分点，相对变化使用百分比。累计目标直接采用终点记录，不求逐轮平均。不生成综合评分。</p>
    <details v-for="group in groups" :key="group.id" class="metric-group" :open="group.id==='GLOBAL_OBJECTIVE' || group.id==='SERVICE_CONSTRAINT'"><summary>{{ group.name }}（{{ group.metrics.length }}项）</summary>
      <div class="table-wrap"><table><thead><tr><th>评价项</th><th>A · 最终值</th><th>B · 最终值</th><th>差值 B−A</th><th>相对变化</th><th>状态说明</th></tr></thead><tbody>
        <tr v-for="m in group.metrics" :key="m.metricId"><th>{{ m.displayName }}<small>{{ m.metricId }}</small></th><td>{{ comparisonValue(m.left,comparison.left.finalSnapshot.contractVersion!=='1.9') }}<small>{{ m.left?.reason }}</small></td><td>{{ comparisonValue(m.right,comparison.right.finalSnapshot.contractVersion!=='1.9') }}<small>{{ m.right?.reason }}</small></td><td>{{ comparisonDelta(m) }}</td><td>{{ comparisonRelative(m) }}</td><td>{{ comparisonReason(m.reason) || (m.unit==='boolean'?'状态对照，不计算差值':'同口径数值对比') }}</td></tr>
      </tbody></table></div>
    </details>
    <details><summary>实验条件差异（{{ comparison.differences.length }}项）与记录追溯</summary><p>不同场景和资源允许比较；多个条件同时变化时，结果不能单独归因于调度策略。相同种子不保证业务状态不同后实际发生的事件完全相同。</p>
      <div class="table-wrap"><table><thead><tr><th>条件</th><th>A</th><th>B</th></tr></thead><tbody><tr v-for="d in comparison.differences" :key="d.path"><th>{{ d.path }}</th><td><pre>{{ JSON.stringify(d.left,null,2) }}</pre></td><td><pre>{{ JSON.stringify(d.right,null,2) }}</pre></td></tr></tbody></table></div>
      <p v-for="side in [comparison.left,comparison.right]" :key="side.executionId">{{ side.executionId }} · manifest {{ side.manifestSha256 }} · 最终评价 {{ side.finalEvaluationSha256 }}</p>
    </details>
    <p class="scope">读取核验范围：{{ comparison.integrityScope }}；完整账本：{{ comparison.ledgerIntegrity }}。本页不重算指标、不推进仿真。</p>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { SandboxComparison, SandboxMetricComparison } from '../../types/sandbox-comparison'
import {comparisonCategories,comparisonValue,comparisonDelta,comparisonRelative,comparisonReason} from '../../sandbox/comparison'
const props=defineProps<{comparison:SandboxComparison}>()
const groups=computed(()=>{
  const buckets=new Map<string,SandboxMetricComparison[]>()
  for(const m of props.comparison.metrics){const list=buckets.get(m.category)||[];list.push(m);buckets.set(m.category,list)}
  const ids=[...Object.keys(comparisonCategories),...Array.from(buckets.keys()).filter(id=>!comparisonCategories[id])]
  return ids.filter(id=>buckets.has(id)).map(id=>({id,name:comparisonCategories[id]||id,metrics:buckets.get(id)!}))
})
</script>
<style scoped>
.side-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.side-grid article{padding:16px;border:1px solid #dde5ed;border-top:4px solid #2878ba;border-radius:8px}.side-grid .side-1{border-top-color:#d67428}p{line-height:1.8;overflow-wrap:anywhere;color:#60758c;font-size:13px}h2{font-size:18px}h3{font-size:15px}.metric-group{margin:24px 0}.table-wrap{overflow:auto;border:1px solid #e1e8ef;border-radius:6px;max-height:560px}table{border-collapse:collapse;width:100%;min-width:780px;font-size:12px}td,th{padding:12px;text-align:left;border-bottom:1px solid #e8edf3;vertical-align:top}thead{background:#f2f6fa}small{display:block;color:#7d8da0;font-weight:normal;margin-top:5px}pre{white-space:pre-wrap;max-width:420px;max-height:240px;overflow:auto}.scope{margin-top:22px}summary{cursor:pointer;color:#346e9d}@media(max-width:760px){.side-grid{grid-template-columns:1fr}}
</style>
