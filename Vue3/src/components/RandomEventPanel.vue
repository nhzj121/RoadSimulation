<template>
  <ElCard shadow="never" class="box-card random-event-card">
    <template #header>
      <div class="event-card-header">
        <span>随机运输事件</span>
        <ElTag v-if="activeEvents.length" type="danger" size="small">
          活跃 {{ activeEvents.length }}
        </ElTag>
      </div>
    </template>

    <div class="event-form">
      <label for="random-event-vehicle">运输车辆</label>
      <ElSelect id="random-event-vehicle" v-model="vehicleId" placeholder="选择运输车辆" size="small" :disabled="disabled" aria-label="运输车辆">
        <ElOption
            v-for="vehicle in vehicles"
            :key="vehicle.vehicleId"
            :label="`${vehicle.licensePlate || `车辆${vehicle.vehicleId}`} · ${statusText(vehicle.status)}`"
            :value="vehicle.vehicleId"
        />
      </ElSelect>
      <label for="congestion-duration">拥堵持续时长（分钟）</label>
      <ElInputNumber id="congestion-duration" v-model="durationMinutes" aria-label="拥堵持续时长（分钟）"
          :min="30"
          :max="240"
          :step="30"
          size="small"
          controls-position="right"
          :disabled="disabled"
      />
      <label for="breakdown-level">故障等级</label>
      <ElSelect id="breakdown-level" v-model="breakdownLevel" size="small" :disabled="disabled" aria-label="故障等级">
        <ElOption label="轻微故障（无需救援）" value="MINOR" />
        <ElOption label="需救援故障" value="ASSISTANCE_REQUIRED" />
        <ElOption label="报废并换车续运" value="REPLACEMENT_REQUIRED" />
      </ElSelect>
      <template v-if="breakdownLevel === 'REPLACEMENT_REQUIRED'">
        <label for="replacement-wait">换车等待时长（分钟）</label>
        <ElInputNumber id="replacement-wait" v-model="replacementWaitMinutes" aria-label="换车等待时长（分钟）" :min="30" :max="180" :step="30" size="small" controls-position="right" :disabled="disabled" />
        <small class="duration-help">只提交换车等待时长；系统将由后端选择兼容的空闲车辆。</small>
      </template>
      <template v-else>
        <template v-if="breakdownLevel === 'ASSISTANCE_REQUIRED'">
          <label for="rescue-wait">救援等待时长（分钟）</label>
          <ElInputNumber id="rescue-wait" v-model="rescueWaitMinutes" aria-label="救援等待时长（分钟）" :min="30" :max="180" :step="30" size="small" controls-position="right" :disabled="disabled" />
        </template>
        <label for="repair-duration">维修时长（分钟）</label>
        <ElInputNumber id="repair-duration" v-model="repairMinutes" aria-label="维修时长（分钟）" :min="30" :max="180" :step="30" size="small" controls-position="right" :disabled="disabled" />
        <small class="duration-help">各阶段须为整数分钟且在 30–180 分钟内；总时长不超过 240 分钟。</small>
      </template>
      <div class="event-actions">
        <ElButton size="small" type="warning" :loading="submitting" :disabled="!vehicleId || disabled" @click="trigger('TRAFFIC_CONGESTION')">
          触发拥堵
        </ElButton>
        <ElButton size="small" type="danger" :loading="submitting" :disabled="!vehicleId || disabled" @click="trigger('VEHICLE_BREAKDOWN')">
          触发故障
        </ElButton>
      </div>
    </div>

    <div v-if="activeEvents.length" class="active-event-list">
      <div v-for="event in activeEvents" :key="event.eventId" class="active-event-item">
        <strong>{{ event.eventTypeText }}</strong>
        <span>{{ event.licensePlate }}</span>
        <small v-if="event.eventType === 'VEHICLE_BREAKDOWN'">{{ describeBreakdown(event) }}</small>
        <small>已持续 {{ formatDelay(event.delaySeconds) }} · 结束 {{ formatTime(event.plannedEndTime) }}</small>
      </div>
    </div>
    <div v-else class="event-empty">当前无活跃事件</div>
    <div v-if="recentEvents.length" class="recent-event-list" aria-label="最近故障结果">
      <strong>最近故障结果</strong>
      <div v-for="event in recentEvents" :key="event.eventId" class="recent-event-item">
        <span>{{ event.licensePlate || `车辆${event.vehicleId}` }}</span>
        <small>{{ event.description }}</small>
        <small>{{ formatTime(event.resolvedTime || event.plannedEndTime) }}</small>
      </div>
    </div>
  </ElCard>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElButton, ElCard, ElInputNumber, ElMessage, ElOption, ElSelect, ElTag } from 'element-plus'
