<template>
  <div class="presentation-chart" :style="{ '--chart-height': `${height}px` }">
    <div v-if="warning || !option" class="presentation-chart__state" role="status">{{ warning || emptyText }}</div>
    <div v-show="option && !warning" ref="chartHost" class="presentation-chart__canvas" role="img" :aria-label="label"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue';
const props = withDefaults(defineProps<{ option: Record<string, unknown> | null; label: string; visible?: boolean; height?: number; emptyText?: string }>(),
  { visible: true, height: 220, emptyText: '暂无可绘制数据' });
interface Chart { setOption: (option: Record<string, unknown>, replace: boolean) => void; resize: () => void; dispose: () => void }
const chartHost = ref<HTMLElement | null>(null), warning = ref('');
let instance: Chart | null = null, observer: ResizeObserver | null = null, mounted = false, revision = 0;
function dispose() { observer?.disconnect(); observer = null; instance?.dispose(); instance = null; }
function fail() { dispose(); warning.value = '图表暂不可用'; }
async function renderChart() {
  const current = ++revision;
  if (!mounted || !props.visible || !props.option) { dispose(); if (!props.option) warning.value = ''; return; }
  const runtime = (window as Window & { echarts?: { init: (host: HTMLElement) => Chart } }).echarts;
  if (!runtime?.init) { fail(); return; }
  warning.value = '';
  await nextTick();
  if (!mounted || !props.visible || current !== revision || !chartHost.value || !props.option) return;
  try {
    if (!instance) {
      instance = runtime.init(chartHost.value);
      if (typeof ResizeObserver !== 'undefined') {
        observer = new ResizeObserver(() => { if (mounted && props.visible) { try { instance?.resize(); } catch { fail(); } } });
        observer.observe(chartHost.value);
      }
    }
    instance.setOption(props.option, true); instance.resize();
  } catch { fail(); }
}
watch([() => props.option, () => props.visible], () => void renderChart(), { flush: 'post' });
onMounted(() => { mounted = true; void renderChart(); });
onUnmounted(() => { mounted = false; revision++; dispose(); });
</script>

<style scoped>
.presentation-chart { min-width: 0; }
.presentation-chart__canvas { width: 100%; height: var(--chart-height); }
.presentation-chart__state { min-height: var(--chart-height); display: grid; place-content: center; color: #8290a1; font-size: 11px; text-align: center; }
</style>
