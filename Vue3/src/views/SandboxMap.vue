<template>
  <main class="sandbox-map-page">
    <h1>数据沙箱 · 逐轮地图</h1><router-link :to="`/sandbox/executions/${executionId}/results`">返回执行结果</router-link>
    <p>只展示本次执行的已记录事实；播放、暂停、选轮和地图操作均不推进或暂停后台仿真。虚线仅表示运输段端点关系，不是道路路线。</p>
    <el-alert v-if="inspection" :title="`需要人工检查：${inspection.reason}；任务编号：${inspection.jobId}`" type="warning" :closable="false" />
    <el-alert v-else-if="blocked" :title="`记录不能解释为地图：${error}；不会自动修复。`" type="error" :closable="false" />
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <p v-if="stale">信息尚未确认或已过期；保留的画面仍是其标注的旧轮次，不代表新轮次，也不代表后台实验失败。</p>
      <el-button :disabled="loading || selecting" @click="retry">重新读取</el-button>
      <template v-if="summary">
        <p>执行编号 {{ summary.executionId }} · {{ resultLabel(summary.resultKind) }} · 完整记录 {{ summary.progress.recordedLoops }} / {{ summary.progress.requestedLoops }} 轮</p>
        <p v-if="summary.status==='CANCELLED'">取消原因 {{ summary.failureCode || 'USER_CANCELLED' }}；仅展示已记录的完整前缀。</p>
        <p v-else-if="summary.failureCode">{{ summary.failureCode }}<template v-if="summary.failurePhase"> · {{ summary.failurePhase }}</template>；失败轮不计入完整记录。</p>
        <div v-if="recorded>0" class="navigation">
          <label>展示轮次 <el-input :model-value="requestedLoop" type="number" @input="requestedLoop=Number($event)" /></label>
          <el-button :disabled="selecting" @click="jump">查看所选轮次</el-button>
          <el-button :disabled="stale || selecting || !snapshot || snapshot.loopIndex===0" @click="step(-1)">上一轮</el-button>
          <el-button :disabled="stale || selecting || !snapshot || snapshot.loopIndex>=recorded-1" @click="step(1)">下一轮</el-button>
          <el-button :disabled="selecting" @click="latest">返回最新轮次</el-button>
          <span>{{ following?'跟随最新完整轮次':'已固定历史轮次' }}</span>
          <el-button :disabled="!playing && (stale || selecting || loading)" @click="playing ? playback.pause() : playback.play()">{{ playing?'暂停播放':'播放记录' }}</el-button>
          <label>每轮停留 <select :value="intervalMs" @change="playback.speed(Number(($event.target as HTMLSelectElement).value))"><option :value="500">0.5秒</option><option :value="1000">1秒</option><option :value="2000">2秒</option></select></label>
          <span v-if="waiting">等待下一轮完整记录</span>
        </div>
        <template v-if="snapshot">
          <h2>第{{ snapshot.loopIndex+1 }}轮结束状态 · {{ snapshot.simTime }}</h2>
          <p>loopIndex={{ snapshot.loopIndex }}；{{ snapshot.integrityScope }}；完整账本 {{ snapshot.ledgerIntegrity }}。</p>
          <el-checkbox v-model="showAllPois">显示全部场景POI（{{ context?.pois.length || 0 }}个）</el-checkbox>
          <p>当前显示{{ visiblePois.length }}个POI；停靠位置为关联POI，不是GPS轨迹。</p>
          <SandboxRecordedMap :key="executionId" :snapshot="snapshot" :pois="visiblePois" :selected-vehicle="selectedVehicleId" @select-vehicle="selectedVehicleId=$event" />
          <h2>车辆状态与详情</h2>
          <p>行驶中或位置未知的车辆仍保留在列表，不伪造地图点位。</p>
          <div class="vehicle-list"><button v-for="v in snapshot.projection.vehicles" :key="v.id" :class="{selected:v.id===selectedVehicleId}" @click="selectedVehicleId=v.id">{{ v.licensePlate || `车辆${v.id}` }} · {{ v.currentStatus || '未知状态' }} · {{ positionLabel(v.positionSource) }}</button></div>
          <article v-if="selectedVehicle && details">
            <h3>车辆{{ selectedVehicle.id }} · {{ selectedVehicle.licensePlate }}</h3>
            <p>状态 {{ selectedVehicle.currentStatus || '未知' }}；位置 {{ positionLabel(selectedVehicle.positionSource) }}；关联POI {{ selectedVehicle.currentPoiId ?? '未记录' }}（不一定是行驶中的当前位置）。</p>
            <p>记录载重 {{ value(selectedVehicle.currentLoad) }} 吨；记录容积 {{ value(selectedVehicle.currentVolumn) }} m³。</p>
            <h4>关联运输段（含该轮记录中的已完成段）</h4>
            <ul><li v-for="leg in details.legs" :key="leg.id">任务{{ leg.assignmentId }} · 段{{ leg.sequenceIndex }} · POI {{ leg.fromPoiId ?? '未知端点' }} → {{ leg.toPoiId ?? '未知端点' }} · {{ leg.progressStatus || '未知' }} · 已执行{{ value(leg.executedDistanceMeters) }} / {{ value(leg.distanceMeters) }}米</li></ul>
            <details><summary>同轮任务、货物、事件和驾驶进度记录</summary><pre>{{ JSON.stringify(details,null,2) }}</pre></details>
          </article>
          <details><summary>同轮全部事件与冻结天气配置</summary><pre>{{ JSON.stringify({events:snapshot.projection.events,weather:snapshot.projection.weather},null,2) }}</pre></details>
          <SandboxEvaluationMetrics :snapshot="snapshot.evaluation" />
        </template><p v-else-if="recorded===0">暂无逐轮地图记录；运行前初态不是第1轮。</p>
      </template>
    </template>
  </main>
