<template>
  <section class="final-bars" aria-label="单指标最终结果对比"><h3>{{ metric.displayName }} · 最终结果</h3>
    <p v-if="!metric.comparable">{{ comparisonReason(metric.reason) }}；请查看双方原值与状态。</p>
    <div v-else-if="metric.unit==='boolean'" class="states"><p>A：{{ comparisonValue(metric.left) }}</p><p>B：{{ comparisonValue(metric.right) }}</p></div>
    <svg v-else viewBox="0 0 720 170" role="img" :aria-label="`${metric.displayName}，A ${comparisonValue(metric.left)}，B ${comparisonValue(metric.right)}`">
      <line :x1="position(0)" :x2="position(0)" y1="15" y2="145" stroke="#a8bacd" />
      <g v-for="(m,i) in [metric.left,metric.right]" :key="i"><text x="12" :y="48+i*70">{{ i===0?'A':'B' }}</text><rect :x="Math.min(position(0),position(m?.value || 0))" :y="25+i*70" :width="Math.abs(position(m?.value || 0)-position(0))" height="34" :fill="comparisonColors[i]" /><text x="510" :y="48+i*70">{{ comparisonValue(m) }}</text></g>
    </svg>
    <p>差值：{{ comparisonDelta(metric) }} · 相对变化：{{ comparisonRelative(metric) }}</p>
  </section>
</template>
<script setup lang="ts">
import type { SandboxMetricComparison } from '../../types/sandbox-comparison'
import {comparisonColors,comparisonReason,comparisonValue,comparisonDelta,comparisonRelative} from '../../sandbox/comparison'
const props=defineProps<{metric:SandboxMetricComparison}>()
function position(value:number){const a=props.metric.left?.value||0,b=props.metric.right?.value||0,min=Math.min(0,a,b),max=Math.max(0,a,b);return 60+(value-min)/(max-min||1)*420}
</script>
<style scoped>.final-bars{padding:20px;border:1px solid #dce5ed;border-radius:8px;background:white;margin:20px 0}h3{font-size:16px}svg{display:block;width:100%;max-width:850px}text{font-size:13px;fill:#36526b}p{font-size:13px;color:#687e93;line-height:1.8}.states{display:flex;gap:30px}</style>
