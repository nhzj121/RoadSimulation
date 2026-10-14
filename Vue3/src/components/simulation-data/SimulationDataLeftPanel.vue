<template>
  <section class="data-left-panel" aria-labelledby="simulation-data-left-title">
    <header class="data-left-panel__header">
      <div>
        <span class="data-left-panel__eyebrow">LIVE OPERATIONS</span>
        <h3 id="simulation-data-left-title">运行健康与运力效率</h3>
      </div>
      <span class="data-left-panel__connection" :class="connectionClass">
        <i></i>{{ connectionText }}
      </span>
    </header>

    <div v-if="networkError" class="data-left-panel__message data-left-panel__message--error" role="alert">
      {{ networkError }}<template v-if="snapshot">；继续显示最近一次成功快照。</template>
    </div>

    <div v-if="initialLoading" class="data-left-panel__state" aria-live="polite">
      <span class="data-left-panel__loading-mark"></span>
      <strong>正在读取实时评价</strong>
      <small>从现有评价快照接口获取数据。</small>
    </div>

    <div v-else-if="!snapshot && networkError" class="data-left-panel__state data-left-panel__state--error">
      <strong>实时评价读取失败</strong>
      <small>面板将自动重试，不影响仿真主循环。</small>
    </div>

    <div v-else-if="waitingForSnapshot || !viewModel" class="data-left-panel__state">
      <strong>等待首个评价快照</strong>
      <small>后端完成一个仿真循环后显示运行数据。</small>
    </div>

    <template v-else>
      <section class="data-left-panel__run" aria-label="当前运行状态">
        <div>
          <span>Loop</span>
          <strong>{{ viewModel.loopIndex }}</strong>
        </div>
        <div>
          <span>仿真时间</span>
          <strong>{{ viewModel.simTimeText }}</strong>
        </div>
        <span
          class="data-left-panel__snapshot-status"
          :class="`data-left-panel__snapshot-status--${viewModel.snapshotStatusClass}`"
        >
          {{ viewModel.snapshotStatusText }}
        </span>
      </section>

      <section class="data-left-panel__health" aria-label="本轮运行健康">
        <div>
          <span>处理任务</span>
          <strong>{{ viewModel.processedAssignmentCount }}</strong>
        </div>
        <div :class="{ 'data-left-panel__health-item--warning': viewModel.failedAssignmentCount > 0 }">
          <span>失败任务</span>
          <strong>{{ viewModel.failedAssignmentCount }}</strong>
        </div>
        <div :class="{ 'data-left-panel__health-item--warning': viewModel.errorCount > 0 }">
          <span>本轮错误</span>
          <strong>{{ viewModel.errorCount }}</strong>
        </div>
      </section>

      <section class="data-left-panel__section" aria-labelledby="capacity-kpi-title">
        <div class="data-left-panel__section-title">
          <div>
            <span>当前状态</span>
            <h4 id="capacity-kpi-title">车辆与运力</h4>
          </div>
          <small>后端评价快照</small>
        </div>
        <div class="data-left-panel__kpi-grid">
          <article
            v-for="kpi in viewModel.kpis"
            :key="kpi.metricId"
            class="data-left-panel__kpi"
            :class="`data-left-panel__kpi--${kpi.status.toLowerCase().replace('_', '-')}`"
            :title="kpi.reason || kpi.statusText"
          >
            <span>{{ kpi.label }}</span>
            <strong>{{ kpi.valueText }}</strong>
            <small v-if="kpi.status !== 'AVAILABLE'">{{ kpi.statusText }}</small>
          </article>
        </div>
        <div
          class="data-left-panel__remaining"
          :title="viewModel.remainingCapacity.reason || viewModel.remainingCapacity.statusText"
        >
          <span>{{ viewModel.remainingCapacity.label }}</span>
          <strong>{{ viewModel.remainingCapacity.valueText }}</strong>
        </div>
      </section>

      <section class="data-left-panel__section data-left-panel__trend" aria-labelledby="capacity-trend-title">
        <div class="data-left-panel__section-title">
          <div>
            <span>本次打开期间</span>
            <h4 id="capacity-trend-title">运力效率趋势</h4>
          </div>
          <small>最多 30 个快照</small>
        </div>
        <div v-if="!viewModel.trend.availablePointCount" class="data-left-panel__chart-state">
          当前两个效率指标暂无可绘制数值，缺失值不会按 0 展示。
        </div>
        <div v-if="viewModel.trend.availablePointCount && chartUnavailable" class="data-left-panel__chart-state data-left-panel__chart-state--warning" role="status">
          图表暂不可用；上方当前值仍可正常查阅，将在下一次更新或重新展开时重试。
        </div>
        <!-- Phase 6 修复：警告只隐藏容器，不销毁 DOM，恢复后可重新绑定图表。 -->
        <div
          v-show="viewModel.trend.availablePointCount && !chartUnavailable"
          ref="chartHost"
          class="data-left-panel__chart"
          role="img"
          aria-label="车辆空载里程率与里程加权载重率随仿真轮次变化"
        ></div>
      </section>

      <footer class="data-left-panel__footer">
        <span>每 1 s 检查最新快照</span>
        <ElButton type="primary" plain size="small" @click="emit('open-full')">完整评价</ElButton>
      </footer>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { ElButton } from 'element-plus';
