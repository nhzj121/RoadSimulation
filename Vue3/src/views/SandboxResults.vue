<template>
  <main class="sandbox-results">
    <header class="results-header">
      <div><span class="results-eyebrow">SANDBOX EXECUTION RESULTS</span><h1>数据沙箱 · 执行结果</h1><p>此页只读取永久记录，不推进仿真、不重跑、不解锁工作库。</p></div>
      <nav class="results-links" aria-label="结果导航"><router-link to="/sandbox/tasks">返回任务列表</router-link><router-link v-if="summary && !blocked && !inspection && !stale" :to="`/sandbox/executions/${executionId}/map`">查看逐轮地图</router-link></nav>
    </header>
    <el-alert v-if="inspection" :title="`需要人工检查：${inspection.reason}；任务编号：${inspection.jobId}`" type="warning" :closable="false" />
    <el-alert v-else-if="blocked" :title="`记录不可用于正常展示或导出：${error}。不会自动修复。`" type="error" :closable="false" />
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <div class="read-toolbar"><p v-if="lastUpdated">最后读取：{{ lastUpdated }}{{ stale ? '（信息已过期；当前网络错误不代表实验失败）' : '' }}</p><el-button :disabled="loading" @click="refresh">重新查询结果</el-button></div>
      <template v-if="summary">
        <section class="results-card execution-overview" aria-label="执行概览">
          <div class="section-heading"><div><span class="results-eyebrow">EXECUTION OVERVIEW</span><h2>{{ resultLabel(summary.resultKind) }}</h2></div><span class="readonly-label">永久记录 · 只读</span></div>
          <div class="execution-facts"><p class="execution-id"><span>执行编号</span><strong>{{ summary.executionId }}</strong></p><p class="execution-progress"><span>执行状态与记录进度</span><strong>{{ summary.status }} · 完整记录 {{ summary.progress.recordedLoops }} / 设定 {{ summary.progress.requestedLoops }} 轮</strong></p></div>
          <p v-if="summary.failureCode" class="failure-notice">原因 {{ summary.failureCode }}；失败阶段 {{ summary.failurePhase || '无' }}；失败 loopIndex {{ summary.failedLoopIndex ?? '无' }}（失败轮不计入完整记录）。</p>
          <p class="read-scope">本次读取：冻结清单指纹及身份、评价载荷指纹及身份检查；不是完整账本核验。</p>
          <p v-if="summary.status === 'COMPLETED' || summary.status === 'CANCELLED'" class="read-scope">正常／取消收尾已执行既有账本核验；下方是用户本次显式重新核验，两者分开说明。</p>
        </section>
        <template v-if="recorded > 0">
          <section class="results-card loop-toolbar" aria-label="逐轮查看"><div><h2>逐轮查看</h2><span class="follow-label">{{ following ? '跟随最新' : '已固定历史轮次，不会被更新覆盖' }}</span></div><div class="loop-controls"><label>展示轮次（从1开始） <el-input :model-value="loopNumber" type="number" @input="loopNumber=Number($event)" /></label><el-button :disabled="stale || selecting" @click="selectLoop">查看所选轮次</el-button><el-button :disabled="stale" @click="returnLatest">返回最新轮次</el-button></div></section>
          <SandboxEvaluationMetrics :snapshot="selected" :record-context="{executionId,manifestSha256:summary.manifestSha256,stale}" @record-error="fail">
            <template #trends>
              <section class="results-card trend-section" aria-label="逐轮趋势">
                <div class="section-heading"><div><span class="results-eyebrow">RECORDED TRENDS</span><h2>逐轮趋势</h2></div></div><p class="section-caption">默认最近200轮；按完整记录连续读取，不抽样、不平滑、缺值断开。各图纵轴自适应，仅用于观察该指标，不做跨指标评分。</p>
                <div class="range-controls"><label>从第 <el-input :model-value="rangeStart" type="number" @input="rangeStart=Number($event)" />轮</label><label>到第 <el-input :model-value="rangeEnd" type="number" @input="rangeEnd=Number($event)" />轮</label><el-button :disabled="stale || trendLoading" @click="applyRange">加载区间</el-button><el-button :disabled="stale || trendLoading" @click="resetWindow">最近200轮</el-button></div>
                <p v-if="trendLoading" class="trend-notice">正在分页读取固定区间 {{ loadedRange || '' }}；新轮次不会混入该次加载。</p>
                <p v-if="trendStale" class="trend-notice">趋势区间尚未确认；暂不展示旧区间为新结果。</p>
                <div v-else class="trends"><article v-for="id in objectiveIds" :key="id"><SandboxRecordedTrendChart :snapshots="trend" :metric-id="id" /><p>{{ trend.length }}轮 · {{ loadedRange }}</p></article></div>
              </section>
            </template>
          </SandboxEvaluationMetrics>
        </template><p v-else class="results-card empty-evaluation">暂无评价（零轮记录）；已收尾时仍可导出空评价 JSON 或仅表头 CSV。</p>
        <section class="results-card verification-section" aria-label="核验与导出"><div class="section-heading"><div><span class="results-eyebrow">INTEGRITY & EXPORT</span><h2>核验与导出</h2></div><div class="operations"><el-button :disabled="!canOperate || verifying" @click="verify">显式完整核验</el-button><el-button :disabled="!canOperate || downloading" @click="download('json')">下载 JSON</el-button><el-button :disabled="!canOperate || downloading" @click="download('csv')">下载 CSV</el-button></div></div>
          <p v-if="verification" class="verification-message">{{ verification }}</p><p v-else>此页面会话尚无此次完整核验结论；查看与导出无需先手动核验。</p><p v-if="operationNotice" class="operation-notice">{{ operationNotice }}</p><a v-if="downloadUrl && canOperate" :href="downloadUrl" :download="downloadName" target="_blank" rel="noopener">浏览器未开始下载？点击保存 {{ downloadName }}</a>
        </section>
        <details v-if="configuration" class="results-card configuration-section"><summary>配置追溯：冻结场景、规格、种子、时钟、算法、天气与事件、指纹</summary><p>Java {{ configuration.javaVersion }}；manifest {{ configuration.manifestSha256 }}</p><pre>{{ JSON.stringify(configuration, null, 2) }}</pre></details>
      </template>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, ref, shallowRef, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { sandboxApi, SandboxApiError } from '../api/sandbox'
