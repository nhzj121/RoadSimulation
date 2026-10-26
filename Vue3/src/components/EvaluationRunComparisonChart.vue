<template>
  <section class="comparison-chart" aria-label="运行指标对比">
    <div v-if="!viewModel" class="comparison-chart__state">请选择可比较的评价指标。</div>
    <div v-else-if="!viewModel.comparable" class="comparison-chart__state">
      <strong>{{ viewModel.displayName }}无法绘制对比柱形</strong>
      <span>
        左侧：{{ metricStatusLabel(viewModel.items[0].status) }}；
        右侧：{{ metricStatusLabel(viewModel.items[1].status) }}。
      </span>
    </div>
    <div v-else-if="chartUnavailable" class="comparison-chart__state comparison-chart__state--warning">
      图表运行库当前不可用，完整对比数值仍保留在下方表格中。
    </div>
    <template v-else-if="viewModel">
      <div ref="chartHost" class="comparison-chart__canvas" role="img" :aria-label="chartAriaLabel"></div>
      <div class="comparison-chart__summary">
        <div>
          <span>绝对差值（右－左）</span>
          <strong>{{ deltaText }}</strong>
        </div>
        <div>
          <span>相对变化</span>
          <strong>{{ relativeText }}</strong>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import type { EvaluationRunComparison } from '../types/evaluation';
import {
  buildComparisonChartViewModel,
  formatComparisonMetricValue,
  formatEvaluationNumber,
  metricStatusLabel,
  toChartValue
} from '../utils/evaluationPresentation';

const props = defineProps<{
  comparison: EvaluationRunComparison | null;
  metricId: string;
}>();

const chartHost = ref<HTMLElement | null>(null);
const chartUnavailable = ref(false);
let chartInstance: any = null;
let resizeObserver: ResizeObserver | null = null;

const viewModel = computed(() => buildComparisonChartViewModel(props.comparison, props.metricId));
const chartAriaLabel = computed(() => {
  const current = viewModel.value;
  return current ? `${current.displayName}在两个运行最终快照之间的对比` : '运行指标对比';
});

const deltaText = computed(() => {
  const metric = viewModel.value?.metric;
  if (!metric || metric.rightMinusLeft === null) return '--';
  const chartDelta = toChartValue(metric.rightMinusLeft, metric.unit);
  if (chartDelta === null) return '--';
  const prefix = chartDelta > 0 ? '+' : '';
  return `${prefix}${formatEvaluationNumber(chartDelta, 2)}${viewModel.value?.axisUnit ? ` ${viewModel.value.axisUnit}` : ''}`;
});

const relativeText = computed(() => {
  const ratio = viewModel.value?.metric.relativeChangeRatio;
  if (ratio === null || ratio === undefined || !Number.isFinite(ratio)) return '--';
  const prefix = ratio > 0 ? '+' : '';
  return `${prefix}${formatEvaluationNumber(ratio * 100, 2)}%`;
});

function getECharts(): any | null {
  return (window as Window & { echarts?: any }).echarts || null;
}

async function renderChart(): Promise<void> {
  await nextTick();
  const current = viewModel.value;
  if (!current?.comparable || !chartHost.value) {
    disposeChart();
    return;
  }
  const echarts = getECharts();
  if (!echarts) {
    chartUnavailable.value = true;
    disposeChart();
    return;
  }
  chartUnavailable.value = false;
  if (!chartInstance) chartInstance = echarts.init(chartHost.value);

  chartInstance.setOption({
    animation: false,
    aria: { enabled: true, description: chartAriaLabel.value },
    color: ['#337ecc'],
    grid: { left: 72, right: 24, top: 24, bottom: 58 },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const item = current.items[params?.dataIndex ?? -1];
        if (!item) return '';
        return [
          `<strong>${escapeHtml(current.displayName)}</strong>`,
          `运行：${escapeHtml(item.runId)}`,
          `最终值：${escapeHtml(formatComparisonMetricValue(item.rawValue, item.status, current.unit))}`,
          `状态：${escapeHtml(metricStatusLabel(item.status))}`
        ].join('<br/>');
      }
    },
    xAxis: {
      type: 'category',
      name: '运行最终快照',
      nameLocation: 'middle',
      nameGap: 40,
      data: current.items.map(item => item.runLabel),
      axisLabel: { interval: 0 }
    },
    yAxis: {
      type: 'value',
      min: (extent: { min: number }) => extent.min < 0 ? extent.min : 0,
      name: current.axisUnit ? `数值（${current.axisUnit}）` : '数值',
      axisLabel: { formatter: (value: number) => formatEvaluationNumber(value, 2) }
    },
    series: [{
      name: current.displayName,
      type: 'bar',
      barMaxWidth: 72,
      data: current.items.map(item => item.chartValue),
      label: {
        show: true,
        position: 'top',
        formatter: (params: any) => `${formatEvaluationNumber(Number(params.value), 2)}${current.axisUnit ? ` ${current.axisUnit}` : ''}`
      },
      emphasis: { focus: 'self' }
    }]
  }, true);
  chartInstance.resize();
  observeChartSize();
}

function observeChartSize(): void {
  resizeObserver?.disconnect();
  if (!chartHost.value || typeof ResizeObserver === 'undefined') return;
  resizeObserver = new ResizeObserver(() => chartInstance?.resize());
  resizeObserver.observe(chartHost.value);
}

function disposeChart(): void {
  resizeObserver?.disconnect();
  resizeObserver = null;
  chartInstance?.dispose?.();
  chartInstance = null;
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

watch(viewModel, () => void renderChart(), { deep: true });
onMounted(() => void renderChart());
onUnmounted(disposeChart);
</script>

<style scoped>
.comparison-chart {
  min-width: 0;
}

.comparison-chart__canvas {
  width: 100%;
  height: 280px;
}

.comparison-chart__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.comparison-chart__summary > div {
  padding: 10px 12px;
  border-top: 1px solid #e1e8f0;
}

.comparison-chart__summary span {
  display: block;
  color: #718096;
  font-size: 11px;
}

.comparison-chart__summary strong {
  display: block;
  margin-top: 4px;
  color: #2d4661;
  font-size: 15px;
  font-variant-numeric: tabular-nums;
}

.comparison-chart__state {
  display: grid;
  min-height: 220px;
  padding: 20px;
  place-content: center;
  gap: 7px;
  box-sizing: border-box;
  border: 1px dashed #d9e2ec;
  border-radius: 8px;
  background: #fafcfe;
  color: #718096;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.comparison-chart__state--warning {
  border-color: #e7d0a4;
  background: #fffaf0;
  color: #906421;
}

@media (max-width: 720px) {
  .comparison-chart__canvas { height: 250px; }
  .comparison-chart__summary { grid-template-columns: 1fr; }
}
</style>
