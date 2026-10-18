<template>
  <section class="vehicle-display-status" aria-label="前端地图车辆状态与活动">
    <p class="vehicle-display-status__source">前端地图展示口径 · 仅此图使用可见车辆；其余车辆评价指标仍使用后端快照。</p>
    <nav class="vehicle-display-status__modes" aria-label="车辆状态统计模式">
      <button type="button" :aria-pressed="mode === 'current'" @click="mode = 'current'">当前状态</button>
      <button type="button" :aria-pressed="mode === 'activity'" @click="mode = 'activity'">最近5分钟活动</button>
    </nav>
    <EvaluationDomainChart :model="selectedChart" :visible="visible" compact show-zero-legend
      :center-label="mode === 'current' ? '当前可见车辆' : '状态进入次数'" />
    <p v-if="mode === 'activity'" class="vehicle-display-status__note">本页观察 · 实际时间窗口 · 暂停仍过期；次数不代表工作量。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import EvaluationDomainChart from './EvaluationDomainChart.vue';
import type { DomainChartModel } from '../utils/evaluationDomainPresentation';
import { buildVisibleVehicleChart, type VisibleVehicleDisplay } from '../utils/simulationWorkspacePresentation';
import { createVehicleActivityWindow } from '../utils/vehicleActivityPresentation';

// 仅消费父页面已存在的车辆投影与活动窗口；不注册第二份观察者、不获取业务数据。
const props = withDefaults(defineProps<{
  visibleVehicles?: VisibleVehicleDisplay[];
  activityChart?: DomainChartModel;
  visible?: boolean;
}>(), { visibleVehicles: () => [], visible: true });
const mode = ref<'current' | 'activity'>('current');
const emptyActivity = createVehicleActivityWindow().chart(0);
const selectedChart = computed(() => mode.value === 'current'
  ? buildVisibleVehicleChart(props.visibleVehicles) : props.activityChart || emptyActivity);
</script>

<style scoped>
.vehicle-display-status { min-width: 0; border: 1px solid #e3eaf2; border-radius: 10px; background: #fff; overflow: hidden; }
.vehicle-display-status__source, .vehicle-display-status__note { margin: 0; padding: 12px 14px 0; color: #718096; font-size: 11px; line-height: 1.6; }
.vehicle-display-status__modes { display: flex; flex-wrap: wrap; gap: 5px; padding: 10px 14px 0; }
.vehicle-display-status__modes button { padding: 5px 9px; border: 1px solid #dfe7f1; border-radius: 5px; background: #fff; color: #657b98; font-size: 12px; cursor: pointer; }
.vehicle-display-status__modes button[aria-pressed="true"] { color: #337ecc; background: #ecf5ff; border-color: #a6cef4; }
.vehicle-display-status__modes button:focus-visible { outline: 2px solid #337ecc; outline-offset: 2px; }
.vehicle-display-status :deep(.domain-chart) { padding: 14px; border: 0; }
.vehicle-display-status__note { padding-top: 0; padding-bottom: 12px; }
</style>