import type { SandboxExecutionSummary, SandboxExecutionConfiguration } from '../types/sandbox'
import type { EvaluationSnapshot } from '../types/evaluation'
import SandboxEvaluationMetrics from '../components/sandbox/SandboxEvaluationMetrics.vue'
import SandboxRecordedTrendChart from '../components/sandbox/SandboxRecordedTrendChart.vue'
import { objectiveIds, latestWindow, verificationBinding, trendSegments, closedExecution, terminalExecution, resultLabel } from '../sandbox/results'
const route=useRoute(), executionId=computed(()=>String(route.params.id || ''))
const summary=shallowRef<SandboxExecutionSummary|null>(null), configuration=shallowRef<SandboxExecutionConfiguration|null>(null)
const selected=ref<EvaluationSnapshot|null>(null), trend=ref<EvaluationSnapshot[]>([]), recorded=computed(()=>summary.value?.progress.recordedLoops || 0)
const loading=ref(false), stale=ref(true), error=ref(''), blocked=ref(false), inspection=ref<{jobId:string;reason:string}|null>(null), lastUpdated=ref('')
const following=ref(true), loopNumber=ref(1), selecting=ref(false), rangeStart=ref(1), rangeEnd=ref(1), loadedRange=ref(''), autoWindow=ref(true), trendLoading=ref(false), trendStale=ref(true)
const verifying=ref(false), downloading=ref(false), verification=ref(''), operationNotice=ref('')
const downloadUrl=ref(''), downloadName=ref('')
const canOperate=computed(()=>!!summary.value && closedExecution(summary.value.status) && !stale.value && !blocked.value && !inspection.value)
let generation=0, rangeGeneration=0, selectionGeneration=0, disposed=false, binding='', timer:ReturnType<typeof setTimeout>|undefined
let pollController:AbortController|undefined, rangeController:AbortController|undefined, selectionController:AbortController|undefined
function fail(value:unknown) {
  const e=value instanceof SandboxApiError?value:null
  error.value=e?.code || (value as Error).message
  if(e?.code==='INSPECTION_REQUIRED') { inspection.value={jobId:e.jobId || '未知任务',reason:e.reason || e.code}; summary.value=null; configuration.value=null; selected.value=null; trend.value=[] }
  if(['EXECUTION_RECORD_CORRUPT','EXECUTION_UNJOURNALED'].includes(e?.code || '')) { blocked.value=true; selected.value=null; trend.value=[]; verification.value='' }
}
async function refresh() {
  if(disposed || loading.value || blocked.value || inspection.value) return
  if(timer) clearTimeout(timer)
  const current=generation,id=executionId.value; pollController=new AbortController(); const signal=pollController.signal
  loading.value=true
  try {
    const s=await sandboxApi.executionSummary(id,signal)
    if(disposed || current!==generation) return
    if(!configuration.value) {
      const c=await sandboxApi.executionConfiguration(id,signal)
      if(disposed || current!==generation) return
      if(c.executionId!==id || c.manifestSha256!==s.manifestSha256) throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false)
      configuration.value=c
    }
    if(s.executionId!==id || s.manifestSha256!==configuration.value.manifestSha256) throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false)
    const changed=s.lastRecordedLoopIndex!==summary.value?.lastRecordedLoopIndex
    summary.value=s; stale.value=false; error.value=''; lastUpdated.value=new Date().toLocaleTimeString()
    if(binding && binding!==verificationBinding(s)) { verification.value=''; binding='' }
    if(following.value) { selected.value=s.lastEvaluation as unknown as EvaluationSnapshot|null; loopNumber.value=(s.lastRecordedLoopIndex ?? -1)+1 }
    if(recorded.value>0 && autoWindow.value && (changed || trendStale.value)) { const range=latestWindow(recorded.value)!; rangeStart.value=range[0]+1; rangeEnd.value=range[1]+1; void loadRange(range[0],range[1]) }
  } catch(value) { if(!disposed && current===generation) { stale.value=true; fail(value) } }
  finally {
    if(current===generation) {
      loading.value=false
      if(!disposed && !blocked.value && !inspection.value && (!summary.value || !terminalExecution(summary.value.status))) timer=setTimeout(refresh,stale.value?5000:2000)
    }
  }
}
async function loadRange(from:number,to:number) {
  rangeController?.abort(); rangeController=new AbortController()
  const current=generation, token=++rangeGeneration,id=executionId.value,signal=rangeController.signal
  trendLoading.value=true; trendStale.value=true
  try {
    const snapshots:EvaluationSnapshot[]=[]
    for(let offset=0;offset<=to-from;offset+=100) {
      const page=await sandboxApi.evaluations(id,from,to,offset,signal)
      if(disposed || current!==generation || token!==rangeGeneration) return
      if(page.executionId!==id || page.fromLoopIndex!==from || page.toLoopIndex!==to || page.items.length!==Math.min(100,to-from+1-offset)) throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false)
      page.items.forEach((item,i)=> { if(item.loopIndex!==from+offset+i) throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false); snapshots.push(item.evaluation as unknown as EvaluationSnapshot) })
    }
    trend.value=snapshots; loadedRange.value=`第${from+1}–${to+1}轮`; trendStale.value=false
  } catch(value) { if(!disposed && current===generation && token===rangeGeneration) fail(value) }
  finally { if(current===generation && token===rangeGeneration) trendLoading.value=false }
}
function validRange(from:number,to:number) { return Number.isSafeInteger(from) && Number.isSafeInteger(to) && from>=0 && to>=from && to<recorded.value }
function applyRange() { if(stale.value) return; if(!validRange(rangeStart.value-1,rangeEnd.value-1)) { error.value='INVALID_RANGE：轮次必须为已记录范围内的连续整数'; return }; autoWindow.value=false; void loadRange(rangeStart.value-1,rangeEnd.value-1) }
function resetWindow() { const range=latestWindow(recorded.value); if(stale.value || !range) return; autoWindow.value=true; rangeStart.value=range[0]+1; rangeEnd.value=range[1]+1; void loadRange(...range) }
async function selectLoop() {
  const index=loopNumber.value-1
  if(stale.value || !validRange(index,index)) { error.value='INVALID_RANGE：只能选择完整记录轮次'; return }
  following.value=false; selectionController?.abort(); selectionController=new AbortController()
  const current=generation, token=++selectionGeneration,id=executionId.value; selecting.value=true
  try {
    const page=await sandboxApi.evaluations(id,index,index,0,selectionController.signal)
    if(!disposed && current===generation && token===selectionGeneration) {
      if(page.executionId!==id || page.items.length!==1 || page.items[0].loopIndex!==index) throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false)
      selected.value=page.items[0].evaluation as unknown as EvaluationSnapshot
    }
  } catch(value) { if(!disposed && current===generation && token===selectionGeneration) fail(value) }
  finally { if(current===generation && token===selectionGeneration) selecting.value=false }
}
function returnLatest() { selectionGeneration++; selectionController?.abort(); selecting.value=false; following.value=true; selected.value=summary.value?.lastEvaluation as unknown as EvaluationSnapshot|null; loopNumber.value=(summary.value?.lastRecordedLoopIndex ?? -1)+1 }
async function verify() {
  if(!canOperate.value || verifying.value || !summary.value) return
  const current=generation,id=executionId.value, fingerprint=verificationBinding(summary.value); verifying.value=true; verification.value=''
  try {
    const result=await sandboxApi.verifyExecution(id)
    if(disposed || current!==generation) return
    if(result.integrity!=='VERIFIED' || result.executionId!==id || result.manifestSha256!==summary.value?.manifestSha256 || result.recordedLoops!==recorded.value || result.lastTickSha256!==(summary.value?.lastTickSha256 || summary.value?.manifestSha256)) throw new SandboxApiError('EXECUTION_RECORD_CORRUPT',500,false)
    binding=fingerprint; verification.value=`本次完整核验：${result.integrity}（绑定当前执行、manifest、末轮指纹及${recorded.value}轮；仅本页面会话有效）`
  } catch(value) { if(!disposed && current===generation) { verification.value='核验结果未确认；不自动重试，不修改任务状态。'; fail(value) } }
  finally { if(current===generation) verifying.value=false }
}
async function download(format:'json'|'csv') {
  if(!canOperate.value || downloading.value) return
  const current=generation,id=executionId.value; downloading.value=true; operationNotice.value=''; clearDownload()
  try {
    const blob=await sandboxApi.exportResult(id,format)
    if(disposed || current!==generation) return
    const url=URL.createObjectURL(blob),anchor=document.createElement('a'); downloadUrl.value=sandboxApi.resultDownloadUrl(id,format); downloadName.value=`sandbox-result-${id}.${format}`; anchor.href=url; anchor.download=downloadName.value
    try { document.body.appendChild(anchor); anchor.click(); operationNotice.value='下载已交给浏览器；覆盖全部已记录轮次，仅核对manifest和评价载荷，不声称完成全账本核验。' }
    finally { anchor.remove(); setTimeout(()=>URL.revokeObjectURL(url),1000) }
  } catch(value) { if(!disposed && current===generation) { operationNotice.value='下载未确认；不自动重试。'; fail(value) } }
  finally { if(current===generation) downloading.value=false }
}
function clearDownload() { downloadUrl.value=''; downloadName.value='' }
function metricName(id:string) { return trend.value.find(s=>s.metrics[id])?.metrics[id]?.displayName || id }
function serviceLabel(s:EvaluationSnapshot) { const m=s.metrics.waitingServiceCompliant; return !m ? '未记录' : m.status!=='AVAILABLE' || m.value===null ? m.status : s.contractVersion!=='1.9' ? `${m.value} ${m.unit} (${m.status})` : m.value>=.5?'满足':'不满足' }
function serviceClass(s:EvaluationSnapshot) { return serviceLabel(s)==='满足'?'satisfied':serviceLabel(s)==='不满足'?'violated':'unavailable' }
function reset() {
  clearDownload()
  generation++; rangeGeneration++; selectionGeneration++; pollController?.abort(); rangeController?.abort(); selectionController?.abort(); if(timer) clearTimeout(timer)
  summary.value=null; configuration.value=null; selected.value=null; trend.value=[]; blocked.value=false; inspection.value=null; stale.value=true; error.value=''; verification.value=''; binding=''; operationNotice.value=''; lastUpdated.value=''; following.value=true; autoWindow.value=true; trendStale.value=true; loading.value=false; trendLoading.value=false; selecting.value=false; verifying.value=false; downloading.value=false
  void refresh()
}
watch(executionId,reset); onMounted(refresh); onBeforeUnmount(()=>{clearDownload();disposed=true;generation++;pollController?.abort();rangeController?.abort();selectionController?.abort();if(timer) clearTimeout(timer)})
</script>
<style scoped>
.sandbox-results { padding: 28px 32px 40px; max-width: 1664px; margin: auto; color: #303133; }
.sandbox-results p, .sandbox-results a, .sandbox-results strong { overflow-wrap: anywhere; }
.sandbox-results h1 { font-size: 27px; line-height: 1.45; font-weight: 600; letter-spacing: -.5px; }
.sandbox-results h2 { font-size: 18px; font-weight: 600; line-height: 1.5; }
.sandbox-results h3 { font-size: 14px; font-weight: 600; }
.results-header, .section-heading, .loop-toolbar, .service-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.results-header { margin-bottom: 18px; }
.results-header p { margin-top: 7px; font-size: 13px; }
.results-eyebrow { display: block; color: #77889a; font-size: 10px; font-weight: 600; letter-spacing: 1.4px; margin-bottom: 5px; }
.results-links { display: flex; flex-wrap: wrap; gap: 10px; flex-shrink: 0; }
.results-links a { border: 1px solid #dcdfe6; background: #fff; border-radius: 6px; padding: 8px 14px; font-size: 13px; text-decoration: none; }
.results-links a:hover { background: #ecf5ff; border-color: #a0cfff; }
.read-toolbar { display: flex; justify-content: flex-end; align-items: center; flex-wrap: wrap; gap: 12px; margin: 12px 0; min-height: 32px; }
.read-toolbar p { font-size: 12px; flex: 1; }
.results-card { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 22px 24px; margin-bottom: 20px; box-shadow: 0 2px 6px rgba(31,45,61,.025); min-width: 0; }
.readonly-label { color: #60758c; background: #f0f4f8; font-size: 12px; padding: 5px 10px; border-radius: 5px; white-space: nowrap; }
.execution-facts { display: grid; grid-template-columns: minmax(0,1.15fr) minmax(0,1fr); gap: 16px; margin: 16px 0; padding: 15px 18px; background: #f7f8fa; border-radius: 7px; }
.execution-facts span { display: block; font-size: 11px; color: #7b8795; margin-bottom: 6px; }
.execution-facts strong { display: block; color: #303133; font-size: 14px; font-weight: 500; font-variant-numeric: tabular-nums; }
.execution-id strong { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
.read-scope { font-size: 12px; line-height: 1.8; }
.failure-notice { background: #fff4f2; border: 1px solid #f4d5cf; color: #a64035 !important; padding: 10px 12px; border-radius: 6px; margin-bottom: 12px; font-size: 13px; }
.loop-toolbar { padding-top: 17px; padding-bottom: 17px; }
.follow-label { display: block; color: #6d8094; font-size: 12px; margin-top: 4px; }
.loop-controls, .range-controls, .operations { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.loop-controls label, .range-controls label { display: flex; align-items: center; gap: 8px; color: #606266; font-size: 13px; }
.loop-controls :deep(.el-input), .range-controls :deep(.el-input) { width: 106px; }
.loop-controls :deep(.el-button + .el-button), .range-controls :deep(.el-button + .el-button), .operations :deep(.el-button + .el-button) { margin-left: 0; }
.section-caption { font-size: 12px; margin: 8px 0 16px; line-height: 1.8; }
.range-controls { padding: 12px 14px; background: #f7f8fa; border-radius: 7px; margin-bottom: 16px; }
.trend-notice { font-size: 13px; padding: 12px 0; }
.trends { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; }
.trends article { min-width: 0; border: 1px solid #e5eaf0; border-radius: 8px; padding: 16px 18px 10px; background: #fbfcfe; }
.trends h3 { color: #425970; }
.trends svg { display: block; width: 100%; max-height: 210px; margin-top: 5px; }
.trends article p { color: #7c8b9a; font-size: 11px; text-align: right; }
.service-heading { margin: 22px 0 12px; flex-wrap: wrap; gap: 10px; }
.service-legend { display: flex; flex-wrap: wrap; gap: 6px; }
.service-legend span { font-size: 11px; border-radius: 4px; padding: 3px 8px; }
.service-strip { display: flex; flex-wrap: wrap; gap: 6px; }
.service-strip span { padding: 5px 9px; font-size: 11px; border-radius: 4px; font-variant-numeric: tabular-nums; }
.satisfied { background: #edf7f0; color: #35774c; }
.violated { background: #fff3e5; color: #956012; }
.unavailable { background: #eef1f5; color: #697888; }
.verification-section .section-heading { margin-bottom: 14px; }
.verification-section p, .verification-section a { font-size: 13px; line-height: 1.8; }
.verification-message, .operation-notice { background: #f5f8fc; border-radius: 6px; padding: 10px 12px; margin-top: 10px; }
.configuration-section summary { cursor: pointer; font-size: 14px; font-weight: 500; line-height: 1.8; }
.configuration-section p { margin-top: 14px; font-size: 12px; }
.configuration-section pre { padding: 16px; background: #f7f8fa; border-radius: 6px; margin-top: 12px; font-size: 12px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.empty-evaluation { text-align: center; padding: 36px 24px; font-size: 14px; }
@media (max-width: 1100px) {
  .sandbox-results { padding: 24px; }
  .results-header, .loop-toolbar, .verification-section .section-heading { align-items: flex-start; flex-direction: column; gap: 14px; }
  .execution-facts { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .sandbox-results { padding: 18px 12px 28px; }
  .sandbox-results h1 { font-size: 22px; }
  .results-card { padding: 18px 16px; }
  .trends { grid-template-columns: 1fr; }
  .loop-controls, .range-controls, .operations { width: 100%; }
  .loop-controls label { flex-basis: 100%; }
  .readonly-label { white-space: normal; }
}
</style>