import type { SimulationEvaluationFeedState } from '../../composables/useSimulationEvaluationFeed';
import type { EvaluationSnapshot } from '../../types/evaluation';
import { buildSimulationDataLeftViewModel } from '../../utils/simulationDataLeftPresentation';

// Phase 6：左栏只负责展示与图表；共享轮询由父级工作区持有。
const props = withDefaults(defineProps<{
  feedState: SimulationEvaluationFeedState;
  trendSnapshots: EvaluationSnapshot[];
  visible?: boolean;
}>(), { visible: true });
const emit = defineEmits<{ (event: 'open-full'): void }>();
const snapshot = computed(() => props.feedState.snapshot);
const initialLoading = computed(() => props.feedState.initialLoading);
const waitingForSnapshot = computed(() => props.feedState.waitingForSnapshot);
const networkError = computed(() => props.feedState.networkError);
const polling = computed(() => props.feedState.polling);
const trendSnapshots = computed(() => props.trendSnapshots);
const chartHost = ref<HTMLElement | null>(null);
const chartUnavailable = ref(false);
let chartInstance: any = null;
let chartResizeObserver: ResizeObserver | null = null;
let chartMounted = false;
let chartRenderRevision = 0;

const viewModel = computed(() => snapshot.value
  ? buildSimulationDataLeftViewModel(snapshot.value, trendSnapshots.value)
  : null
);

const connectionText = computed(() => {
  if (networkError.value) return '连接异常';
  if (initialLoading.value || (polling.value && !snapshot.value)) return '读取中';
  if (waitingForSnapshot.value) return '等待快照';
  return '动态更新';
});

const connectionClass = computed(() => ({
  'data-left-panel__connection--error': Boolean(networkError.value),
  'data-left-panel__connection--waiting': waitingForSnapshot.value && !networkError.value
}));

function getECharts(): any | null {
  return (window as Window & { echarts?: any }).echarts || null;
}

