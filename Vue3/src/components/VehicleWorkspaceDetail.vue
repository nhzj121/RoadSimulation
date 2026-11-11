<template>
  <div class="vehicle-floating-status-row">
    <span class="vehicle-floating-status-dot" :style="{ backgroundColor: status.color }"></span>
    <span>{{ status.text }}</span>
    <span v-if="loading" class="vehicle-floating-loading">Loading...</span>
  </div>
  <div v-if="action" class="vehicle-floating-line"><strong>Action:</strong> {{ action }}</div>
  <section class="vehicle-floating-section">
    <div class="vehicle-floating-section-title">Task</div>
    <div class="vehicle-floating-grid">
      <template v-for="field in task" :key="field.label">
        <span>{{ field.label }}</span><strong>{{ field.value ?? '-' }}</strong>
      </template>
    </div>
  </section>
  <slot />
  <section class="vehicle-floating-section">
    <div class="vehicle-floating-section-title">Load</div>
    <template v-for="meter in load" :key="meter.unit">
      <div class="vehicle-floating-metric-row">
        <span>{{ meter.current }} / {{ meter.limit }} {{ meter.unit }}</span>
        <strong>{{ meter.percent === null ? '—' : meter.percent.toFixed(1) + '%' }}</strong>
      </div>
      <div v-if="meter.percent !== null" class="vehicle-floating-progress">
        <div class="vehicle-floating-progress-fill" :class="meter.unit === 't' ? 'load' : 'volume'" :style="{ width: `${meter.percent}%` }"></div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  status: { text: string; color: string }
  loading?: boolean
  action?: string
  task: Array<{ label: string; value?: string | number | null }>
  load: Array<{ current: string; limit: string; unit: 't' | 'm³'; percent: number | null }>
}>()
</script>

<style scoped>
.vehicle-floating-status-row { display:flex;align-items:center;gap:8px;margin-bottom:10px;font-weight:600 }
.vehicle-floating-status-dot { width:9px;height:9px;border-radius:50%;flex-shrink:0 }
.vehicle-floating-loading { margin-left:auto;color:#909399;font-weight:400 }
.vehicle-floating-line { margin:6px 0;color:#606266 }
.vehicle-floating-section { margin-top:12px;padding-top:10px;border-top:1px solid #ebeef5 }
.vehicle-floating-section-title { margin-bottom:8px;font-size:13px;font-weight:600;color:#303133 }
.vehicle-floating-grid { display:grid;grid-template-columns:82px minmax(0,1fr);gap:6px 8px;align-items:start }
.vehicle-floating-grid span { color:#909399 }
.vehicle-floating-grid strong,.vehicle-floating-metric-row strong { color:#303133;font-weight:600;min-width:0;overflow-wrap:anywhere }
.vehicle-floating-metric-row { display:flex;justify-content:space-between;gap:8px;margin:8px 0 4px;color:#606266 }
.vehicle-floating-progress { height:6px;background:#ebeef5;border-radius:3px;overflow:hidden }
.vehicle-floating-progress-fill { height:100%;border-radius:3px }
.vehicle-floating-progress-fill.load { background:#67c23a }
.vehicle-floating-progress-fill.volume { background:#409eff }
</style>
