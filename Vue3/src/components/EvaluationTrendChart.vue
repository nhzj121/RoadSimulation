<template>
  <section class="evaluation-chart" aria-label="评价指标历史趋势">
    <div v-if="loading" class="evaluation-chart__state">正在读取历史趋势…</div>
    <div v-else-if="!viewModel || !viewModel.availablePointCount" class="evaluation-chart__state">
      所选指标在该运行中没有可绘制的可用值；原始状态仍可在下方表格查看。
    </div>
    <div v-else-if="chartUnavailable" class="evaluation-chart__state evaluation-chart__state--warning">
      图表运行库当前不可用，已保留原始趋势表格供精确查阅。
    </div>
    <div v-else ref="chartHost" class="evaluation-chart__canvas" role="img" :aria-label="chartAriaLabel"></div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import type { EvaluationTrend } from '../types/evaluation';
import {
  buildTrendChartViewModel,
  formatEvaluationNumber,
  metricStatusLabel
} from '../utils/evaluationPresentation';

const props = defineProps<{
  trend: EvaluationTrend | null;
  metricId: string;
  loading?: boolean;
}>();

const chartHost = ref<HTMLElement | null>(null);
const chartUnavailable = ref(false);
let chartInstance: any = null;
let resizeObserver: ResizeObserver | null = null;

const viewModel = computed(() => buildTrendChartViewModel(props.trend, props.metricId));
const chartAriaLabel = computed(() => {
  const current = viewModel.value;
  return current
    ? `${current.displayName}随仿真轮次变化，共${current.availablePointCount}个可用数据点`
    : '评价指标历史趋势';
});

function getECharts(): any | null {
  return (window as Window & { echarts?: any }).echarts || null;
}

async function renderChart(): Promise<void> {
  await nextTick();
  const current = viewModel.value;
  if (!current?.availablePointCount || !chartHost.value) {
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
    grid: { left: 72, right: 26, top: 28, bottom: 54, containLabel: false },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      formatter: (params: any) => {
        const item = Array.isArray(params) ? params[0] : params;
        const point = current.points[item?.dataIndex ?? -1];
        if (!point) return '';
        const valueText = point.chartValue === null
          ? metricStatusLabel(point.metricStatus)
          : `${formatEvaluationNumber(point.chartValue, 2)}${current.axisUnit ? ` ${current.axisUnit}` : ''}`;
        const reason = point.reason ? `<br/>说明：${escapeHtml(point.reason)}` : '';
        return [
          `<strong>${escapeHtml(current.displayName)}</strong>`,
          `Loop：${point.loopIndex}`,
          `仿真时间：${escapeHtml(point.simTime.replace('T', ' '))}`,
          `指标值：${escapeHtml(valueText)}`,
          `指标状态：${escapeHtml(metricStatusLabel(point.metricStatus))}${reason}`
        ].join('<br/>');
      }
    },
    xAxis: {
      type: 'category',
      name: '仿真轮次',
      nameLocation: 'middle',
      nameGap: 34,
      boundaryGap: false,
      data: current.points.map(point => String(point.loopIndex)),
      axisLabel: { hideOverlap: true },
      axisTick: { alignWithLabel: true }
    },
    yAxis: {
      type: 'value',
      name: current.axisUnit ? `数值（${current.axisUnit}）` : '数值',
      nameGap: 14,
      scale: true,
      axisLabel: {
        formatter: (value: number) => formatEvaluationNumber(value, 2)
      }
    },
    series: [{
      name: current.displayName,
      type: 'line',
      smooth: false,
      connectNulls: false,
      showSymbol: current.points.length <= 30,
      symbolSize: 6,
      data: current.points.map(point => point.chartValue),
      lineStyle: { width: 2 },
      emphasis: { focus: 'series' }
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

watch([viewModel, () => props.loading], () => void renderChart(), { deep: true });
onMounted(() => void renderChart());
onUnmounted(disposeChart);
</script>

<style scoped>
.evaluation-chart {
  min-width: 0;
}

.evaluation-chart__canvas {
  width: 100%;
  height: 330px;
}

.evaluation-chart__state {
  display: grid;
  min-height: 240px;
  padding: 20px;
  place-items: center;
  box-sizing: border-box;
  border: 1px dashed #d9e2ec;
  border-radius: 8px;
  background: #fafcfe;
  color: #718096;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.evaluation-chart__state--warning {
  border-color: #e7d0a4;
  background: #fffaf0;
  color: #906421;
}

@media (max-width: 720px) {
  .evaluation-chart__canvas { height: 280px; }
}
</style>
