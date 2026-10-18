<template>
  <article class="domain-chart" :class="{ 'domain-chart--compact': compact }" :aria-label="model.title">
    <header>
      <h5 :title="model.description">{{ model.title }}</h5>
      <p v-if="!compact">{{ model.description }}</p>
    </header>
    <div v-if="!compact && model.kind !== 'bars' && (model.state === 'ready' || model.state === 'empty')" class="domain-chart__total">
      <span>{{ totalLabel }}</span><strong>{{ domainNumber(model.total) }} {{ domainUnit(model.unit) }}</strong>
    </div>
    <div
      v-if="model.state !== 'ready'"
      class="domain-chart__state"
      :class="{ 'domain-chart__state--invalid': model.state === 'invalid' }"
      role="status"
    >{{ model.message }}</div>
    <div v-else-if="chartWarning" class="domain-chart__state domain-chart__state--warning" role="status">
      {{ chartWarning }}；下方保留精确数值，完整指标明细可继续查阅。
    </div>
    <p v-for="notice in model.notices" :key="notice" class="domain-chart__notice" role="status">{{ notice }}</p>
    <div class="domain-chart__body" :class="{ 'domain-chart__body--ring': isRing }">
      <!-- 容器保持稳定，避免不可用状态恢复后将实例绑定到已移除的 DOM。 -->
      <div
        v-show="model.state === 'ready' && !chartWarning"
        ref="chartHost"
        class="domain-chart__canvas"
        :class="{ 'domain-chart__canvas--ring': isRing, 'domain-chart__canvas--bars': model.kind === 'bars' }"
        role="img"
        :aria-label="model.title"
      ></div>
      <div v-if="model.state === 'ready' || (showZeroLegend && model.state === 'empty')" class="domain-chart__legend" :class="{ 'domain-chart__legend--inline': !isRing }">
        <template v-if="model.inner.length">
          <h6>内环 · 执行状态</h6>
          <div v-for="item in model.inner" :key="`inner-${item.label}`" class="domain-chart__legend-item" :title="item.note || ''">
            <i :style="{ backgroundColor: item.color }"></i>
            <span>{{ item.label }}</span>
            <strong>{{ domainNumber(item.value, 0) }}</strong>
            <small>{{ shareText(item.value) }}</small>
          </div>
          <h6>外环 · 执行中细分</h6>
        </template>
        <div v-for="item in model.items" :key="item.label" class="domain-chart__legend-item" :title="item.note || ''">
          <i :style="{ backgroundColor: item.color }"></i>
          <span>{{ item.label }}</span>
          <strong>{{ domainNumber(item.value) }}<template v-if="!compact || !isRing"> {{ domainUnit(model.unit) }}</template></strong>
          <small v-if="model.kind !== 'bars'">{{ shareText(item.value) }}</small>
          <small v-if="item.threshold !== undefined">P95 阈值 {{ domainNumber(item.threshold) }} s</small>
        </div>
        <p v-if="model.inner.length" class="domain-chart__legend-note">执行中其他未进一步区分装卸或等待；非执行中不等同于可用。</p>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { domainChartOption, domainNumber, domainUnit } from '../utils/evaluationDomainPresentation';
import type { DomainChartModel } from '../utils/evaluationDomainPresentation';

// 紧凑模式仅供主仿真数据栏，完整评价页仍使用原默认布局。
const props = withDefaults(defineProps<{ model: DomainChartModel; visible?: boolean; compact?: boolean; centerLabel?: string; showZeroLegend?: boolean }>(),
  { visible: true, compact: false, centerLabel: '', showZeroLegend: false });
interface ChartInstance {
  setOption: (option: Record<string, unknown>, replace: boolean) => void;
  resize: () => void;
  dispose: () => void;
}
interface ChartRuntime { init: (element: HTMLElement) => ChartInstance }
const chartHost = ref<HTMLElement | null>(null);
const chartWarning = ref('');
const isRing = computed(() => props.model.kind === 'ring' || props.model.kind === 'nested-ring');
const totalLabel = computed(() => ({
  'vehicle-state': '车辆总数', 'vehicle-mileage': '总实际里程', 'vehicle-capacity': '总额定载重',
  'cargo-stages': '总需求吨位', 'task-states': '任务总数'
} as Record<string, string>)[props.model.id] || '总量');
let instance: ChartInstance | null = null;
let observer: ResizeObserver | null = null;
let mounted = false;
let renderRevision = 0;

