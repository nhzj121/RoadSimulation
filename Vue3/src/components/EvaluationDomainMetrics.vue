<template>
  <section v-if="viewModel" class="domain-visualization" :aria-label="`${groupLabel}可视化`">
    <div class="domain-visualization__top">
      <!-- 只开放车辆首图替换位；其它摘要、比例、图表与明细继续使用原快照模型。 -->
      <slot v-if="groupKey === 'vehicle'" name="vehicle-state">
        <EvaluationDomainChart :model="viewModel.primary" />
      </slot>
      <EvaluationDomainChart v-else :model="viewModel.primary" />
      <div class="domain-visualization__summary">
        <div class="domain-visualization__statistics">
          <article v-for="metric in viewModel.statistics" :key="metric.id" class="domain-stat" :class="metricClass(metric)">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.text }}</strong>
            <small v-if="metric.status !== 'AVAILABLE' || metric.issue">{{ metric.issue || domainStatusText(metric.status) }}</small>
            <p v-if="metric.note">{{ metric.note }}</p>
            <p v-if="metric.reason">{{ metric.reason }}</p>
          </article>
        </div>
        <article class="domain-ratios">
          <h5>{{ groupKey === 'vehicle' ? '载重水平' : groupKey === 'cargo' ? '需求履约' : '完成与载重水平' }}</h5>
          <div v-for="metric in viewModel.ratios" :key="metric.id" class="domain-ratio" :class="metricClass(metric)">
            <div class="domain-ratio__heading"><span>{{ metric.label }}</span><strong>{{ metric.text }}</strong></div>
            <!-- 无效比例不钳制为正常值，保留原值并显示局部问题。 -->
            <progress v-if="metric.status === 'AVAILABLE' && !metric.issue && metric.value !== null" :value="metric.value" max="1" :aria-label="metric.label"></progress>
            <small v-else>{{ metric.issue || domainStatusText(metric.status) }}</small>
            <p v-if="metric.note">{{ metric.note }}</p>
            <p v-if="metric.reason">{{ metric.reason }}</p>
          </div>
        </article>
      </div>
    </div>
    <div class="domain-visualization__charts">
      <EvaluationDomainChart v-for="model in viewModel.charts" :key="model.id" :model="model" />
    </div>
    <div v-if="viewModel.sections.length" class="domain-visualization__sections">
      <article v-for="section in viewModel.sections" :key="section.title" class="domain-metric-section">
        <h5>{{ section.title }}</h5>
        <p>{{ section.description }}</p>
        <div class="domain-metric-section__values">
          <div v-for="metric in section.metrics" :key="metric.id" class="domain-stat domain-stat--compact" :class="metricClass(metric)">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.text }}</strong>
            <small v-if="metric.status !== 'AVAILABLE' || metric.issue">{{ metric.issue || domainStatusText(metric.status) }}</small>
            <p v-if="metric.reason">{{ metric.reason }}</p>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { EvaluationSnapshot } from '../types/evaluation';
import type { DomainMetricDisplay } from '../utils/evaluationDomainPresentation';
import { buildDomainViewModel, domainStatusText } from '../utils/evaluationDomainPresentation';
import EvaluationDomainChart from './EvaluationDomainChart.vue';

const props = defineProps<{ snapshot: EvaluationSnapshot; groupKey: string }>();
const viewModel = computed(() => buildDomainViewModel(props.snapshot, props.groupKey));
const groupLabel = computed(() => ({ vehicle: '车辆', cargo: '货物', task: '运输任务' } as Record<string, string>)[props.groupKey] || '评价');
function metricClass(metric: DomainMetricDisplay): string {
  return metric.issue || metric.status === 'INVALID' ? 'is-invalid' : metric.status === 'AVAILABLE' ? '' : 'is-unavailable';
}
</script>

<style scoped>
.domain-visualization { min-width: 0; margin-top: 16px; }
.domain-visualization__top { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); align-items: start; gap: 14px; }
.domain-visualization__summary { display: grid; min-width: 0; gap: 12px; }
.domain-visualization__statistics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.domain-stat { min-width: 0; padding: 13px; border: 1px solid #e3eaf2; border-radius: 9px; background: #fff; }
.domain-stat > span { display: block; color: #68788b; font-size: 11px; }
.domain-stat > strong { display: block; margin-top: 8px; color: #24394f; font-size: 21px; font-weight: 600; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.domain-stat > small { display: block; margin-top: 5px; font-size: 10px; color: #8290a1; }
.domain-stat p, .domain-ratio p { margin: 5px 0 0; color: #8290a1; font-size: 10px; line-height: 1.6; overflow-wrap: anywhere; }
.domain-ratios { min-width: 0; padding: 15px; border: 1px solid #e3eaf2; border-radius: 9px; background: #fff; }
.domain-ratios h5, .domain-metric-section h5 { margin: 0; color: #2d4661; font-size: 13px; }
.domain-ratio { margin-top: 13px; }
.domain-ratio__heading { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; color: #68788b; font-size: 11px; }
.domain-ratio__heading strong { color: #2d4661; font-size: 15px; font-variant-numeric: tabular-nums; }
.domain-ratio > small { display: block; margin-top: 5px; font-size: 10px; color: #8290a1; }
.domain-ratio progress { display: block; appearance: none; width: 100%; height: 7px; border: 0; border-radius: 5px; overflow: hidden; margin-top: 7px; background: #edf1f6; accent-color: #337ecc; }
.domain-ratio progress::-webkit-progress-bar { background: #edf1f6; border-radius: 5px; }
.domain-ratio progress::-webkit-progress-value { background: #337ecc; border-radius: 5px; }
.domain-ratio progress::-moz-progress-bar { background: #337ecc; border-radius: 5px; }
.domain-visualization__charts, .domain-visualization__sections { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 14px; }
.domain-visualization__charts > :only-child { grid-column: 1 / -1; }
.domain-metric-section { min-width: 0; padding: 15px; border: 1px solid #e3eaf2; border-radius: 9px; background: #fff; }
.domain-metric-section > p { margin: 5px 0 0; color: #8290a1; font-size: 10px; line-height: 1.6; }
.domain-metric-section__values { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 10px; margin-top: 10px; }
.domain-stat--compact { padding: 8px 0; border: 0; border-radius: 0; }
.domain-stat--compact > strong { font-size: 17px; }
.is-invalid > small, .is-invalid.domain-ratio > small { color: #aa4545; }
.is-invalid.domain-stat { border-color: #efc5c5; }
.is-unavailable > strong { color: #8290a1; }
@media (max-width: 1100px) {
  .domain-visualization__top { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .domain-visualization__charts, .domain-visualization__sections { grid-template-columns: 1fr; }
  .domain-visualization__statistics { grid-template-columns: 1fr; }
}
</style>
