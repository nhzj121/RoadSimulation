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
      <ElSelect v-model="vehicleId" placeholder="选择运输车辆" size="small" :disabled="disabled">
        <ElOption
            v-for="vehicle in vehicles"
            :key="vehicle.vehicleId"
            :label="`${vehicle.licensePlate || `车辆${vehicle.vehicleId}`} · ${statusText(vehicle.status)}`"
            :value="vehicle.vehicleId"
        />
      </ElSelect>
      <ElInputNumber
          v-model="durationMinutes"
          :min="30"
          :max="240"
          :step="30"
          size="small"
          controls-position="right"
          :disabled="disabled"
      />
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
        <small>已持续 {{ formatDelay(event.delaySeconds) }} · 结束 {{ formatTime(event.plannedEndTime) }}</small>
      </div>
    </div>
    <div v-else class="event-empty">当前无活跃事件</div>
  </ElCard>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElButton, ElCard, ElInputNumber, ElMessage, ElOption, ElSelect, ElTag } from 'element-plus'
import { randomEventApi } from '../api/randomEventApi'

const props = defineProps({
  vehicles: { type: Array, default: () => [] },
  activeEvents: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['triggered'])

const vehicleId = ref(null)
const durationMinutes = ref(60)
const submitting = ref(false)

watch(() => props.vehicles, (vehicles) => {
  if (vehicleId.value && !vehicles.some(vehicle => vehicle.vehicleId === vehicleId.value)) {
    vehicleId.value = null
  }
}, { deep: true })

const trigger = async (eventType) => {
  if (!vehicleId.value || submitting.value) return
  submitting.value = true
  try {
    await randomEventApi.trigger(eventType, vehicleId.value, durationMinutes.value)
    ElMessage.success(eventType === 'TRAFFIC_CONGESTION' ? '交通拥堵已触发' : '车辆故障已触发')
    emit('triggered')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '随机事件触发失败')
  } finally {
    submitting.value = false
  }
}

const statusText = (status) => ({
  ORDER_DRIVING: '前往装货点',
  TRANSPORT_DRIVING: '运输中',
  BREAKDOWN: '故障'
}[status] || status || '未知')

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
</style>