function shareText(value: number): string {
  return props.model.total > 0 ? `${domainNumber(value / props.model.total * 100)}%` : '--';
}
function disposeChart(): void {
  observer?.disconnect();
  observer = null;
  instance?.dispose();
  instance = null;
}
async function renderChart(): Promise<void> {
  const revision = ++renderRevision;
  if (!mounted) return;
  // Phase 6：隐藏侧栏不停止共享数据源，只释放不可见图表；重新显示按最新模型绘制。
  if (!props.visible) { disposeChart(); return; }
  if (props.model.state !== 'ready') {
    chartWarning.value = '';
    disposeChart();
    return;
  }
  const runtime = (window as Window & { echarts?: ChartRuntime }).echarts;
  if (!runtime?.init) {
    chartWarning.value = '图表运行库当前不可用';
    disposeChart();
    return;
  }
  chartWarning.value = '';
  await nextTick();
  // 连续快照或快速切换分组只绘制最后一次有效请求，不留下跨运行图形。
  if (!mounted || !props.visible || revision !== renderRevision || !chartHost.value) return;
  try {
    if (!instance) {
      instance = runtime.init(chartHost.value);
      if (typeof ResizeObserver !== 'undefined') {
        // Phase 7：异步尺寸回调也隔离绘图异常，不能绕过 renderChart 的降级处理。
        observer = new ResizeObserver(() => {
          if (!mounted || !props.visible) return;
          try { instance?.resize(); }
          catch { disposeChart(); chartWarning.value = '图表暂时无法绘制'; }
        });
        observer.observe(chartHost.value);
      }
    }
    const option = domainChartOption(props.model);
    if (!props.compact && isRing.value && props.centerLabel) {
      option.title = { ...(option.title as Record<string, unknown>), subtext: props.centerLabel };
    }
    if (props.compact && isRing.value) {
      option.title = { ...(option.title as Record<string, unknown>), subtext: props.centerLabel || totalLabel.value,
        ...(props.showZeroLegend ? { text: `${domainNumber(props.model.total, 0)} ${domainUnit(props.model.unit)}` } : {}),
        textStyle: { color: '#24394f', fontSize: 23, fontWeight: 600 } };
    }
    instance.setOption(option, true);
    instance.resize();
  } catch {
    disposeChart();
    chartWarning.value = '图表暂时无法绘制';
  }
}
watch([() => props.model, () => props.visible, () => props.compact, () => props.centerLabel], () => void renderChart(), { flush: 'post' });
onMounted(() => { mounted = true; void renderChart(); });
onUnmounted(() => { mounted = false; renderRevision += 1; disposeChart(); });
</script>

<style scoped>
.domain-chart { min-width: 0; padding: 16px; border: 1px solid #e3eaf2; border-radius: 10px; background: #fff; }
.domain-chart h5 { margin: 0; color: #2d4661; font-size: 14px; }
.domain-chart header p { margin: 5px 0 0; color: #78879a; font-size: 11px; line-height: 1.6; }
.domain-chart__total { display: flex; align-items: baseline; gap: 9px; margin-top: 13px; font-size: 12px; color: #718096; }
.domain-chart__total strong { color: #24394f; font-size: 17px; font-variant-numeric: tabular-nums; }
.domain-chart__canvas { width: 100%; height: 125px; min-width: 0; }
.domain-chart__canvas--ring { height: 250px; flex: 1 1 180px; }
.domain-chart__canvas--bars { height: 175px; }
.domain-chart__body--ring { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 16px; }
.domain-chart__legend { display: grid; gap: 9px; min-width: 0; flex: 1 1 180px; margin-top: 10px; }
.domain-chart__legend--inline { display: flex; flex-wrap: wrap; gap: 10px 18px; }
.domain-chart__legend h6 { margin: 5px 0 0; font-size: 11px; color: #718096; font-weight: 500; }
.domain-chart__legend-item { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; color: #56687c; font-size: 11px; }
.domain-chart__legend-item i { display: inline-block; flex: 0 0 8px; height: 8px; border-radius: 2px; }
.domain-chart__legend-item strong { color: #2d4661; font-variant-numeric: tabular-nums; font-weight: 600; overflow-wrap: anywhere; }
.domain-chart__legend-item small { color: #8290a1; font-size: 10px; }
.domain-chart__legend-note { margin: 4px 0 0; color: #8290a1; font-size: 10px; line-height: 1.5; }
.domain-chart__state { padding: 20px 12px; margin-top: 14px; border-radius: 7px; background: #f6f8fb; color: #718096; font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.domain-chart__state--invalid { color: #aa4545; background: #fff4f4; }
.domain-chart__state--warning { color: #946825; background: #fff9ee; }
.domain-chart__notice { margin: 10px 0 0; color: #8290a1; font-size: 11px; line-height: 1.6; }
/* 不缩小整个页面：仅主数据栏的图形和图例紧凑，图例始终占一行。 */
.domain-chart--compact { padding: 10px; }
.domain-chart--compact .domain-chart__body--ring { display: block; }
.domain-chart--compact .domain-chart__canvas--ring { height: 170px; }
.domain-chart--compact .domain-chart__canvas { flex: none; }
.domain-chart--compact .domain-chart__legend { display: flex; flex-wrap: nowrap; gap: 8px; overflow-x: auto; margin-top: 5px; padding-bottom: 4px; }
.domain-chart--compact .domain-chart__legend-item { flex: 0 0 auto; flex-wrap: nowrap; white-space: nowrap; gap: 4px; }
.domain-chart--compact .domain-chart__legend-item small { display: none; }
.domain-chart--compact .domain-chart__state { margin-top: 8px; padding: 12px; }
.domain-chart--compact .domain-chart__legend-note { display: none; }
@media (max-width: 720px) {
  .domain-chart { padding: 13px; }
  .domain-chart__body--ring { flex-direction: column; align-items: stretch; }
  .domain-chart__canvas--ring { flex: none; height: 230px; }
  .domain-chart__legend { flex: none; }
}
@media (max-width: 720px) { .domain-chart--compact .domain-chart__canvas--ring { height: 170px; } }
</style>
