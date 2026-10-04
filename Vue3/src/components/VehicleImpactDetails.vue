<template>
  <section v-if="vehicle && weather?.runId" class="vehicle-impact" aria-label="车辆行驶影响">
    <strong>车辆为什么变慢？</strong>
    <div>天气：{{ names[weather.weatherType] || weather.weatherType }} ×{{ factor(weather.speedFactor) }}</div>
    <div v-if="event?.eventType === 'TRAFFIC_CONGESTION'">单车拥堵：×{{ factor(event.speedFactor) }}</div>
    <div v-if="stopped" class="impact-stopped">{{ statusPresentation.text }}：暂停行驶</div>
    <div v-if="event?.eventType === 'VEHICLE_BREAKDOWN'">{{ describeBreakdown(event) }}</div>
    <div v-if="event?.eventType === 'VEHICLE_BREAKDOWN' && event.breakdownPhase === 'WAITING_RESCUE'">救援为仿真阶段，不代表真实救援车辆已派出。</div>
    <div v-if="driving || stopped">当前有效速度：正常速度 ×{{ factor(vehicle.effectiveSpeedFactor) }}</div>
    <div v-else>当前正在{{ vehicle.statusText || '执行非驾驶操作' }}，天气不延长装卸时间。</div>
    <div v-if="event">预计{{ event.breakdownLevel === 'REPLACEMENT_REQUIRED' ? '替换车辆就绪' : stopped ? '维修完成' : '拥堵结束' }}：{{ time(event.plannedEndTime) }}（仿真时间）</div>
    <div v-if="event?.repairStartTime">维修开始：{{ time(event.repairStartTime) }}（仿真时间）</div>
    <div v-if="event?.resolvedTime">维修结束：{{ time(event.resolvedTime) }}（仿真时间）</div>
    <small>动画倍速只调整地图播放速度，不计入上述速度倍率。</small>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { describeBreakdown } from '../utils/breakdownPresentation'
import { isStoppedVehicleStatus, vehicleStatusPresentation } from '../utils/vehicleStatusPresentation'
const props = defineProps({ vehicle: Object, weather: Object })
const names = { SUNNY: '晴天', RAIN: '雨天', SNOW: '雪天', FOG: '雾天' }
const event = computed(() => props.vehicle?.activeEvent)
const statusPresentation = computed(() => vehicleStatusPresentation(props.vehicle?.status))
const stopped = computed(() => isStoppedVehicleStatus(props.vehicle?.status))
const driving = computed(() => ['ORDER_DRIVING', 'TRANSPORT_DRIVING'].includes(props.vehicle?.status))
const factor = value => Number.isFinite(value) ? Number(value.toFixed(3)) : '等待更新'
const time = value => value ? String(value).replace('T', ' ').slice(0, 19) : '等待更新'
</script>

<style scoped>
.vehicle-impact { display: grid; gap: 7px; margin-top: 12px; padding: 12px; background: #eef5ff; border-radius: 8px; color: #26374d; font-size: 12px; overflow-wrap: anywhere; }
.vehicle-impact small { color: #596779; line-height: 1.6; }
.vehicle-impact progress { width: 100%; accent-color: #409eff; }
.impact-stopped { color: #b42318; font-weight: 600; }
</style>
