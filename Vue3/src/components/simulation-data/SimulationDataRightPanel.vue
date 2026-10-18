<template>
  <section class="data-right-panel" aria-labelledby="simulation-data-right-title">
    <header class="data-right-panel__header"><h3 id="simulation-data-right-title">车辆与货物</h3><small>不同数据口径分区展示</small></header>
    <!-- 地图投影不依赖评价接口是否成功，不以后台任务终态隐藏仍可见的动画车辆。 -->
    <section class="data-right-panel__section">
      <span class="data-right-panel__source">前端地图展示</span>
      <nav class="data-right-panel__modes" aria-label="车辆状态统计模式">
        <button type="button" :aria-pressed="vehicleMode === 'current'" @click="vehicleMode = 'current'">当前状态</button>
        <button type="button" :aria-pressed="vehicleMode === 'activity'" @click="vehicleMode = 'activity'">最近5分钟活动</button>
      </nav>
      <EvaluationDomainChart :model="selectedVehicleChart" :visible="visible" compact show-zero-legend
        :center-label="vehicleMode === 'current' ? '当前可见车辆' : '状态进入次数'" />
      <p v-if="vehicleMode === 'activity'" class="data-right-panel__activity-note">本页观察 · 实际时间窗口 · 暂停仍过期；次数不代表工作量。</p>
    </section>
    <section class="data-right-panel__section">
      <span class="data-right-panel__source">后端履约 · 总需求 {{ cargoTotalText }} t</span>
      <div v-if="feedState.networkError" class="data-right-panel__message" role="alert">
        实时评价读取失败：{{ feedState.networkError }}<template v-if="feedState.snapshot">；保留最近成功快照。</template>
      </div>
      <div v-if="feedState.initialLoading" class="data-right-panel__state">正在读取货物履约数据。</div>
      <div v-else-if="feedState.waitingForSnapshot || !cargoChart" class="data-right-panel__state">等待首个评价快照。</div>
      <EvaluationDomainChart v-else :model="cargoChart" :visible="visible" compact />
    </section>
    <footer class="data-right-panel__footer"><span>精确明细保留在完整评价页</span><ElButton type="primary" plain size="small" @click="emit('open-full')">完整评价</ElButton></footer>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElButton } from 'element-plus';
import type { SimulationEvaluationFeedState } from '../../composables/useSimulationEvaluationFeed';
import { buildDomainViewModel, domainNumber } from '../../utils/evaluationDomainPresentation';
import { buildVisibleVehicleChart, type VisibleVehicleDisplay } from '../../utils/simulationWorkspacePresentation';
import { createVehicleActivityWindow } from '../../utils/vehicleActivityPresentation';
import type { DomainChartModel } from '../../utils/evaluationDomainPresentation';
import EvaluationDomainChart from '../EvaluationDomainChart.vue';
const props = withDefaults(defineProps<{ feedState: SimulationEvaluationFeedState; visible?: boolean; visibleVehicles?: VisibleVehicleDisplay[]; activityChart?: DomainChartModel }>(),
  { visible: true, visibleVehicles: () => [] });
const emit = defineEmits<{ (event: 'open-full'): void }>();
// 只读取父级已存在的投影，原投影、状态管理器和车辆动画完全不改。
const vehicleChart = computed(() => buildVisibleVehicleChart(props.visibleVehicles));
const vehicleMode = ref<'current' | 'activity'>('current');
const emptyActivityChart = createVehicleActivityWindow().chart(0);
const selectedVehicleChart = computed(() => vehicleMode.value === 'current' ? vehicleChart.value : props.activityChart || emptyActivityChart);
const cargoChart = computed(() => props.feedState.snapshot ? buildDomainViewModel(props.feedState.snapshot, 'cargo')?.primary || null : null);
const cargoTotalText = computed(() => cargoChart.value && ['ready', 'empty'].includes(cargoChart.value.state) ? domainNumber(cargoChart.value.total) : '--');
</script>
<style scoped>
.data-right-panel { min-height: 0; padding: 14px; box-sizing: border-box; color: #263445; background: #f5f7fa; }
.data-right-panel__header, .data-right-panel__footer { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.data-right-panel__header h3 { margin: 0; font-size: 17px; }
.data-right-panel__header small { font-size: 10px; color: #8290a1; }
.data-right-panel__section { margin-top: 12px; border: 1px solid #dfe7f1; border-radius: 10px; background: #fff; overflow: hidden; }
.data-right-panel__section :deep(.domain-chart) { border: 0; }
.data-right-panel__source { display: block; padding: 9px 10px 0; font-size: 10px; color: #8290a1; }
.data-right-panel__modes { display: flex; gap: 4px; padding: 8px 10px 0; }
.data-right-panel__modes button { border: 1px solid #dfe7f1; border-radius: 5px; padding: 4px 7px; background: #fff; color: #657b98; font-size: 11px; cursor: pointer; }
.data-right-panel__modes button[aria-pressed="true"] { color: #337ecc; background: #ecf5ff; border-color: #a6cef4; }
.data-right-panel__modes button:focus-visible { outline: 2px solid #337ecc; outline-offset: 2px; }
.data-right-panel__activity-note { margin: 0; padding: 0 10px 10px; color: #8290a1; font-size: 10px; line-height: 1.5; }
.data-right-panel__state { padding: 20px 12px; color: #8290a1; font-size: 12px; }
.data-right-panel__message { margin: 8px 10px; padding: 8px; color: #b94b4b; background: #fff6f6; border-radius: 6px; font-size: 11px; overflow-wrap: anywhere; }
.data-right-panel__footer { padding-top: 12px; font-size: 10px; color: #8290a1; }
</style>