import { randomEventApi } from '../api/randomEventApi'
import { activeEventSignature, createEventHistoryTransitionHandler, describeBreakdown, recentBreakdownRows, validateRandomEventInput } from '../utils/breakdownPresentation'
import { vehicleStatusPresentation } from '../utils/vehicleStatusPresentation'

const props = defineProps({
  vehicles: { type: Array, default: () => [] },
  activeEvents: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['triggered'])

const vehicleId = ref(null)
const durationMinutes = ref(60)
const breakdownLevel = ref('MINOR')
const rescueWaitMinutes = ref(30)
const repairMinutes = ref(60)
const replacementWaitMinutes = ref(60)
const submitting = ref(false)
const recentEvents = ref([])

const refreshHistory = async () => {
  recentEvents.value = recentBreakdownRows(await randomEventApi.getHistory(20)).slice(0, 5)
}
onMounted(() => refreshHistory().catch(() => {}))
const handleActiveEventTransition = createEventHistoryTransitionHandler(() => refreshHistory().catch(() => {}))
watch(() => activeEventSignature(props.activeEvents), handleActiveEventTransition)

watch(breakdownLevel, level => {
  if (level === 'REPLACEMENT_REQUIRED') {
    replacementWaitMinutes.value = 60
    return
  }
  rescueWaitMinutes.value = level === 'MINOR' ? 0 : 30
  repairMinutes.value = level === 'MINOR' ? 60 : 90
})

watch(() => props.vehicles, (vehicles) => {
  if (vehicleId.value && !vehicles.some(vehicle => vehicle.vehicleId === vehicleId.value)) {
    vehicleId.value = null
  }
}, { deep: true })

const trigger = async (eventType) => {
  if (!vehicleId.value || submitting.value) return
  const options = eventType === 'VEHICLE_BREAKDOWN'
      ? breakdownLevel.value === 'REPLACEMENT_REQUIRED'
        ? { breakdownLevel: breakdownLevel.value, replacementWaitMinutes: replacementWaitMinutes.value }
        : { breakdownLevel: breakdownLevel.value, rescueWaitMinutes: breakdownLevel.value === 'MINOR' ? 0 : rescueWaitMinutes.value, repairMinutes: repairMinutes.value }
      : { durationMinutes: durationMinutes.value }
  const validationError = validateRandomEventInput(eventType, options)
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  submitting.value = true
  try {
    await randomEventApi.trigger(eventType, vehicleId.value, options)
    ElMessage.success(eventType === 'TRAFFIC_CONGESTION' ? '交通拥堵已触发' : '车辆故障已触发')
    emit('triggered')
    await refreshHistory().catch(() => {})
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '随机事件触发失败')
  } finally {
    submitting.value = false
  }
}

const statusText = status => vehicleStatusPresentation(status).text

const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const formatDelay = (seconds) => {
  const totalMinutes = Math.max(0, Math.floor(Number(seconds || 0) / 60))
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return hours > 0 ? `${hours}小时${minutes}分` : `${minutes}分`
}
</script>

<style scoped>
.event-card-header,
.event-actions,
.active-event-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.event-form {
  display: grid;
  gap: 10px;
}

.event-actions .el-button {
  flex: 1;
  margin: 0;
}

.active-event-list {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}
.recent-event-list { margin-top: 12px; display: grid; gap: 6px; padding-top: 10px; border-top: 1px solid #ebeef5; font-size: 12px; }
.recent-event-item { display: grid; gap: 2px; padding: 7px; border-radius: 6px; background: #f5f7fa; }
.recent-event-item small { color: #606266; }

.active-event-item {
  padding: 8px;
  border-radius: 6px;
  background: #fff4e6;
  font-size: 12px;
  flex-wrap: wrap;
}

.active-event-item small {
  color: #909399;
}

.event-empty {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
  text-align: center;
}
.duration-help { color: #606266; line-height: 1.5; }
</style>
