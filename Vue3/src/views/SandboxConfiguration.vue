<template>
  <main v-number-accessibility class="sandbox-page">
    <header>
      <div><h1>数据沙箱 · 配置与发布</h1><p>独立实验配置；不修改普通仿真数据。发布后可显式准备并运行。</p><router-link to="/sandbox/tasks">查看任务与进度</router-link></div>
    </header>
    <el-alert title="发布只生成不可变配置版本，不代表已开始运行。路径事实尚未冻结。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice" />
    <section class="connection">
      <el-tag :type="connected ? 'success' : 'warning'">{{ connected ? '沙箱管理可用' : '沙箱管理不可用' }}</el-tag>
      <span v-if="baselineId">基线：{{ baselineId }}</span>
      <span v-if="workspace">工作库：{{ workspace.workspace_state }} · {{ workspace.control_schema_version }}</span>
      <el-button :loading="busy" @click="connect">重新检查连接</el-button>
    </section>
    <p v-if="!connected">请在后端显式启用sandbox.management.enabled，并配置独立沙箱账号。此页面不接收数据库地址、用户名或密码。</p>

    <el-tabs v-model="tab">
      <el-tab-pane label="1. 场景选择与覆盖" name="scenario">
        <div class="toolbar">
          <el-input v-model="scenarioLoadKey" placeholder="场景Key" :disabled="busy" />
          <el-button :disabled="!connected || busy" @click="loadScenarioDraft">加载草稿</el-button>
          <el-input-number v-model="scenarioLoadRevision" :min="1" :disabled="busy" />
          <el-button :disabled="!connected || busy" @click="loadScenarioRevision">选用已发布版本</el-button>
          <el-button :disabled="!connected || busy" @click="listFamilies('scenarios')">查看场景列表</el-button>
        </div>
        <el-form v-if="scenario" :disabled="busy || !connected" label-width="155px">
          <el-form-item label="场景Key"><el-input v-model="scenario.scenarioKey" placeholder="小写字母、数字、短横线" /></el-form-item>
          <el-form-item label="显示名称"><el-input v-model="scenario.displayName" /></el-form-item>
          <el-form-item label="说明"><el-input v-model="scenario.description" type="textarea" /></el-form-item>
          <el-collapse>
            <el-collapse-item title="基线只读指纹" name="baseline"><pre>{{ scenario.baseline }}</pre></el-collapse-item>
          </el-collapse>
          <h3>车辆属性筛选（空值表示不限）</h3>
            <div class="filter-grid">
              <el-form-item label="载重下限（吨）"><el-input-number v-model="scenario.selection.vehicles.minimumLoadCapacityTonnes" :min="0" /></el-form-item>
              <el-form-item label="载重上限（吨）"><el-input-number v-model="scenario.selection.vehicles.maximumLoadCapacityTonnes" :min="0" /></el-form-item>
              <el-form-item label="容积下限（m³）"><el-input-number v-model="scenario.selection.vehicles.minimumCargoVolumeCubicMeters" :min="0" /></el-form-item>
              <el-form-item label="容积上限（m³）"><el-input-number v-model="scenario.selection.vehicles.maximumCargoVolumeCubicMeters" :min="0" /></el-form-item>
            </div>
            <el-form-item label="车型"><el-select v-model="scenario.selection.vehicles.vehicleTypes" multiple filterable allow-create default-first-option><el-option v-for="v in vehicleTypes" :key="v" :value="v" :label="v" /></el-select></el-form-item>
            <el-form-item label="车身型号"><el-select v-model="scenario.selection.vehicles.modelTypes" multiple filterable allow-create default-first-option><el-option v-for="v in modelTypes" :key="v" :value="v" :label="v" /></el-select></el-form-item>
          <SandboxSelectionEditor :key="selectionEpoch + '-vehicles'" title="车辆" type="vehicles" :selection="scenario.selection.vehicles" :disabled="busy || !connected" @invalid="selectionErrors.vehicles = $event" />
            <el-form-item label="POI类型"><el-select v-model="scenario.selection.pois.poiTypes" multiple filterable allow-create default-first-option><el-option v-for="v in poiTypes" :key="v" :value="v" :label="v" /></el-select></el-form-item>
          <SandboxSelectionEditor :key="selectionEpoch + '-pois'" title="POI" type="pois" :selection="scenario.selection.pois" :disabled="busy || !connected" @invalid="selectionErrors.pois = $event" />
            <el-form-item label="货物类别"><el-select v-model="scenario.selection.goods.categories" multiple filterable allow-create default-first-option><el-option v-for="v in categories" :key="v" :value="v" :label="v" /></el-select></el-form-item>
          <SandboxSelectionEditor :key="selectionEpoch + '-goods'" title="货物" type="goods" :selection="scenario.selection.goods" :disabled="busy || !connected" @invalid="selectionErrors.goods = $event" />
          <SandboxSelectionEditor :key="selectionEpoch + '-chains'" title="完整加工链" type="processing-chains" :selection="scenario.selection.processingChains" :disabled="busy || !connected" @invalid="selectionErrors.chains = $event" />
          <p>不自动补回加工链引用的货物和POI。通用场景可以保存为空加工链，但PRODUCTION运行必须包含至少一条有效ACTIVE链。</p>
          <el-collapse>
            <el-collapse-item title="加工链基线参考（输入ID、输出比例与模板POI）" name="chains">
              <div v-for="chain in chains" :key="chain.id"><h4>{{ chain.id }} · {{ chain.chainName }} · {{ chain.status }}</h4>
                <el-table :data="chain.stages" size="small">
                  <el-table-column prop="id" label="加工段ID" /><el-table-column prop="stageName" label="名称" />
                  <el-table-column prop="processingPoiId" label="模板POI" /><el-table-column prop="outputWeightRatio" label="基线输出比例" />
                  <el-table-column label="输入ID / 货物 / 占比"><template #default="{row}">{{ formatInputs(row.inputs) }}</template></el-table-column>
                </el-table>
              </div>
            </el-collapse-item>
          </el-collapse>
          <h3>覆盖项（留空表示沿用基线）</h3>
          <p>输出比例(0,1]、最多6位小数；同段输入占比合计为1。数量按正整数件。固定POI只允许已选仓库或配送中心。</p>
          <h4>加工段输出比例</h4>
          <div v-for="(row, index) in scenario.overrides.stageOutputRatios" :key="index" class="override-row">
            <el-input-number v-model="row.stageId" :min="1" aria-label="加工段ID" />
            <el-input-number v-model="row.outputWeightRatio" :min="0.000001" :max="1" :step="0.01" aria-label="输出比例" />
            <el-button @click="removeOverride('stageOutputRatios', index)">删除</el-button>
          </div>
          <el-button @click="addOverride('stageOutputRatios')">添加输出比例覆盖</el-button>
          <h4>加工输入占比</h4>
          <div v-for="(row, index) in scenario.overrides.processingInputShares" :key="index" class="override-row">
            <el-input-number v-model="row.inputId" :min="1" aria-label="加工输入ID" />
            <el-input-number v-model="row.inputShare" :min="0.000001" :step="0.01" aria-label="输入占比" />
            <el-button @click="removeOverride('processingInputShares', index)">删除</el-button>
          </div>
          <el-button @click="addOverride('processingInputShares')">添加输入占比覆盖</el-button>
          <h4>初始库存（POI / 货物 / 件数）</h4>
          <div v-for="(row, index) in scenario.overrides.initialInventories" :key="index" class="override-row">
            <el-input-number v-model="row.poiId" :min="1" aria-label="库存POI ID" /><el-input-number v-model="row.goodsId" :min="1" aria-label="库存货物ID" />
            <el-input-number v-model="row.quantity" :min="1" aria-label="库存件数" /><el-button @click="removeOverride('initialInventories', index)">删除</el-button>
          </div>
          <el-button @click="addOverride('initialInventories')">添加初始库存</el-button>
          <h4>固定车辆初始POI（车辆 / POI）</h4>
          <div v-for="(row, index) in scenario.overrides.vehicleInitialPois" :key="index" class="override-row">
            <el-input-number v-model="row.vehicleId" :min="1" aria-label="固定车辆ID" /><el-input-number v-model="row.poiId" :min="1" aria-label="固定POI ID" />
            <span>FIXED_POI</span><el-button @click="removeOverride('vehicleInitialPois', index)">删除</el-button>
          </div>
          <el-button @click="addOverride('vehicleInitialPois')">添加固定POI</el-button>
        </el-form>
        <div v-if="scenario" class="actions">
          <el-button :disabled="!canEdit" @click="previewScenario">编译预览</el-button>
          <el-button :disabled="!canEdit || scenarioWriteLocked || !scenarioPreviewCurrent" @click="saveScenario">保存草稿</el-button>
          <el-button type="primary" :disabled="!canEdit || scenarioWriteLocked || !scenarioPublishable" @click="publishScenario">发布场景版本</el-button>
          <el-button :disabled="busy || !connected" @click="resetScenario">重置到基线配置</el-button>
          <el-button :disabled="busy || !scenarioSavedDefinition" @click="restoreScenarioLocal">放弃本地修改</el-button>
          <el-button :disabled="busy" @click="exportScenario">导出JSON</el-button>
          <el-button :disabled="busy || !connected" @click="openImport('scenario')">导入JSON</el-button>
        </div>
        <p v-if="scenario">{{ scenarioSavedCurrent ? `已保存草稿，行版本${scenarioVersion}` : scenarioUsesPublished ? '已选用发布版本，未改动其内容' : '有未保存的本地修改或尚未保存' }}。重复发布相同数据定义复用Revision，名称/说明变化不生成数据版本。编辑已有Key请先加载其草稿；选用历史Revision不会获取可覆盖草稿的权限。</p>
        <p v-if="scenarioWriteLocked">场景写操作已锁定，请先导出本地修改并重新加载服务端草稿。</p>
        <section v-if="scenarioPreviewCurrent && scenarioPreview" class="preview">
          <h3>编译通过 · 最终数据集合</h3><p>各表数量：{{ scenarioPreview.rowCounts }} · 固定车辆 {{ scenarioPreview.fixedVehicleCount }}</p>
          <p class="fingerprint">场景指纹：{{ scenarioPreview.scenarioDefinitionSha256 }}</p><p class="fingerprint">有效数据：{{ scenarioPreview.effectiveScenarioDataSha256 }}</p>
          <el-collapse><el-collapse-item title="最终ID集合" name="ids"><pre>{{ scenarioPreview.resolvedSelection }}</pre></el-collapse-item></el-collapse>
        </section>
        <section v-if="publishedScenario" class="preview">
          <h3>已选发布版本：{{ publishedScenario.scenarioKey }} / Revision {{ publishedScenario.revision }}</h3>
          <el-button type="primary" :disabled="busy || !connected" @click="createRunTemplate">以此版本配置运行规格</el-button>
        </section>
      </el-tab-pane>

      <el-tab-pane label="2. 运行规格与发布" name="run">
        <div class="toolbar">
          <el-input v-model="runLoadKey" placeholder="运行规格Key" :disabled="busy" /><el-button :disabled="!connected || busy" @click="loadRunDraft">加载v2草稿</el-button>
          <el-input-number v-model="runLoadRevision" :min="1" :disabled="busy" /><el-button :disabled="!connected || busy" @click="loadRunRevision">读取v2发布版本</el-button>
          <el-button :disabled="!connected || busy" @click="listFamilies('runs')">查看规格列表</el-button>
        </div>
        <p v-if="!run">先发布或选用场景版本，再生成运行模板；也可以加载已存在的v2草稿。</p>
        <el-alert v-if="runBindingStale" title="场景配置已改变。已保留旧运行草稿，但其场景绑定已失效；请发布/选用场景版本后显式重新绑定，或重新加载已保存规格。" type="warning" :closable="false" />
        <el-button v-if="run && publishedScenario" :disabled="busy || !connected" @click="bindPublishedScenario">保留运行参数，绑定当前已选场景版本</el-button>
        <el-form v-if="run" :disabled="busy || !connected" label-width="170px">
          <el-form-item label="运行规格Key"><el-input v-model="run.runSpecKey" /></el-form-item>
          <el-form-item label="显示名称"><el-input v-model="run.displayName" /></el-form-item>
          <el-form-item label="说明"><el-input v-model="run.description" type="textarea" /></el-form-item>
          <el-form-item label="已绑定场景版本"><span>{{ run.scenario.scenarioKey }} / Revision {{ run.scenario.revision }}</span></el-form-item>
          <el-form-item label="顶层种子（整数文本）"><el-input v-model="run.random.rootSeed" placeholder="例如20260927；不要使用浮点数" /></el-form-item>
          <el-form-item label="固定仿真轮次"><el-input-number v-model="run.simulationClock.totalLoops" :min="1" :max="17520" /></el-form-item>
          <el-form-item label="调度策略"><el-select v-model="run.dispatch.strategy" @change="changeStrategy"><el-option label="原有VRP（ORIGINAL）" value="ORIGINAL" /><el-option label="现有启发式（HEURISTIC）" value="HEURISTIC" /></el-select></el-form-item>
          <el-form-item label="司机随机行为"><el-switch v-model="run.driverBehavior.enabled" /></el-form-item>
          <h3>天气配置</h3>
          <el-form-item label="天气来源"><el-select :model-value="run.weather.sourceMode" @change="changeWeather"><el-option value="SEEDED_GENERATION" label="顶层种子派生天气" /><el-option value="EXPLICIT_TIMELINE" label="明确指定时间线" /></el-select></el-form-item>
          <template v-if="run.weather.sourceMode === 'SEEDED_GENERATION'">
            <el-form-item label="天气间隔（分钟）"><el-input-number v-model="run.weather.intervalMinutes" :min="1" /></el-form-item>
            <div v-for="(weather, index) in weatherTypes" :key="weather" class="override-row">
              <span>{{ weather }}</span><label>权重 <el-input-number v-if="run.weather.weights" v-model="run.weather.weights[index]" :min="0" /></label>
              <label>速度系数 <el-input-number v-if="run.weather.speedFactors" v-model="run.weather.speedFactors[weather]" :min="0.000001" :max="1" :step="0.1" /></label>
            </div>
          </template>
          <template v-else>
            <p>从第0分钟起连续覆盖全时域（{{ run.simulationClock.totalLoops * 30 }}分钟）；改变轮次后需重新核对覆盖范围。</p>
            <div v-for="(slice, index) in run.weather.timeSlices" :key="index" class="override-row">
              <el-input-number v-model="slice.startMinute" :min="0" aria-label="开始分钟" /><el-input-number v-model="slice.endMinute" :min="1" aria-label="结束分钟" />
              <el-select v-model="slice.weatherType"><el-option v-for="w in weatherTypes" :key="w" :label="w" :value="w" /></el-select>
              <el-input-number v-model="slice.speedFactor" :min="0.000001" :max="1" :step="0.1" aria-label="天气速度系数" /><el-button @click="removeWeatherSlice(index)">删除</el-button>
            </div>
            <el-button @click="addWeatherSlice">添加时间段</el-button>
          </template>
          <h3>自动交通事件</h3>
          <el-form-item label="启用事件系统"><el-switch v-model="run.events.enabled" @change="eventEnabledChanged" /></el-form-item>
          <el-form-item label="启用自动事件"><el-switch v-model="run.events.autoEnabled" :disabled="!run.events.enabled" /></el-form-item>
          <p>自动事件使用受控随机规则；不开启时不生成自动事件。本页面不提供手动触发事件。</p>
          <div class="filter-grid" v-for="kind in eventTypes" :key="kind">
            <h4>{{ kind === 'congestion' ? '拥堵' : '故障' }}</h4>
            <el-form-item label="每小时发生概率"><el-input-number v-model="run.events[kind].hourlyProbability" :min="0" :max="1" :step="0.01" /></el-form-item>
            <el-form-item label="持续时间下限（分）"><el-input-number v-model="run.events[kind].minDurationMinutes" :min="1" /></el-form-item>
            <el-form-item label="持续时间上限（分）"><el-input-number v-model="run.events[kind].maxDurationMinutes" :min="1" /></el-form-item>
            <el-form-item label="速度系数"><el-input-number v-model="run.events[kind].speedFactor" :disabled="kind === 'breakdown'" :min="0" :max="1" :step="0.1" /></el-form-item>
          </div>
          <el-collapse>
            <el-collapse-item title="只读运行协议与策略参数" name="protocol">
              <p>起始时刻2026-01-01 00:00，每轮1800秒；PRODUCTION每6轮生成需求，调度每3轮。算法参数来自已登记配置，不开放GA/归一化内部参数。</p>
              <pre>{{ readonlyRunProtocol }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-form>
        <div v-if="run" class="actions">
          <el-button :disabled="!canRunEdit" @click="previewRun">编译预览</el-button><el-button :disabled="!canRunEdit || runWriteLocked || !runPreviewCurrent" @click="saveRun">保存草稿</el-button>
          <el-button type="primary" :disabled="!canRunEdit || runWriteLocked || !runPublishable" @click="publishRun">发布运行规格</el-button>
          <el-button :disabled="busy || !runSavedDefinition" @click="restoreRunLocal">放弃本地修改</el-button>
          <el-button :disabled="busy" @click="exportRun">导出JSON</el-button><el-button :disabled="!canEdit" @click="openImport('run')">导入JSON</el-button>
        </div>
        <p v-if="run">{{ runSavedCurrent ? `已保存草稿，行版本${runVersion}` : runUsesPublished ? '已选用发布版本，未改动其内容' : '有未保存的本地修改或尚未保存' }}。绑定发布场景，不读取当前工作库作为新配置的基础。</p>
        <p v-if="runWriteLocked">运行规格写操作已锁定，请先导出本地修改并重新加载服务端草稿。</p>
        <section v-if="runPreviewCurrent && runPreview" class="preview">
          <h3>编译通过</h3><p>初态车辆 {{ runPreview.vehicleInitialStates.length }}，候选初始POI {{ runPreview.eligibleVehicleInitialPoiCount }}，天气段 {{ runPreview.weatherTimeline.length }}</p>
          <p class="fingerprint">规格：{{ runPreview.runSpecificationSha256 }}</p><p class="fingerprint">车辆初态：{{ runPreview.resolvedVehicleInitialStateSha256 }}</p>
          <p class="fingerprint">天气：{{ runPreview.weatherTimelineSha256 }}</p><p class="fingerprint">事件：{{ runPreview.eventConfigurationSha256 }}</p>
          <el-collapse><el-collapse-item title="车辆初态和天气预览" name="resolved"><pre>{{ resolvedRunPreview }}</pre></el-collapse-item></el-collapse>
        </section>
        <section v-if="publishedRun" class="preview"><h3>已发布：{{ publishedRun.runSpecKey }} / Revision {{ publishedRun.revision }}</h3></section>
        <SandboxStartPanel :revision="publishedRun" :enabled="canStartPublished" />
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="importVisible" title="导入配置JSON（只修改本地草稿）" width="70%"><el-input v-model="importText" type="textarea" :rows="16" /><template #footer><el-button @click="importVisible = false">取消</el-button><el-button @click="applyImport">应用到本地</el-button></template></el-dialog>
    <el-dialog v-model="familiesVisible" title="已保存配置族" width="70%"><el-table :data="families"><el-table-column prop="scenario_key" label="场景Key" /><el-table-column prop="run_spec_key" label="规格Key" /><el-table-column prop="display_name" label="名称" /><el-table-column prop="row_version" label="草稿行版本" /><el-table-column label="操作"><template #default="{row}"><el-button @click="pickFamily(row)">加载草稿</el-button></template></el-table-column></el-table><el-button :disabled="familiesOffset === 0 || busy" @click="listFamilies(familyKind, familiesOffset - 50)">上一页</el-button><el-button :disabled="!familiesMore || busy" @click="listFamilies(familyKind, familiesOffset + 50)">下一页</el-button></el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import SandboxSelectionEditor from '../components/sandbox/SandboxSelectionEditor.vue'
import SandboxStartPanel from '../components/sandbox/SandboxStartPanel.vue'
import { sandboxApi, SandboxApiError } from '../api/sandbox'
import { cloneDefinition, draftIdentity, errorHints, setWeatherMode, validateSeed } from '../sandbox/authoring'
import type { SandboxScenarioDefinition, SandboxScenarioPreview, SandboxScenarioRevision, SandboxRunDefinition, SandboxRunPreview, SandboxRunRevision, SandboxCatalogItem } from '../types/sandbox'

// Installed ElementPlus sets number-input aria-disabled only on mount. Synchronize it
// locally after busy/connection changes; do not patch the dependency or ordinary pages.
function syncNumberAccessibility(root: HTMLElement) {
  root.querySelectorAll<HTMLInputElement>('input[role="spinbutton"]').forEach(input => input.setAttribute('aria-disabled', String(input.disabled)))
}
const vNumberAccessibility = {mounted: syncNumberAccessibility, updated: syncNumberAccessibility}

const connected = ref(false), busy = ref(false), error = ref(''), baselineId = ref(''), workspace = ref<Record<string, unknown> | null>(null), tab = ref('scenario')
const scenario = ref<SandboxScenarioDefinition | null>(null), baseTemplate = ref<SandboxScenarioDefinition | null>(null)
const run = ref<SandboxRunDefinition | null>(null)
const scenarioPreview = ref<SandboxScenarioPreview | null>(null), runPreview = ref<SandboxRunPreview | null>(null)
const scenarioPreviewIdentity = ref(''), runPreviewIdentity = ref(''), scenarioSavedIdentity = ref(''), runSavedIdentity = ref('')
const scenarioSavedDefinition = ref<SandboxScenarioDefinition | null>(null), runSavedDefinition = ref<SandboxRunDefinition | null>(null)
const scenarioVersion = ref(-1), runVersion = ref(-1), scenarioKnownKey = ref(''), runKnownKey = ref('')
const scenarioWriteLocked = ref(false), runWriteLocked = ref(false)
const runBindingStale = ref(false)
const publishedScenario = ref<SandboxScenarioRevision | null>(null), publishedRun = ref<SandboxRunRevision | null>(null)
const scenarioLoadKey = ref(''), runLoadKey = ref(''), scenarioLoadRevision = ref(1), runLoadRevision = ref(1)
const chains = ref<SandboxCatalogItem[]>([]), vehicleTypes = ref<string[]>([]), modelTypes = ref<string[]>([]), poiTypes = ref<string[]>([]), categories = ref<string[]>([])
const selectionEpoch = ref(0), selectionErrors = ref<Record<string, boolean>>({})
const weatherTypes = ['SUNNY', 'RAIN', 'SNOW', 'FOG'], eventTypes: Array<'congestion' | 'breakdown'> = ['congestion', 'breakdown']
const importVisible = ref(false), importText = ref(''), importKind = ref<'scenario' | 'run'>('scenario')
const familiesVisible = ref(false), familyKind = ref('scenarios'), families = ref<Array<Record<string, unknown>>>([]), familiesOffset = ref(0), familiesMore = ref(false)
const canEdit = computed(() => connected.value && !busy.value && !Object.values(selectionErrors.value).some(Boolean))
const canRunEdit = computed(() => connected.value && !busy.value && !runBindingStale.value)
const readonlyRunProtocol = computed(() => run.value ? {scenario: run.value.scenario, simulationClock: run.value.simulationClock,
  demand: run.value.demand, dispatch: run.value.dispatch, randomProtocol: run.value.random.protocolId,
  vehicleInitialization: run.value.vehicleInitialization, driverBehavior: run.value.driverBehavior, breakdownPolicy: run.value.events.breakdownPolicy} : null)
const resolvedRunPreview = computed(() => runPreview.value ? {vehicleInitialStates: runPreview.value.vehicleInitialStates, weatherTimeline: runPreview.value.weatherTimeline} : null)
function formatInputs(inputs: Array<{id: number; goodsId: number; inputShare: number}>) { return inputs.map(v => `${v.id} / ${v.goodsId} / ${v.inputShare}`).join('; ') }
const scenarioPreviewCurrent = computed(() => !!scenarioPreview.value && scenarioPreviewIdentity.value === draftIdentity(scenario.value))
const runPreviewCurrent = computed(() => !!runPreview.value && runPreviewIdentity.value === draftIdentity(run.value))
const scenarioSavedCurrent = computed(() => !!scenario.value && scenarioSavedIdentity.value === draftIdentity(scenario.value))
const runSavedCurrent = computed(() => !!run.value && runSavedIdentity.value === draftIdentity(run.value))
const scenarioPublishable = computed(() => scenarioSavedCurrent.value && scenarioPreviewCurrent.value && scenarioVersion.value >= 0)
const runPublishable = computed(() => runSavedCurrent.value && runPreviewCurrent.value && runVersion.value >= 0)
const scenarioUsesPublished = computed(() => !!publishedScenario.value && draftIdentity(scenario.value) === draftIdentity(publishedScenario.value.definition))
const runUsesPublished = computed(() => !!publishedRun.value && draftIdentity(run.value) === draftIdentity(publishedRun.value.specification))
const canStartPublished = computed(() => connected.value && !busy.value && !runBindingStale.value && !runWriteLocked.value
  && !!publishedRun.value && (runSavedCurrent.value || runUsesPublished.value)
  && (scenarioSavedCurrent.value || scenarioUsesPublished.value))
watch(scenario, () => {
  scenarioPreview.value = null; publishedScenario.value = null
  if (run.value) { runBindingStale.value = true; runPreview.value = null; publishedRun.value = null }
}, {deep: true, flush: 'sync'})
watch(run, () => { runPreview.value = null; publishedRun.value = null }, {deep: true, flush: 'sync'})

function reportFailure(failure: unknown) {
  if (failure instanceof SandboxApiError) {
    error.value = `${failure.code}: ${errorHints[failure.code] || '请求被拒绝。请检查配置、引用关系和后端错误码。'}`
    if (failure.uncertain) error.value += ' 请求结果未知：请先重新加载草稿或查询发布版本，不自动重试。'
    if (failure.status === 404 && failure.code === 'SANDBOX_UNAVAILABLE' || failure.status === null || failure.status === 503) connected.value = false
    if (failure.code === 'DRAFT_CONFLICT' || failure.uncertain) { scenarioPreview.value = null; runPreview.value = null }
  } else error.value = (failure as Error).message || '配置操作失败'
}
async function action(work: () => Promise<void>, mutationKind?: 'scenario' | 'run') {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await work() } catch (failure) {
    reportFailure(failure)
    if (mutationKind && failure instanceof SandboxApiError && (failure.uncertain || failure.code === 'DRAFT_CONFLICT')) {
      if (mutationKind === 'scenario') {
        scenarioWriteLocked.value = true; publishedScenario.value = null
        if (run.value) runBindingStale.value = true
      } else { runWriteLocked.value = true; publishedRun.value = null }
    }
  } finally { busy.value = false }
}
async function confirmDiscard(which: 'scenario' | 'run') {
  const value = which === 'scenario' ? scenario.value : run.value
  const saved = which === 'scenario' ? scenarioSavedCurrent.value : runSavedCurrent.value
  if (value && !saved) {
    try { await ElMessageBox.confirm('此操作会替换本地未保存配置。请先导出需要保留的JSON。继续？', '确认替换', {type: 'warning'}) }
    catch { return false }
  }
  return true
}
async function catalogAll(type: string) {
  const result: SandboxCatalogItem[] = []; let offset = 0
  do { const page = await sandboxApi.catalog(type, offset); result.push(...page.items); offset += page.items.length; if (offset >= page.total) break; if (!page.items.length) throw new Error('目录分页异常') } while (offset < 10000)
  return result
}
async function connect() {
  connected.value = false
  await action(async () => {
    const info = await sandboxApi.baseline(); const health = await sandboxApi.workspace()
    const template = await sandboxApi.scenarioTemplate()
    // Enum choices are server-derived from the authenticated eligible baseline; POIs remain paged.
    vehicleTypes.value = info.catalogFilters.vehicleTypes; modelTypes.value = info.catalogFilters.modelTypes
    poiTypes.value = info.catalogFilters.poiTypes; categories.value = info.catalogFilters.categories
    chains.value = await catalogAll('processing-chains')
    baseTemplate.value = template; baselineId.value = info.baselineId; workspace.value = health; connected.value = true
    if (!scenario.value) { scenario.value = cloneDefinition(template); scenario.value.scenarioKey = 'new-scenario' }
  })
}
async function loadScenarioDraft() {
  if (!await confirmDiscard('scenario')) return
  await action(async () => { const view = await sandboxApi.draft<SandboxScenarioDefinition>('scenarios', scenarioLoadKey.value)
    scenario.value = view.definition; scenarioKnownKey.value = view.definition.scenarioKey; scenarioVersion.value = view.rowVersion
    scenarioSavedDefinition.value = cloneDefinition(view.definition); scenarioSavedIdentity.value = draftIdentity(view.definition); scenarioWriteLocked.value = false; resetSelections() })
}
async function loadScenarioRevision() {
  if (!await confirmDiscard('scenario')) return
  await action(async () => { const revision = await sandboxApi.scenarioRevision(scenarioLoadKey.value, scenarioLoadRevision.value)
    scenario.value = cloneDefinition(revision.definition); scenarioVersion.value = -1; scenarioKnownKey.value = ''; scenarioSavedDefinition.value = null; scenarioSavedIdentity.value = ''
    resetSelections(); publishedScenario.value = revision })
}
function resetSelections() { selectionErrors.value = {}; selectionEpoch.value++ }
async function previewScenario() {
  await action(async () => { const definition = cloneDefinition(scenario.value!); const identity = draftIdentity(definition)
    const result = await sandboxApi.compileScenario(definition); if (identity !== draftIdentity(scenario.value)) throw new Error('配置已改变，请重新编译')
    scenarioPreview.value = result; scenarioPreviewIdentity.value = identity })
}
async function saveScenario() {
  if (!scenarioPreviewCurrent.value || scenarioWriteLocked.value) return
  await action(async () => { const definition = cloneDefinition(scenario.value!); const identity = draftIdentity(definition)
    const saved = await sandboxApi.saveScenario(definition, definition.scenarioKey === scenarioKnownKey.value ? scenarioVersion.value : -1)
    scenarioKnownKey.value = definition.scenarioKey; scenarioVersion.value = saved.rowVersion; scenarioSavedDefinition.value = definition; scenarioSavedIdentity.value = identity
    scenarioPreview.value = saved.compilation; scenarioPreviewIdentity.value = identity }, 'scenario')
}
async function publishScenario() {
  if (!scenarioPublishable.value || scenarioWriteLocked.value) return
  await action(async () => { publishedScenario.value = await sandboxApi.publishScenario(scenario.value!.scenarioKey, scenarioVersion.value, scenarioPreview.value!.scenarioDefinitionSha256) }, 'scenario')
}
async function resetScenario() {
  if (!baseTemplate.value || !await confirmDiscard('scenario')) return
  const metadata = scenario.value ? {scenarioKey: scenario.value.scenarioKey, displayName: scenario.value.displayName, description: scenario.value.description} : {}
  scenario.value = {...cloneDefinition(baseTemplate.value), ...metadata}; resetSelections()
}
function restoreScenarioLocal() { if (scenarioSavedDefinition.value) { scenario.value = cloneDefinition(scenarioSavedDefinition.value); resetSelections() } }
async function createRunTemplate() {
  if (!publishedScenario.value || !await confirmDiscard('run')) return
  await action(async () => { const ref = publishedScenario.value!; run.value = await sandboxApi.runTemplate(ref.scenarioKey, ref.revision)
    run.value.runSpecKey = 'new-run'; runVersion.value = -1; runKnownKey.value = ''; runSavedDefinition.value = null; runSavedIdentity.value = ''; runBindingStale.value = false; tab.value = 'run' })
}
function bindPublishedScenario() {
  if (!run.value || !publishedScenario.value) return
  const revision = publishedScenario.value
  run.value.scenario = {scenarioKey: revision.scenarioKey, revision: revision.revision,
    scenarioDefinitionSha256: revision.fingerprints.scenarioDefinitionSha256, effectiveScenarioDataSha256: revision.fingerprints.effectiveScenarioDataSha256}
  runBindingStale.value = false
}
function requireV2(definition: SandboxRunDefinition) { if (definition.artifactVersion !== 'sandbox-run-specification/v2') throw new Error('仅接受v2运行规格，不将v1自动转换为v2') }
async function loadRunDraft() {
  if (!await confirmDiscard('run')) return
  await action(async () => { const view = await sandboxApi.draft<SandboxRunDefinition>('runs', runLoadKey.value); requireV2(view.definition)
    run.value = view.definition; runKnownKey.value = view.definition.runSpecKey; runVersion.value = view.rowVersion
    runSavedDefinition.value = cloneDefinition(view.definition); runSavedIdentity.value = draftIdentity(view.definition); runWriteLocked.value = false; runBindingStale.value = false; tab.value = 'run' })
}
async function loadRunRevision() {
  if (!await confirmDiscard('run') || !await confirmDiscard('scenario')) return
  await action(async () => { const revision = await sandboxApi.runRevision(runLoadKey.value, runLoadRevision.value); requireV2(revision.specification)
    const boundScenario = await sandboxApi.scenarioRevision(revision.specification.scenario.scenarioKey, revision.specification.scenario.revision)
    scenario.value = cloneDefinition(boundScenario.definition); scenarioVersion.value = -1; scenarioKnownKey.value = ''; scenarioSavedDefinition.value = null; scenarioSavedIdentity.value = ''; resetSelections(); publishedScenario.value = boundScenario
    run.value = cloneDefinition(revision.specification); runVersion.value = -1; runKnownKey.value = ''; runSavedDefinition.value = null; runSavedIdentity.value = ''; runBindingStale.value = false; runWriteLocked.value = false; publishedRun.value = revision })
}
async function previewRun() {
  if (runBindingStale.value) return
  await action(async () => { const definition = cloneDefinition(run.value!); validateSeed(definition.random.rootSeed); const identity = draftIdentity(definition)
    const result = await sandboxApi.compileRun(definition); if (identity !== draftIdentity(run.value)) throw new Error('规格已改变，请重新编译')
    runPreview.value = result; runPreviewIdentity.value = identity })
}
async function saveRun() {
  if (!runPreviewCurrent.value || runWriteLocked.value || runBindingStale.value) return
  await action(async () => { const definition = cloneDefinition(run.value!); const identity = draftIdentity(definition)
    const saved = await sandboxApi.saveRun(definition, definition.runSpecKey === runKnownKey.value ? runVersion.value : -1)
    runKnownKey.value = definition.runSpecKey; runVersion.value = saved.rowVersion; runSavedDefinition.value = definition; runSavedIdentity.value = identity
    runPreview.value = saved.compilation; runPreviewIdentity.value = identity }, 'run')
}
async function publishRun() {
  if (!runPublishable.value || runWriteLocked.value || runBindingStale.value) return
  await action(async () => { publishedRun.value = await sandboxApi.publishRun(run.value!.runSpecKey, runVersion.value, runPreview.value!.runSpecificationSha256) }, 'run')
}
function restoreRunLocal() { if (runSavedDefinition.value) run.value = cloneDefinition(runSavedDefinition.value) }
function changeStrategy() { if (run.value) run.value.dispatch.algorithmProfileId = run.value.dispatch.strategy === 'ORIGINAL' ? 'original-vrp/v1' : 'heuristic-multi-order-ga/v1' }
function changeWeather(mode: 'SEEDED_GENERATION' | 'EXPLICIT_TIMELINE') { if (run.value) setWeatherMode(run.value, mode) }
function eventEnabledChanged() { if (run.value && !run.value.events.enabled) run.value.events.autoEnabled = false }
function addWeatherSlice() { if (run.value) { const slices = run.value.weather.timeSlices; const start = slices.length ? slices[slices.length - 1].endMinute : 0; slices.push({startMinute: start, endMinute: start + 60, weatherType: 'SUNNY', speedFactor: 1}) } }
function removeWeatherSlice(index: number) { run.value?.weather.timeSlices.splice(index, 1) }
function removeOverride(kind: keyof SandboxScenarioDefinition['overrides'], index: number) { scenario.value?.overrides[kind].splice(index, 1) }
function addOverride(kind: keyof SandboxScenarioDefinition['overrides']) {
  if (!scenario.value) return
  const overrides = scenario.value.overrides
  if (kind === 'stageOutputRatios') overrides.stageOutputRatios.push({stageId: chains.value[0]?.stages?.[0]?.id || 1, outputWeightRatio: 1})
  if (kind === 'processingInputShares') overrides.processingInputShares.push({inputId: chains.value[0]?.stages?.[0]?.inputs?.[0]?.id || 1, inputShare: 1})
  if (kind === 'initialInventories') overrides.initialInventories.push({poiId: 1, goodsId: 1, quantity: 1})
  if (kind === 'vehicleInitialPois') overrides.vehicleInitialPois.push({vehicleId: 1, poiId: 1, initializationPolicy: 'FIXED_POI'})
}
function exportRun() { if (run.value) exportJson(run.value, run.value.runSpecKey) }
function exportScenario() { if (scenario.value) exportJson(scenario.value, scenario.value.scenarioKey) }
function exportJson(definition: unknown, key: string) { const url = URL.createObjectURL(new Blob([JSON.stringify(definition, null, 2)], {type: 'application/json'})); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${key.replace(/[^a-z0-9-]/g, '_') || 'sandbox'}.json`; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
function openImport(kind: 'scenario' | 'run') { importKind.value = kind; importText.value = ''; importVisible.value = true }
async function applyImport() {
  try {
    if (importText.value.length > 1048576) throw new Error('JSON超过1MB限制')
    const definition = JSON.parse(importText.value)
    if (!await confirmDiscard(importKind.value)) return
    // Validate before binding malformed imported objects to form controls; no database mutation.
    await action(async () => {
      // Send original bytes for strict duplicate-key detection BEFORE JSON.parse's value is used.
      if (importKind.value === 'scenario') { const preview = await sandboxApi.compileScenarioText(importText.value); scenario.value = definition; resetSelections(); scenarioPreview.value = preview; scenarioPreviewIdentity.value = draftIdentity(definition) }
      else { requireV2(definition); validateSeed(definition.random?.rootSeed); const preview = await sandboxApi.compileRunText(importText.value); run.value = definition; runBindingStale.value = false; runPreview.value = preview; runPreviewIdentity.value = draftIdentity(definition) }
      importVisible.value = false
    })
  } catch (failure) { reportFailure(failure) }
}
async function listFamilies(kind: string, offset = 0) { await action(async () => { const list = await sandboxApi.families(kind, offset); families.value = list.items; familyKind.value = kind; familiesOffset.value = offset; familiesMore.value = list.hasMore; familiesVisible.value = true }) }
async function pickFamily(row: Record<string, unknown>) { familiesVisible.value = false; if (familyKind.value === 'scenarios') { scenarioLoadKey.value = String(row.scenario_key); await loadScenarioDraft() } else { runLoadKey.value = String(row.run_spec_key); await loadRunDraft() } }
const hasLocalChanges = computed(() => !!scenario.value && !scenarioSavedCurrent.value && !(publishedScenario.value && draftIdentity(scenario.value) === draftIdentity(publishedScenario.value.definition))
  || !!run.value && !runSavedCurrent.value && !(publishedRun.value && draftIdentity(run.value) === draftIdentity(publishedRun.value.specification)))
function beforeUnload(event: BeforeUnloadEvent) { if (hasLocalChanges.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(async () => { if (!hasLocalChanges.value) return true; try { await ElMessageBox.confirm('存在未保存配置，离开后将丢失本地修改。继续？', '离开配置页'); return true } catch { return false } })
onMounted(() => { window.addEventListener('beforeunload', beforeUnload); connect() })
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<style scoped>
.sandbox-page { max-width: 1250px; margin: 0 auto; padding: 24px; color: #303133; }
header, .connection, .toolbar, .actions, .override-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
header { justify-content: space-between; } h1 { margin: 0; font-size: 25px; }
.connection, .toolbar, .actions { margin: 18px 0; } .toolbar .el-input { width: 230px; }
.notice { margin-top: 12px; } .filter-grid { display: grid; grid-template-columns: repeat(2, minmax(280px, 1fr)); gap: 8px; }
.override-row { margin: 10px 0; } .preview { background: #f0f7f5; border: 1px solid #bcd9d0; border-radius: 8px; padding: 16px; margin-top: 16px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 450px; overflow: auto; } .fingerprint { overflow-wrap: anywhere; font-family: monospace; }
@media(max-width: 700px) { .sandbox-page { padding: 12px; } .filter-grid { grid-template-columns: 1fr; } }
</style>
