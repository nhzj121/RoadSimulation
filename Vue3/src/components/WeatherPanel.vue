<template>
  <ElCard shadow="never" class="box-card weather-panel">
    <template #header>天气与实验环境</template>
    <div class="weather-form">
      <ElSelect v-model="selected" :disabled="locked || disabled" aria-label="天气场景">
        <ElOption label="晴天基线 · 自动事件关闭" value="BASELINE" />
        <ElOption label="天气演示 · 自动事件关闭" value="DEMO" />
        <ElOption label="固定种子自动 · 自动事件开启" value="AUTO" />
        <ElOption v-for="scene in scenarios" :key="scene.id" :label="`已保存：${scene.name}`" :value="`saved:${scene.id}`" />
      </ElSelect>
      <template v-if="selected === 'AUTO'">
        <label>随机种子</label>
        <ElInputNumber v-model="seed" :min="0" :max="2147483647" :disabled="locked || disabled" />
      </template>
      <small>{{ presetDescription }}</small>
      <small>默认：晴 ×1 · 雨 ×0.8 · 雪 ×0.5 · 雾 ×0.6（演示初值，导入可调整）</small>
      <small>{{ eventDescription }}</small>
      <div class="weather-actions">
        <ElButton size="small" :disabled="locked || disabled" @click="importInput?.click()">导入场景</ElButton>
        <ElButton size="small" :disabled="busy || disabled" @click="exportScene">导出场景</ElButton>
        <ElButton v-if="current?.runId" size="small" @click="exportRun">运行记录</ElButton>
      </div>
      <input ref="importInput" type="file" accept="application/json,.json" hidden @change="importScene" />
      <div v-if="current?.runId" class="weather-current" aria-live="polite">
        <strong>{{ weatherNames[current.weatherType] || current.weatherType }} · 速度 ×{{ current.speedFactor }}</strong>
        <span>单车自动事件：{{ current.autoEvents ? '开启' : '关闭' }}</span>
        <span>下次变化：{{ formatTime(current.nextChangeTime) }}</span>
        <span>场景 {{ current.scenarioId }} · 运行 {{ current.runId }}</span>
        <ElTag v-if="current.manuallyIntervened" type="warning">本次含人工干预</ElTag>
        <small>运行配置已锁定；重置后可以选择新场景。</small>
      </div>
      <small v-else>启动前可选择。天气时间表结束后恢复晴天。</small>
    </div>
  </ElCard>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElCard, ElInputNumber, ElMessage, ElOption, ElSelect, ElTag } from 'element-plus'
import { weatherApi } from '../api/weatherApi'
import { describeBreakdownPolicy } from '../utils/breakdownPresentation'

const props = defineProps({ weather: { type: Object, default: null }, disabled: Boolean })
const selected = ref('DEMO')
const seed = ref(20260911)
const current = ref(null)
const scenarios = ref([])
const busy = ref(false)
const importInput = ref(null)
const locked = computed(() => Boolean(current.value?.locked))
const weatherNames = { CLEAR: '晴', SUNNY: '晴', RAIN: '雨', SNOW: '雪', FOG: '雾' }
const selectedScene = computed(() => scenarios.value.find(scene => `saved:${scene.id}` === selected.value))
const presetDescription = computed(() => selectedScene.value
    ? selectedScene.value.timeSlices?.map(slice => `${slice.startMinute}–${slice.endMinute}分 ${weatherNames[slice.weatherType]} ×${slice.speedFactor}`).join(' → ') || '回放保存的天气时间表。'
    : ({
  BASELINE: '全程晴天，作为无扰动基线。',
  DEMO: '晴 60 分钟 → 雨 120 分钟 → 雾 60 分钟 → 晴。',
  AUTO: '预生成 24 小时天气，每 120 分钟抽取：晴 50%、雨 25%、雪 10%、雾 15%。'
}[selected.value] || '回放保存的完整天气时间表，不重新抽取。'))
const eventDescription = computed(() => {
  const scene = selectedScene.value
  const congestion = scene?.congestion || { hourlyProbability: .08, minDurationMinutes: 30, maxDurationMinutes: 90 }
  const breakdown = scene?.breakdown || { hourlyProbability: .02, minDurationMinutes: 60, maxDurationMinutes: 120 }
  const breakdownPolicy = scene ? scene.breakdownPolicy : {
    version: 'breakdown-v2', minorProbability: .7, minorRepairMin: 30, minorRepairMax: 60,
    rescueWaitMin: 30, rescueWaitMax: 60, assistanceRepairMin: 60, assistanceRepairMax: 120
  }
  const auto = scene ? scene.autoEvents : selected.value === 'AUTO'
  return `自动事件${auto ? '开启' : '关闭'}；拥堵 ${Math.round(congestion.hourlyProbability * 100)}%/小时、${congestion.minDurationMinutes}–${congestion.maxDurationMinutes} 分钟；故障 ${Math.round(breakdown.hourlyProbability * 100)}%/小时，${describeBreakdownPolicy(breakdownPolicy, breakdown)}。`
})
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '无（之后晴天）'
watch(() => props.weather, value => {
  current.value = value
  if (value?.locked && value.scenarioId) selected.value = `saved:${value.scenarioId}`
}, { deep: true })

async function refresh() {
  const [state, list] = await Promise.all([weatherApi.current(), weatherApi.list()])
  current.value = state
  scenarios.value = list || []
  if (state?.locked && state.scenarioId) selected.value = `saved:${state.scenarioId}`
}
onMounted(() => refresh().catch(error => ElMessage.warning(`天气配置读取失败：${error.message}`)))

async function selectedSceneId() {
  if (selected.value.startsWith('saved:')) return selected.value.slice(6)
  const scene = await weatherApi.create({
    name: { BASELINE: '晴天基线', DEMO: '天气演示', AUTO: '固定种子自动场景' }[selected.value],
    preset: selected.value,
    seed: seed.value
  })
  await refresh()
  selected.value = `saved:${scene.id}`
  return scene.id
}
async function prepareStart() {
  if (busy.value) throw new Error('天气场景正在准备，请稍候')
  busy.value = true
  try {
    current.value = await weatherApi.current()
    if (current.value?.locked) return current.value.scenarioId ? { scenarioId: current.value.scenarioId } : {}
    return { scenarioId: await selectedSceneId() }
  } finally { busy.value = false }
}
function download(value, name) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
async function exportScene() {
  busy.value = true
  try {
    const id = current.value?.scenarioId || await selectedSceneId()
    download(await weatherApi.export(id), `weather-scene-${id}.json`)
  } catch (error) { ElMessage.error(error.message) }
  finally { busy.value = false }
}
async function exportRun() {
  try { download(await weatherApi.exportRun(current.value.runId), `weather-run-${current.value.runId}.json`) }
  catch (error) { ElMessage.error(error.message) }
}
async function importScene(event) {
  const file = event.target.files?.[0]
  if (!file) return
  try {
    const scene = await weatherApi.import(JSON.parse(await file.text()))
    await refresh()
    selected.value = `saved:${scene.id}`
    ElMessage.success('场景已导入，可在启动时回放')
  } catch (error) { ElMessage.error(`导入失败：${error.message}`) }
  finally { event.target.value = '' }
}
defineExpose({ prepareStart, refresh })
</script>

<style scoped>
.weather-form, .weather-current { display: grid; gap: 9px; }
.weather-form small { color: #606266; line-height: 1.6; }
.weather-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.weather-actions .el-button { margin: 0; }
.weather-current { background: #eef5ff; padding: 10px; border-radius: 6px; font-size: 12px; overflow-wrap: anywhere; }
</style>
