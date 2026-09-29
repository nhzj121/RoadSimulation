<template>
  <ElCard shadow="never" class="box-card weather-panel">
    <template #header>天气与运行环境</template>
    <div class="weather-form">
      <ElSelect v-model="selected" :disabled="locked || disabled" aria-label="天气场景">
        <ElOption label="晴天基线" value="BASELINE" />
        <ElOption label="天气演示" value="DEMO" />
        <ElOption label="固定种子自动场景" value="AUTO" />
        <ElOption v-for="scene in scenarios" :key="scene.id" :label="`已保存：${scene.name}`" :value="`saved:${scene.id}`" />
      </ElSelect>
      <template v-if="selected === 'AUTO'">
        <div class="seed-row">
          <span>随机种子</span>
          <ElInputNumber v-model="seed" :min="0" :max="2147483647" :disabled="locked || disabled" controls-position="right" />
        </div>
      </template>

      <div class="seed-row">
        <span>天气覆盖时长（小时）</span>
        <ElInputNumber v-model="horizonHours" :min="1" :max="8760" :precision="0" :disabled="locked || disabled" />
      </div>
      <div class="seed-row">
        <span>自动故障与拥堵（独立于天气）</span>
        <ElSwitch v-model="autoEvents" :disabled="locked || disabled" />
      </div>
      <div class="environment-summary" aria-live="polite">
        <template v-if="current?.runId">
          <div>
            <span>当前天气</span>
            <strong>{{ weatherNames[current.weatherType] || current.weatherType }} · ×{{ current.speedFactor }}</strong>
          </div>
          <div>
            <span>自动事件</span>
            <strong>{{ current.autoEvents ? '开启' : '关闭' }}</strong>
          </div>
          <div>
            <span>下次变化</span>
            <strong>{{ formatTime(current.nextChangeTime) }}</strong>
          </div>
        </template>
        <template v-else>
          <div>
            <span>已选场景</span>
            <strong>{{ selectedSceneName }}</strong>
          </div>
          <div>
            <span>自动事件</span>
            <strong>{{ selectedAutoEvents ? '开启' : '关闭' }}</strong>
          </div>
          <div>
            <span>运行状态</span>
            <strong>等待启动</strong>
          </div>
        </template>
      </div>

      <div class="weather-actions">
        <ElButton size="small" :disabled="locked || disabled" @click="importInput?.click()">导入场景</ElButton>
        <ElButton size="small" :disabled="busy || disabled" @click="exportScene">导出场景</ElButton>
        <ElButton v-if="current?.runId" size="small" @click="exportRun">运行记录</ElButton>
      </div>
      <input ref="importInput" type="file" accept="application/json,.json" hidden @change="importScene" />
      <ElTag v-if="current?.manuallyIntervened" class="manual-tag" type="warning">本次含人工干预</ElTag>
      <ElCollapse v-model="expandedDetails" class="environment-details">
        <ElCollapseItem title="场景规则与运行详情" name="details">
          <p>{{ presetDescription }}</p>
          <p>{{ eventDescription }}</p>
          <p>天气系数：晴 ×1 · 雨 ×0.8 · 雪 ×0.5 · 雾 ×0.6。</p>
          <p v-if="current?.runId">运行配置已锁定；重置后可选择新场景。</p>
          <p v-else>天气时间表结束后恢复晴天。</p>
        </ElCollapseItem>
      </ElCollapse>
    </div>
  </ElCard>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElCard, ElCollapse, ElCollapseItem, ElInputNumber, ElMessage, ElOption, ElSelect, ElSwitch, ElTag } from 'element-plus'
import { weatherApi } from '../api/weatherApi'

const props = defineProps({ weather: { type: Object, default: null }, disabled: Boolean })
const selected = ref('DEMO')
const seed = ref(20260911)
const horizonHours = ref(24)
const autoEvents = ref(false)
const current = ref(null)
const scenarios = ref([])
const busy = ref(false)
const importInput = ref(null)
const expandedDetails = ref([])
const locked = computed(() => Boolean(current.value?.locked))
const weatherNames = { CLEAR: '晴', SUNNY: '晴', RAIN: '雨', SNOW: '雪', FOG: '雾' }
const selectedScene = computed(() => scenarios.value.find(scene => `saved:${scene.id}` === selected.value))
const selectedSceneName = computed(() => selectedScene.value?.name || ({ BASELINE: '晴天基线', DEMO: '天气演示', AUTO: '固定种子自动场景' }[selected.value] || '已保存场景'))
const selectedAutoEvents = computed(() => autoEvents.value)
const presetDescription = computed(() => selectedScene.value
    ? selectedScene.value.timeSlices?.map(slice => `${slice.startMinute}–${slice.endMinute}分 ${weatherNames[slice.weatherType]} ×${slice.speedFactor}`).join(' → ') || '回放保存的天气时间表。'
    : ({
  BASELINE: '全程晴天，作为无扰动基线。',
  DEMO: '晴 60 分钟 → 雨 120 分钟 → 雾 60 分钟 → 晴。',
  AUTO: '按所选时长预生成天气；每 120 分钟抽取：晴 50%、雨 25%、雪 10%、雾 15%。'
}[selected.value] || '回放保存的完整天气时间表，不重新抽取。'))
const eventDescription = computed(() => {
  return `自动事件${(current.value?.locked ? current.value.autoEvents : autoEvents.value) ? '开启' : '关闭'}；事件参数由启动配置独立确定，不读取天气场景的旧事件字段。`
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
    seed: seed.value,
    generationHorizonMinutes: horizonHours.value * 60
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
    return { scenarioId: await selectedSceneId(), eventOptions: { autoEnabled: autoEvents.value } }
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
.weather-form { display: grid; gap: 10px; }
.seed-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #606266; font-size: 13px; }
.seed-row .el-input-number { width: 180px; }
.weather-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.weather-actions .el-button { margin: 0; }
.environment-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 10px; border-radius: 8px; background: #f3f7fd; }
.environment-summary > div { display: grid; gap: 3px; min-width: 0; }
.environment-summary span { color: #778397; font-size: 12px; }
.environment-summary strong { color: #303133; font-size: 13px; overflow-wrap: anywhere; }
.manual-tag { justify-self: start; }
.environment-details :deep(.el-collapse-item__header) { height: 34px; color: #606266; font-size: 13px; }
.environment-details :deep(.el-collapse-item__content) { padding-bottom: 4px; color: #606266; font-size: 12px; line-height: 1.65; }
.environment-details p { margin: 0 0 7px; }
@media (max-width: 480px) { .environment-summary { grid-template-columns: 1fr; } }
</style>