async function renderChart(): Promise<void> {
  const revision = ++chartRenderRevision;
  if (!chartMounted || !props.visible) { disposeChart(); return; }
  const trend = viewModel.value?.trend;
  if (!trend?.availablePointCount) {
    chartUnavailable.value = false;
    disposeChart();
    return;
  }
  const echarts = getECharts();
  if (!echarts?.init) {
    chartUnavailable.value = true;
    disposeChart();
    return;
  }
  chartUnavailable.value = false;
  await nextTick();
  if (!chartMounted || !props.visible || revision !== chartRenderRevision || !chartHost.value) return;
  // Phase 6 修复：图表初始化/绘制失败只降级展示，不能形成未处理的异步异常。
  try {
    if (!chartInstance) chartInstance = echarts.init(chartHost.value);
    chartInstance.setOption({
      animation: false,
      aria: {
        enabled: true,
        description: '车辆空载里程率与里程加权载重率随仿真轮次变化'
      },
      color: ['#d9902f', '#337ecc'],
      grid: { left: 42, right: 16, top: 42, bottom: 38 },
      legend: { top: 4, left: 0, itemWidth: 12, itemHeight: 6, textStyle: { fontSize: 10 } },
      tooltip: {
        trigger: 'axis',
        valueFormatter: (value: number | null) => value === null || value === undefined
          ? '无可用值'
          : `${Number(value).toFixed(2)}%`
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: trend.categories,
        axisLabel: { hideOverlap: true, fontSize: 10 },
        axisTick: { alignWithLabel: true }
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        name: '%',
        nameGap: 8,
        axisLabel: { formatter: '{value}%', fontSize: 10 },
        splitLine: { lineStyle: { color: '#edf0f5' } }
      },
      series: trend.series.map(item => ({
        name: item.name,
        type: 'line',
        smooth: false,
        connectNulls: false,
        showSymbol: trend.categories.length <= 15,
        symbolSize: 5,
        data: item.data,
        lineStyle: { width: 2 },
        emphasis: { focus: 'series' }
      }))
    }, true);
    chartInstance.resize();
    observeChartSize();
  } catch {
    chartUnavailable.value = true;
    disposeChart();
  }
}

function observeChartSize(): void {
  chartResizeObserver?.disconnect();
  if (!chartHost.value || typeof ResizeObserver === 'undefined') return;
  chartResizeObserver = new ResizeObserver(() => {
    if (!props.visible || !chartMounted) return;
    try { chartInstance?.resize(); }
    catch { chartUnavailable.value = true; disposeChart(); }
  });
  chartResizeObserver.observe(chartHost.value);
}

function disposeChart(): void {
  chartResizeObserver?.disconnect();
  chartResizeObserver = null;
  chartInstance?.dispose?.();
  chartInstance = null;
}

watch([viewModel, () => props.visible], () => void renderChart(), { deep: true, flush: 'post' });
onMounted(() => { chartMounted = true; void renderChart(); });
onUnmounted(() => { chartMounted = false; chartRenderRevision++; disposeChart(); });
</script>

<style scoped>
.data-left-panel {
  height: 100%;
  padding: 14px;
  box-sizing: border-box;
  overflow-y: auto;
  color: #263445;
  background: #f5f7fa;
}

