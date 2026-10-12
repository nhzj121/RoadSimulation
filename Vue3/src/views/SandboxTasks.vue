<template>
  <main class="sandbox-tasks">
    <h1>数据沙箱 · 任务与进度</h1><router-link to="/sandbox">返回配置与发布</router-link>
    <p>刷新或关闭页面不停止后台任务；此页不推进业务、不支持暂停或恢复。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="operationNotice" :title="operationNotice" type="info" :closable="false" />
    <el-button :disabled="loading" @click="refresh">重新查询</el-button>
    <p v-if="lastUpdated">最后查询成功：{{ lastUpdated }}{{ stale ? '（当前状态暂不可获取，以下为上次信息）' : '' }}</p>
    <section v-if="view">
      <h2>{{ taskLabel(view.status) }}</h2>
      <p>任务编号：{{ view.jobId }}</p><p>规格：{{ view.runSpecKey }} / Revision {{ view.revision }}</p>
      <p>执行记录：{{ view.executionId || '尚未创建' }}；进程观察：{{ view.workerObservation }}</p>
      <template v-if="view.progress.requestedLoops !== null && view.progress.recordedLoops !== null">
        <el-progress :percentage="Math.round(Math.min(100, view.progress.percent || 0) * 100) / 100" />
        <p>已完整记录 {{ view.progress.recordedLoops }} / {{ view.progress.requestedLoops }} 轮。达到100%不等于核验收尾完成。</p>
      </template>
      <p v-else>准备阶段暂无已落库轮次进度；不估算准备百分比。</p>
      <el-alert v-if="view.attentionRequired || view.status === 'INTERRUPTED'" :title="`需要人工检查：${view.failureCode || view.workerObservation}。任务编号：${view.jobId}。请使用内部inspect-job/close-abnormal-job流程；页面不提供强制解锁。`" type="warning" :closable="false" />
      <p v-if="view.failureCode">原因：{{ view.failureCode }}；阶段：{{ view.failurePhase || view.phase }}</p>
      <el-button type="warning" :disabled="!canCancel || cancelling" @click="requestCancel">请求安全取消</el-button>
      <p v-if="view.cancellationRequested && !terminalStatuses.has(view.status)">取消请求已记录。等待完整轮次边界及核验；业务或收尾失败仍可能得到FAILED，不保证最终CANCELLED。</p>
      <p v-if="view.status === 'CANCELLED'">取消已完成；保留完整轮次前缀，没有强制终止正在执行的业务。</p>
      <p v-if="view.status === 'SUCCEEDED'">全部轮次正常收尾；结果及逐轮地图按执行编号独立保存。</p>
      <router-link v-if="view.executionId && !view.attentionRequired && view.status !== 'INTERRUPTED'" :to="`/sandbox/executions/${view.executionId}/results`">查看结果</router-link>
    </section>
    <h2>任务列表</h2><p v-if="activeId">工作库当前占位：<router-link :to="`/sandbox/jobs/${activeId}`">{{ activeId }}</router-link></p>
    <el-table :data="items">
      <el-table-column prop="job_id" label="任务编号" /><el-table-column prop="run_spec_key" label="运行规格" /><el-table-column prop="run_spec_revision" label="Revision" />
      <el-table-column label="状态"><template #default="{row}">{{ taskLabel(row.job_status) }}</template></el-table-column>
      <el-table-column label="操作"><template #default="{row}"><router-link :to="`/sandbox/jobs/${row.job_id}`">查看进度</router-link> <router-link v-if="row.execution_id && row.job_status !== 'INTERRUPTED'" :to="`/sandbox/executions/${row.execution_id}/results`">查看结果</router-link></template></el-table-column>
    </el-table>
    <el-button :disabled="offset === 0 || loading" @click="changePage(-20)">上一页</el-button><el-button :disabled="!hasMore || loading" @click="changePage(20)">下一页</el-button>
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { sandboxApi, SandboxApiError } from '../api/sandbox'
import type { SandboxJobView, SandboxJobList } from '../types/sandbox'
import { canCancelTask, taskLabel, terminalStatuses } from '../sandbox/execution'
const route = useRoute(), view = ref<SandboxJobView | null>(null), items = ref<SandboxJobList['items']>([])
const activeId = ref<string | null>(null), offset = ref(0), hasMore = ref(false), loading = ref(false), cancelling = ref(false)
const stale = ref(true), error = ref(''), operationNotice = ref(''), lastUpdated = ref(''), jobId = computed(() => String(route.params.id || ''))
const canCancel = computed(() => canCancelTask(view.value, stale.value) && !loading.value)
let timer: ReturnType<typeof setTimeout> | undefined, disposed = false, generation = 0
function fail(value: unknown) { error.value = value instanceof SandboxApiError ? `${value.code}：状态查询或操作未完成，不代表后台实验失败。` : (value as Error).message }
async function refresh() {
  if (loading.value || disposed) return
  if (timer) clearTimeout(timer)
  loading.value = true; error.value = ''; const current = generation, id = jobId.value
  try {
    const [workspace, list, currentView] = await Promise.all([sandboxApi.workspace(), sandboxApi.jobs(offset.value), id ? sandboxApi.jobView(id) : Promise.resolve(null)])
    if (disposed || current !== generation) return
    activeId.value = workspace.active_job_id as string | null; items.value = list.items; hasMore.value = list.hasMore
    view.value = currentView; stale.value = false; lastUpdated.value = new Date().toLocaleTimeString()
  } catch (value) { if (!disposed && current === generation) { stale.value = true; fail(value) } }
  finally {
    loading.value = false
    if (!disposed) {
      if (current !== generation) void refresh()
      else if (stale.value || !view.value || !terminalStatuses.has(view.value.status) && !view.value.attentionRequired && view.value.status !== 'INTERRUPTED') timer = setTimeout(refresh, stale.value ? 5000 : 2000)
    }
  }
}
async function requestCancel() {
  if (!canCancel.value || !view.value || cancelling.value) return
  const id = view.value.jobId
  try { await ElMessageBox.confirm('仅请求在完整轮次边界停止；当前算法或高德请求不会被强制打断。准备/收尾期间不能取消。确认？', '安全取消', {type: 'warning'}) } catch { return }
  if (id !== jobId.value || !canCancel.value) return
  cancelling.value = true
  try { const result = await sandboxApi.cancelJob(id); operationNotice.value = `${taskLabel(result.status)}。以后台后续状态为准。` }
  catch (value) { operationNotice.value = value instanceof SandboxApiError ? `${value.code}：取消未确认，已重新查询状态；不自动重试。` : (value as Error).message }
  finally { cancelling.value = false; await refresh() }
}
function changePage(delta: number) { offset.value = Math.max(0, offset.value + delta); void refresh() }
watch(jobId, () => { generation++; view.value = null; operationNotice.value = ''; stale.value = true; void refresh() })
onMounted(refresh)
onBeforeUnmount(() => { disposed = true; generation++; if (timer) clearTimeout(timer) })
</script>
<style scoped>.sandbox-tasks{padding:24px;max-width:1200px;margin:auto}.sandbox-tasks p{overflow-wrap:anywhere}.sandbox-tasks section{padding:16px;border:1px solid #ccc;margin-top:16px}</style>