</template>
<script setup lang="ts">
import {computed,ref,watch,onMounted,onBeforeUnmount,provide} from 'vue'
import {useRoute} from 'vue-router'
import {sandboxApi} from '../api/sandbox'
import {createRecordedMapController,involvedPois,vehicleDetails} from '../sandbox/recordedMap'
import {createRecordedPlayback} from '../sandbox/recordedPlayback'
import {resultLabel} from '../sandbox/results'
import {recordedMapFactoryKey} from '../sandbox/recordedMapRenderer'
import {createRecordedAmapFactory} from '../sandbox/recordedAmap'
import SandboxRecordedMap from '../components/sandbox/SandboxRecordedMap.vue'
import SandboxEvaluationMetrics from '../components/sandbox/SandboxEvaluationMetrics.vue'
const route=useRoute(),model=createRecordedMapController(sandboxApi,String(route.params.id||''))
provide(recordedMapFactoryKey,createRecordedAmapFactory())
const {executionId,summary,context,snapshot,stale,loading,selecting,following,error,blocked,inspection}=model
const playback=createRecordedPlayback(model),{playing,waiting,intervalMs}=playback
const requestedLoop=ref(1),selectedVehicleId=ref<number|null>(null),showAllPois=ref(false)
const recorded=computed(()=>summary.value?.progress.recordedLoops||0)
const selectedVehicle=computed(()=>snapshot.value?.projection.vehicles.find(v=>v.id===selectedVehicleId.value)||null)
const details=computed(()=>snapshot.value&&selectedVehicle.value?vehicleDetails(snapshot.value,selectedVehicle.value):null)
const visiblePois=computed(()=>snapshot.value?(showAllPois.value?(context.value?.pois||[]):involvedPois(snapshot.value)):[])
let disposed=false,pollTimer:ReturnType<typeof setTimeout>|undefined
function stopTimer(){if(pollTimer)clearTimeout(pollTimer);pollTimer=undefined}
function schedule(){stopTimer();if(!disposed&&!blocked.value&&!inspection.value&&!model.isTerminal())pollTimer=setTimeout(refresh,stale.value?5000:2000)}
async function refresh(){await model.refresh();schedule()}
async function retry(){await model.refresh();if(model.snapshot.value&&model.stale.value&&!blocked.value&&!inspection.value)await model.select(model.snapshot.value.loopIndex,false);schedule()}
async function jump(){playback.pause();await model.select(requestedLoop.value-1)}
async function step(delta:number){playback.pause();if(snapshot.value)await model.select(snapshot.value.loopIndex+delta)}
async function latest(){playback.pause();await model.latest()}
function positionLabel(source:string){return source==='POI_LINK'?'关联POI停靠':source==='IN_TRANSIT'?'运输中（无精确坐标）':'位置未知'}
function value(v:unknown){return v===null||v===undefined?'未记录':String(v)}
watch(snapshot,s=>{if(s){requestedLoop.value=s.loopIndex+1;if(selectedVehicleId.value!==null&&!s.projection.vehicles.some(v=>v.id===selectedVehicleId.value))selectedVehicleId.value=null}})
watch([stale,blocked,inspection],()=>{if(stale.value||blocked.value||inspection.value)playback.pause()})
watch(()=>String(route.params.id||''),async id=>{playback.pause();stopTimer();selectedVehicleId.value=null;showAllPois.value=false;requestedLoop.value=1;await model.switchExecution(id);schedule()})
onMounted(refresh);onBeforeUnmount(()=>{disposed=true;stopTimer();playback.dispose();model.dispose()})
</script>
<style scoped>
.sandbox-map-page{padding:24px;max-width:1250px;margin:auto}.sandbox-map-page p,.sandbox-map-page a{overflow-wrap:anywhere}.navigation{display:flex;flex-wrap:wrap;align-items:center;gap:10px;margin:16px 0}.navigation .el-input{width:100px}.vehicle-list{display:flex;flex-wrap:wrap;gap:8px}.vehicle-list button{border:1px solid #bac8d7;background:#f4f7fa;padding:8px;border-radius:6px;cursor:pointer}.vehicle-list button.selected{border-color:#b64214;background:#fff3ec}article{margin-top:16px;padding:16px;background:#f4f7fa;border-radius:8px}pre{white-space:pre-wrap;overflow-wrap:anywhere}
</style>
