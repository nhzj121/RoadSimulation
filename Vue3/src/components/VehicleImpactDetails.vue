<template>
  <section v-if="vehicle && weather?.runId" class="vehicle-impact" aria-label="车辆行驶影响">
    <strong>车辆为什么变慢？</strong>
    <div>天气：{{ names[weather.weatherType] || weather.weatherType }} ×{{ factor(weather.speedFactor) }}</div>
    <div v-if="event?.eventType === 'TRAFFIC_CONGESTION'">单车拥堵：×{{ factor(event.speedFactor) }}</div>
    <div v-if="broken" class="impact-stopped">车辆故障：暂停行驶</div>
    <div v-if="driving || broken">当前有效速度：正常速度 ×{{ factor(vehicle.effectiveSpeedFactor) }}</div>
    <div v-else>当前正在{{ vehicle.statusText || '执行非驾驶操作' }}，天气不延长装卸时间。</div>
    <div v-if="event">预计{{ broken ? '维修完成' : '拥堵结束' }}：{{ time(event.plannedEndTime) }}（仿真时间）</div>
    <template v-if="hasProgress">
      <label>本段驾驶进度：{{ percent }}%</label>
      <progress :value="percent" max="100" aria-label="本段驾驶进度" />
      <div>剩余正常驾驶工作量：{{ (vehicle.remainingDrivingSeconds / 60).toFixed(1) }} 分钟</div>
      <small>指正常速度下还需开多久；实际到达时间还受后续天气、拥堵和故障影响。</small>
    </template>
    <small v-else>驾驶进度等待后端更新。</small>
    <small>动画倍速只调整地图播放速度，不改变上述仿真计算。</small>
  </section>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ vehicle: Object, weather: Object })
const names = { SUNNY: '晴天', RAIN: '雨天', SNOW: '雪天', FOG: '雾天' }
const event = computed(() => props.vehicle?.activeEvent)
const broken = computed(() => props.vehicle?.status === 'BREAKDOWN')
const driving = computed(() => ['ORDER_DRIVING', 'TRANSPORT_DRIVING'].includes(props.vehicle?.status))
const hasProgress = computed(() => props.vehicle?.drivingPhaseKey && Number.isFinite(props.vehicle?.drivingProgress) && Number.isFinite(props.vehicle?.remainingDrivingSeconds))
const percent = computed(() => Math.round(Math.max(0, Math.min(1, props.vehicle?.drivingProgress || 0)) * 100))
const factor = value => Number.isFinite(value) ? Number(value.toFixed(3)) : '等待更新'
const time = value => value ? String(value).replace('T', ' ').slice(0, 19) : '等待更新'
</script>

<style scoped>
.vehicle-impact { display: grid; gap: 7px; margin-top: 12px; padding: 12px; background: #eef5ff; border-radius: 8px; color: #26374d; font-size: 12px; overflow-wrap: anywhere; }
.vehicle-impact small { color: #596779; line-height: 1.6; }
.vehicle-impact progress { width: 100%; accent-color: #409eff; }
.impact-stopped { color: #b42318; font-weight: 600; }
</style>
