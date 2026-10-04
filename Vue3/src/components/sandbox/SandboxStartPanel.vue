<template>
  <section class="start-panel">
    <h3>准备并运行已发布版本</h3>
    <p>不会执行未发布的草稿；每次新运行均重新准备沙箱业务数据，沿用该版本种子。历史版本与账本保留。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="pending">请求结果待核对：{{ pending.clientRequestId }} · {{ pending.runSpecKey }} / Revision {{ pending.revision }}。不会自动重试或生成新请求。</p>
    <el-button v-if="pending" :disabled="busy" @click="resolvePending">核对启动请求</el-button>
    <el-button v-if="pending && confirmedAbsent" :disabled="busy" @click="retrySameRequest">使用同一请求标识重新提交</el-button>
    <el-button type="primary" :disabled="!enabled || !revision || busy || !!pending || storageBlocked" @click="openConfirmation">准备并运行</el-button>
    <router-link to="/sandbox/tasks">查看沙箱任务与进度</router-link>
    <p v-if="!enabled">有未保存/未发布修改、绑定失效或后台不可用。请完成发布，或明确加载已发布版本后再运行。</p>
    <el-dialog v-model="confirmation" title="确认准备并运行" width="650px">
      <template v-if="confirmedRevision">
        <p>运行规格：{{ confirmedRevision.runSpecKey }} / Revision {{ confirmedRevision.revision }}</p>
        <p>场景：{{ confirmedRevision.specification.scenario.scenarioKey }} / Revision {{ confirmedRevision.specification.scenario.revision }}</p>
        <p>根种子：{{ confirmedRevision.specification.random.rootSeed }}；轮次：{{ confirmedRevision.specification.simulationClock.totalLoops }}；调度：{{ confirmedRevision.specification.dispatch.strategy }}</p>
        <p>天气：{{ confirmedRevision.specification.weather.sourceMode }}；自动事件：{{ confirmedRevision.specification.events.enabled && confirmedRevision.specification.events.autoEnabled ? '启用' : '关闭' }}</p>
        <p>实际故障维修/救援/换车时间由breakdownPolicy决定，不等同于通用故障持续时间字段。</p>
        <details><summary>实际故障策略</summary><pre>{{ confirmedRevision.specification.events.breakdownPolicy }}</pre></details>
        <p>将重建vehicle_scheduler_sandbox业务表。普通仿真数据不变。准备和核验收尾期间不能取消；运行中只能在完整轮次边界安全取消。关闭页面不停止后台任务。</p>
      </template>
      <template #footer><el-button @click="confirmation = false">返回</el-button><el-button type="primary" :disabled="busy || !enabled" @click="confirmStart">确认准备并运行</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { SandboxRunRevision, SandboxStartRequest } from '../../types/sandbox'
import { sandboxApi, SandboxApiError } from '../../api/sandbox'
import { pendingStartKey, parsePendingStart } from '../../sandbox/execution'
import { beginNavigationOperation } from '../../navigation/modeNavigation'
const props = defineProps<{revision: SandboxRunRevision | null; enabled: boolean}>()
const router = useRouter(), busy = ref(false), error = ref(''), pending = ref<SandboxStartRequest | null>(null)
const confirmation = ref(false), confirmedRevision = ref<SandboxRunRevision | null>(null), confirmedAbsent = ref(false), storageBlocked = ref(false)
function failure(value: unknown) { error.value = value instanceof SandboxApiError ? `${value.code}：启动状态未确认。请核对请求或任务，不自动重试。` : (value as Error).message }
function retain(request: SandboxStartRequest) { localStorage.setItem(pendingStartKey, JSON.stringify(request)); pending.value = request; confirmedAbsent.value = false }
async function submitPending() {
  if (!pending.value || busy.value) return
  busy.value = true; error.value = ''; confirmedAbsent.value = false
  const releaseNavigation = beginNavigationOperation('SANDBOX_START_UNCONFIRMED')
  try {
    const request = {...pending.value}; const receipt = await sandboxApi.start(request)
    if (receipt.clientRequestId !== request.clientRequestId || receipt.runSpecKey !== request.runSpecKey || receipt.revision !== request.revision) throw new Error('启动回执与请求不匹配，需人工核对')
    localStorage.removeItem(pendingStartKey); pending.value = null; confirmation.value = false
    await router.push(`/sandbox/jobs/${receipt.jobId}`)
  } catch (value) { failure(value) } finally { busy.value = false; releaseNavigation() }
}
async function openConfirmation() {
  if (!props.enabled || !props.revision || pending.value || busy.value) return
  busy.value = true; error.value = ''
  try {
    const workspace = await sandboxApi.workspace()
    if (workspace.control_schema_version !== 'sandbox-control-schema/v9') throw new Error('启动需要管理员先升级沙箱控制结构到v9')
    // active_execution_id also identifies the LAST terminal execution; it is not a busy flag.
    if (workspace.active_job_id || workspace.workspace_state === 'EXECUTION_RUNNING') throw new Error('沙箱工作库忙；请查看现有任务，不排队')
    confirmedRevision.value = JSON.parse(JSON.stringify(props.revision)); confirmation.value = true
  } catch (value) { failure(value) } finally { busy.value = false }
}
async function confirmStart() {
  if (!props.enabled || !confirmedRevision.value || pending.value || busy.value) return
  try {
    const request = {clientRequestId: crypto.randomUUID(), runSpecKey: confirmedRevision.value.runSpecKey, revision: confirmedRevision.value.revision}
    // Persist BEFORE the write: reload or response loss must retain the SAME identity.
    retain(request); await submitPending()
  } catch (value) { storageBlocked.value = true; failure(value) }
}
async function resolvePending() {
  if (!pending.value || busy.value) return
  busy.value = true; error.value = ''; confirmedAbsent.value = false
  try {
    const request = pending.value; const receipt = await sandboxApi.startRequest(request.clientRequestId)
    if (receipt.runSpecKey !== request.runSpecKey || receipt.revision !== request.revision) throw new Error('请求绑定不一致，需人工核对')
    localStorage.removeItem(pendingStartKey); pending.value = null
    await router.push(`/sandbox/jobs/${receipt.jobId}`)
  } catch (value) {
    if (value instanceof SandboxApiError && value.code === 'START_REQUEST_NOT_FOUND') {
      confirmedAbsent.value = true; error.value = '当前尚未发现对应任务。这不证明原请求永远不会到达；可显式使用同一请求标识重新提交，不能生成新标识。'
    } else failure(value)
  } finally { busy.value = false }
}
async function retrySameRequest() { if (confirmedAbsent.value) await submitPending() }
onMounted(() => { try { pending.value = parsePendingStart(localStorage.getItem(pendingStartKey)) } catch (value) { storageBlocked.value = true; failure(value) } })
</script>
<style scoped>.start-panel{border:1px solid #b9c7cd;padding:16px;margin-top:16px}.start-panel a{margin-left:16px}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style>