.data-left-panel__header,
.data-left-panel__section-title,
.data-left-panel__footer,
.data-left-panel__run {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.data-left-panel__header { margin-bottom: 12px; }
.data-left-panel__header h3 { margin: 2px 0 0; font-size: 18px; }
.data-left-panel__eyebrow { color: #337ecc; font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.data-left-panel__connection { display: inline-flex; align-items: center; gap: 6px; color: #28784a; font-size: 12px; white-space: nowrap; }
.data-left-panel__connection i { width: 7px; height: 7px; border-radius: 50%; background: #31a66a; box-shadow: 0 0 0 4px rgba(49, 166, 106, .12); }
.data-left-panel__connection--waiting { color: #9b6a14; }
.data-left-panel__connection--waiting i { background: #d9a441; box-shadow: 0 0 0 4px rgba(217, 164, 65, .14); }
.data-left-panel__connection--error { color: #c45656; }
.data-left-panel__connection--error i { background: #d85b5b; box-shadow: 0 0 0 4px rgba(216, 91, 91, .14); }

.data-left-panel__message { margin-bottom: 10px; padding: 10px; border-radius: 8px; font-size: 12px; line-height: 1.5; }
.data-left-panel__message--error { border: 1px solid #f1c4c4; color: #b94b4b; background: #fff6f6; }

.data-left-panel__state { min-height: 220px; padding: 20px; border: 1px dashed #d9e2ec; border-radius: 10px; display: grid; place-content: center; gap: 8px; color: #718096; background: #fff; text-align: center; }
.data-left-panel__state--error { border-color: #f1c4c4; background: #fffafa; }
.data-left-panel__state small { max-width: 240px; line-height: 1.6; }
.data-left-panel__loading-mark { width: 24px; height: 24px; margin: 0 auto 4px; border: 3px solid #d9ecff; border-top-color: #409eff; border-radius: 50%; animation: data-left-spin .8s linear infinite; }
@keyframes data-left-spin { to { transform: rotate(360deg); } }

.data-left-panel__run { padding: 10px 12px; border: 1px solid #dfe7f1; border-radius: 10px; background: #fff; }
.data-left-panel__run > div { display: grid; gap: 2px; }
.data-left-panel__run span { color: #7b8797; font-size: 11px; }
.data-left-panel__run strong { font-size: 13px; font-variant-numeric: tabular-nums; }
.data-left-panel__snapshot-status { padding: 4px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.data-left-panel__snapshot-status--complete { color: #28784a !important; background: #eaf7ef; }
.data-left-panel__snapshot-status--partial { color: #9b6a14 !important; background: #fff5df; }
.data-left-panel__snapshot-status--failed { color: #b94b4b !important; background: #fff0f0; }

.data-left-panel__health { margin-top: 10px; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.data-left-panel__health > div { padding: 9px 10px; border: 1px solid #e4eaf1; border-radius: 8px; display: grid; gap: 3px; background: #fff; }
.data-left-panel__health span { color: #7b8797; font-size: 10px; }
.data-left-panel__health strong { font-size: 17px; font-variant-numeric: tabular-nums; }
.data-left-panel__health-item--warning strong { color: #c45656; }

.data-left-panel__section { margin-top: 12px; padding: 12px; border: 1px solid #dfe7f1; border-radius: 10px; background: #fff; }
.data-left-panel__section-title span { color: #7b8797; font-size: 10px; }
.data-left-panel__section-title h4 { margin: 2px 0 0; font-size: 14px; }
.data-left-panel__section-title small { color: #909399; font-size: 10px; }
.data-left-panel__kpi-grid { margin-top: 10px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.data-left-panel__kpi { min-width: 0; padding: 10px; border: 1px solid #edf0f5; border-radius: 8px; display: grid; gap: 5px; background: #fafcfe; }
.data-left-panel__kpi > span { min-height: 30px; color: #718096; font-size: 11px; line-height: 15px; }
.data-left-panel__kpi strong { overflow-wrap: anywhere; color: #24394f; font-size: 18px; font-variant-numeric: tabular-nums; }
.data-left-panel__kpi small { color: #b88230; font-size: 10px; }
.data-left-panel__kpi--invalid strong { color: #c45656; }
.data-left-panel__remaining { margin-top: 8px; padding: 9px 10px; border-left: 3px solid #409eff; display: flex; align-items: center; justify-content: space-between; gap: 10px; background: #f2f7fd; }
.data-left-panel__remaining span { color: #606f7b; font-size: 11px; }
.data-left-panel__remaining strong { font-size: 15px; font-variant-numeric: tabular-nums; }

.data-left-panel__chart { width: 100%; height: 220px; margin-top: 8px; }
.data-left-panel__chart-state { min-height: 150px; margin-top: 10px; padding: 16px; border: 1px dashed #d9e2ec; border-radius: 8px; display: grid; place-items: center; box-sizing: border-box; color: #718096; background: #fafcfe; font-size: 12px; line-height: 1.6; text-align: center; }
.data-left-panel__chart-state--warning { border-color: #e7d0a4; color: #906421; background: #fffaf0; }
.data-left-panel__footer { padding: 12px 2px 2px; color: #909399; font-size: 11px; }

@media (max-width: 1400px) {
  .data-left-panel__kpi-grid { grid-template-columns: 1fr; }
  .data-left-panel__kpi > span { min-height: auto; }
}
</style>
