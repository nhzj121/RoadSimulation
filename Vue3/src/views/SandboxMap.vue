<template>
  <main v-show="view !== 'evaluation'" class="sandbox-map-page">
    <aside class="sandbox-map-controls" aria-label="沙箱回放控制">
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
          <el-button :disabled="!!roadTimeline || roadPreparing || (!playing && (stale || selecting || loading))" @click="playing ? playback.pause() : playback.play()">{{ playing?'暂停播放':'播放记录' }}</el-button>
          <label>每轮停留 <select :value="intervalMs" @change="playback.speed(Number(($event.target as HTMLSelectElement).value))"><option :value="500">0.5秒</option><option :value="1000">1秒</option><option :value="2000">2秒</option></select></label>
          <span v-if="waiting">等待下一轮完整记录</span>
        </div>
        <div v-if="recorded>0 && summary.status !== 'INTERRUPTED'" class="navigation" aria-label="道路回放">
          <el-button v-if="!roadTimeline || roadError || roadTimeline.missingRoads.length" :disabled="roadPreparing || stale" @click="prepareRoadReplay">{{ roadActive?'重试读取道路记录':'准备高德道路回放' }}</el-button>
          <span v-if="roadPreparing">{{ roadProgress || '正在读取回放记录' }}</span>
          <span v-if="roadError" role="alert">{{ roadError }}</span>
          <template v-if="roadTimeline">
            <el-button @click="roadPlaying ? roadReplay.pause() : roadReplay.play()">{{ roadPlaying?'暂停道路动画':'播放道路动画' }}</el-button>
            <el-button @click="roadReplay.reset">返回逐轮地图</el-button>
            <label class="road-time">展示时间 <el-slider :min="0" :max="roadDuration" :step="0.1"
              :model-value="roadSeconds" @input="roadReplay.seek(Number($event))" /></label>
            <label>播放倍速 <select v-model.number="roadSpeed"><option :value="1">1×</option><option :value="4">4×</option><option :value="16">16×</option><option :value="60">60×</option></select></label>
            <span>{{ roadSeconds.toFixed(1) }} / {{ roadDuration.toFixed(1) }} 秒 · {{ roadTerminal && roadSeconds>=roadDuration?'到达展示终点':roadWaiting?'等待下一段完整记录':'道路动画' }}</span>
            <span v-if="roadTerminal && roadSeconds<roadDuration">后台执行已结束，动画继续播放已记录过程</span>
            <span v-if="roadTimeline.missingRoads.length">道路不可用 {{ roadTimeline.missingRoads.length }} 段</span>
            <span v-if="roadTimeline.disconnectedRoads.length">路线接续缺口 {{ roadTimeline.disconnectedRoads.length }} 处</span>
          </template>
        </div>
      </template>
    </template>
      <SimulationDataLeftPanel v-if="view === 'data'" :feed-state="evaluationFeed" :trend-snapshots="evaluationTrend"
        @open-full="emit('open-evaluation')" />
    </aside>
    <template v-if="snapshot && !inspection && !blocked">
        <section class="sandbox-map-surface" aria-label="地图工作区">
          <h2>第{{ snapshot.loopIndex+1 }}轮结束状态 · {{ snapshot.simTime }}</h2>
          <p>loopIndex={{ snapshot.loopIndex }}；{{ snapshot.integrityScope }}；完整账本 {{ snapshot.ledgerIntegrity }}。</p>
          <el-checkbox v-model="showAllPois">显示全部场景POI（{{ context?.pois.length || 0 }}个）</el-checkbox>
          <p>当前显示{{ visiblePois.length }}个POI；停靠位置为关联POI，不是GPS轨迹。</p>
          <div class="sandbox-map-layer">
            <SandboxRecordedMap :key="executionId" :snapshot="snapshot" :pois="visiblePois" :selected-vehicle="selectedVehicleId"
              :replay-frame="roadFrame" :replay-roads="roadRoutes" @select-vehicle="selectedVehicleId=$event" />
            <section v-if="workspaceVehicle" class="sandbox-vehicle-popup" aria-label="车辆详情">
              <header><strong>{{ workspaceVehicle.title }}</strong><button type="button" aria-label="关闭车辆详情" @click="selectedVehicleId=null">×</button></header>
              <VehicleWorkspaceDetail :status="workspaceVehicle.status" :action="workspaceVehicle.action"
                :task="workspaceVehicle.task" :load="workspaceVehicle.load" />
            </section>
          </div>
        </section>
        <aside class="sandbox-map-inspector" aria-label="沙箱记录详情">
          <SimulationDataRightPanel v-if="view === 'data'" recorded :feed-state="evaluationFeed"
            :visible-vehicles="displayedMarkers" @open-full="emit('open-evaluation')" />
          <template v-if="roadFrame">
            <h2>道路回放车辆状态</h2>
            <p>{{ roadCounts }}。下方记录详情与评价对应第 {{ snapshot.loopIndex+1 }} 轮。</p>
            <p v-if="roadSelected">车辆 {{ roadSelected.licensePlate || roadSelected.id }} · {{ roadSelected.currentStatus || '未记录' }} · 展示载重 {{ value(roadSelected.currentLoad) }} 吨</p>
          </template>
          <h2>车辆状态与详情</h2>
          <p>行驶中或位置未知的车辆仍保留在列表，不伪造地图点位。</p>
          <div class="vehicle-list"><button v-for="v in roadFrame ?? snapshot.projection.vehicles" :key="v.id" :class="{selected:v.id===selectedVehicleId}" @click="selectedVehicleId=v.id">{{ v.licensePlate || `车辆${v.id}` }} · {{ vehicleStatusPresentation(v.currentStatus).text }} · {{ positionLabel(v.positionSource) }}</button></div>
          <article v-if="selectedVehicle && details">
            <h3>车辆{{ selectedVehicle.id }} · {{ selectedVehicle.licensePlate }}</h3>
            <p>状态 {{ selectedVehicle.currentStatus || '未知' }}；位置 {{ positionLabel(selectedVehicle.positionSource) }}；关联POI {{ selectedVehicle.currentPoiId ?? '未记录' }}（不一定是行驶中的当前位置）。</p>
            <p>记录载重 {{ value(selectedVehicle.currentLoad) }} 吨；记录容积 {{ value(selectedVehicle.currentVolumn) }} m³。</p>
            <h4>关联运输段（含该轮记录中的已完成段）</h4>
            <ul><li v-for="leg in details.legs" :key="leg.id">任务{{ leg.assignmentId }} · 段{{ leg.sequenceIndex }} · POI {{ leg.fromPoiId ?? '未知端点' }} → {{ leg.toPoiId ?? '未知端点' }} · {{ leg.progressStatus || '未知' }} · 已执行{{ value(leg.executedDistanceMeters) }} / {{ value(leg.distanceMeters) }}米</li></ul>
            <details><summary>同轮任务、货物、事件和驾驶进度记录</summary><pre>{{ JSON.stringify(details,null,2) }}</pre></details>
          </article>
          <details><summary>同轮全部事件与冻结天气配置</summary><pre>{{ JSON.stringify({events:snapshot.projection.events,weather:snapshot.projection.weather},null,2) }}</pre></details>
          <SandboxEvaluationMetrics v-if="view === 'operation'" :snapshot="snapshot.evaluation" />
        </aside>
        <SimulationDataBottomPanel v-if="view === 'data'" class="sandbox-map-bottom" :state="recordedSession"
          :feed-state="evaluationFeed" :trend-snapshots="evaluationTrend" />
    </template><p v-else-if="summary && recorded===0" class="sandbox-map-surface">暂无逐轮地图记录；运行前初态不是第1轮。</p>
  </main>
  <EvaluationDashboard v-if="view === 'evaluation'" :key="executionId" :evaluation-source="evaluationSource"
    :shared-feed-state="evaluationFeed" :visible-vehicles="displayedMarkers" @back="emit('back')" />
</template>
<script setup lang="ts">
import {computed,defineAsyncComponent,ref,shallowRef,watch,onMounted,onBeforeUnmount,provide} from 'vue'
import {useRoute} from 'vue-router'
import {sandboxApi} from '../api/sandbox'
import {createRecordedMapController,involvedPois,vehicleDetails} from '../sandbox/recordedMap'
import {createRecordedPlayback} from '../sandbox/recordedPlayback'
import {createRoadReplayPlayback} from '../sandbox/roadReplayPlayback'
import {resultLabel} from '../sandbox/results'
import {recordedMapFactoryKey} from '../sandbox/recordedMapRenderer'
import {createRecordedAmapFactory} from '../sandbox/recordedAmap'
import SandboxRecordedMap from '../components/sandbox/SandboxRecordedMap.vue'
import SandboxEvaluationMetrics from '../components/sandbox/SandboxEvaluationMetrics.vue'
import SimulationDataLeftPanel from '../components/simulation-data/SimulationDataLeftPanel.vue'
import SimulationDataRightPanel from '../components/simulation-data/SimulationDataRightPanel.vue'
import SimulationDataBottomPanel from '../components/simulation-data/SimulationDataBottomPanel.vue'
import VehicleWorkspaceDetail from '../components/VehicleWorkspaceDetail.vue'
import { presentSandboxWorkspaceVehicle } from '../sandbox/workspaceVehicleDetail'
import { vehicleStatusPresentation } from '../utils/vehicleStatusPresentation'
import { createSandboxWorkspaceEvaluationSource, readSandboxEvaluationRange } from '../sandbox/workspaceEvaluation'
import { createSessionRecords } from '../utils/simulationSessionRecords'
import type { SimulationEvaluationFeedState } from '../composables/useSimulationEvaluationFeed'
import type { EvaluationSnapshot } from '../types/evaluation'
import type { RecordedVehicle, RecordedPositionSource } from '../types/sandbox-map'
import type { VisibleVehicleDisplay } from '../utils/simulationWorkspacePresentation'

const props=withDefaults(defineProps<{view?:'operation'|'data'|'evaluation'}>(),{view:'operation'})
const emit=defineEmits<{(event:'open-evaluation'):void;(event:'back'):void}>()
const EvaluationDashboard=defineAsyncComponent(()=>import('../components/EvaluationDashboard.vue'))
const route=useRoute(),model=createRecordedMapController(sandboxApi,String(route.params.id||''))
provide(recordedMapFactoryKey,createRecordedAmapFactory())
const {executionId,summary,context,snapshot,stale,loading,selecting,following,error,blocked,inspection}=model
const playback=createRecordedPlayback(model),{playing,waiting,intervalMs}=playback
const roadReplay=createRoadReplayPlayback(sandboxApi)
const {frame:roadFrame,roads:roadRoutes,timeline:roadTimeline,seconds:roadSeconds,duration:roadDuration,
  speed:roadSpeed,playing:roadPlaying,preparing:roadPreparing,progress:roadProgress,error:roadError,
  active:roadActive,terminal:roadTerminal,waiting:roadWaiting}=roadReplay
const requestedLoop=ref(1),selectedVehicleId=ref<number|null>(null),showAllPois=ref(false)
const roadSelected=computed(()=>roadFrame.value?.find(vehicle=>vehicle.id===selectedVehicleId.value))
const roadCounts=computed(()=>{
  const counts=new Map<string,number>()
  for(const vehicle of roadFrame.value??[]){
    const status=vehicle.currentStatus??'未知'
    counts.set(status,(counts.get(status)??0)+1)
  }
  return [...counts].map(([status,count])=>`${status} ${count} 辆`).join(' · ')
})
const recorded=computed(()=>summary.value?.progress.recordedLoops||0)
const selectedVehicle=computed(()=>snapshot.value?.projection.vehicles.find(v=>v.id===selectedVehicleId.value)||null)
const details=computed(()=>snapshot.value&&selectedVehicle.value?vehicleDetails(snapshot.value,selectedVehicle.value):null)
const workspaceVehicle=computed(()=>snapshot.value&&selectedVehicleId.value!==null
  ?presentSandboxWorkspaceVehicle(snapshot.value,selectedVehicleId.value,roadFrame.value,roadReplay.replayRecord.value):null)
const visiblePois=computed(()=>snapshot.value?(showAllPois.value?(context.value?.pois||[]):involvedPois(snapshot.value)):[])
const evaluationSource=computed(()=>createSandboxWorkspaceEvaluationSource(sandboxApi,executionId.value))
const evaluationFeed=computed<SimulationEvaluationFeedState>(()=>({
  snapshot:snapshot.value?.evaluation??null, initialLoading:loading.value&&!snapshot.value,
  waitingForSnapshot:!snapshot.value, polling:loading.value, networkError:error.value,
}))
const evaluationTrend=shallowRef<EvaluationSnapshot[]>([])
let trendCache:{executionId:string;snapshots:EvaluationSnapshot[]}|undefined
watch([snapshot,()=>props.view],async ([selected,view],_,onCleanup)=>{
  if(!selected||view!=='data'){evaluationTrend.value=[];return}
  const request=new AbortController()
  onCleanup(()=>request.abort())
  const id=selected.executionId, end=selected.loopIndex, start=Math.max(0,end-49)
  const cache=trendCache?.executionId===id?trendCache.snapshots:[]
  const retained=cache.filter(item=>item.loopIndex>=start&&item.loopIndex<=end)
  const reusable=retained[0]?.loopIndex===start?retained:[]
  evaluationTrend.value=reusable
  try{
    const additions=await readSandboxEvaluationRange(sandboxApi,id,start+reusable.length,end-1,request.signal)
    if(request.signal.aborted)return
    const combined=[...reusable.filter(item=>item.loopIndex!==end),...additions,selected.evaluation]
    trendCache={executionId:id,snapshots:combined}
    evaluationTrend.value=combined
  }catch{
    if(!request.signal.aborted)evaluationTrend.value=[...reusable.filter(item=>item.loopIndex!==end),selected.evaluation]
  }
})
const displayedMarkers=shallowRef<VisibleVehicleDisplay[]>([])
let markerStatusKey=''
watch([snapshot,roadFrame],([selected,frame])=>{
  const candidates:ReadonlyArray<RecordedVehicle<RecordedPositionSource|'GAODE_REPLAY'>>=frame??selected?.projection.vehicles??[]
  const vehicles=candidates.filter(vehicle=>vehicle.position
    && (frame!==null||vehicle.positionSource==='POI_LINK'))
  const key=JSON.stringify([selected?.executionId,vehicles.map(vehicle=>[vehicle.id,vehicle.currentStatus])])
  if(key===markerStatusKey)return
  markerStatusKey=key
  displayedMarkers.value=vehicles.map(vehicle=>({id:vehicle.id,status:vehicle.currentStatus??undefined}))
})
const recordedSession=computed(()=>{
  const state=createSessionRecords()
  const selected=snapshot.value
  if(!selected)return state
  state.health.transport.phase=stale.value?'failed':'ready'
  state.health.transport.hasData=true
  state.health.evaluation.phase=stale.value?'failed':'ready'
  state.health.evaluation.hasData=true
  state.records=selected.projection.events.slice(-50).map((event,index)=>({
    id:index+1,category:'random',source:'transport',title:String(event.eventType??'运输事件'),
    detail:String(event.description??event.status??''),vehicleText:event.vehicleId==null?'':`车辆 ${event.vehicleId}`,
    eventId:String(event.id),observedAt:selected.simTime,eventTime:typeof event.startTime==='string'?event.startTime:null,
    timeLabel:'已提交轮次 · 仿真时间',
  }))
  return state
})
let disposed=false,pollTimer:ReturnType<typeof setTimeout>|undefined
function stopTimer(){if(pollTimer)clearTimeout(pollTimer);pollTimer=undefined}
function schedule(){stopTimer();if(!disposed&&!blocked.value&&!inspection.value&&!model.isTerminal())pollTimer=setTimeout(refresh,stale.value?5000:2000)}
async function refresh(){await model.refresh();schedule()}
async function retry(){await model.refresh();if(model.snapshot.value&&model.stale.value&&!blocked.value&&!inspection.value)await model.select(model.snapshot.value.loopIndex,false);schedule()}
async function prepareRoadReplay(){
  if(!summary.value)return
  playback.pause()
  await (roadActive.value?roadReplay.refresh(summary.value):roadReplay.prepare(summary.value))
}
async function jump(){playback.pause();roadReplay.reset();await model.select(requestedLoop.value-1)}
async function step(delta:number){playback.pause();roadReplay.reset();if(snapshot.value)await model.select(snapshot.value.loopIndex+delta)}
async function latest(){playback.pause();roadReplay.reset();await model.latest()}
function positionLabel(source:string){return source==='GAODE_REPLAY'?'高德道路回放':source==='POI_LINK'?'关联POI停靠':source==='IN_TRANSIT'?'运输中（无精确坐标）':'位置未知'}
function value(v:unknown){return v===null||v===undefined?'未记录':String(v)}
watch(snapshot,s=>{if(s){requestedLoop.value=s.loopIndex+1;if(selectedVehicleId.value!==null&&!s.projection.vehicles.some(v=>v.id===selectedVehicleId.value))selectedVehicleId.value=null}})
watch([summary,stale,blocked,inspection],([next,isStale,isBlocked,needsInspection])=>{
  if(isBlocked||needsInspection){playback.pause();roadReplay.reset();return}
  if(isStale){playback.pause();return}
  if(next&&roadActive.value)void roadReplay.refresh(next)
})
watch(()=>String(route.params.id||''),async id=>{playback.pause();roadReplay.reset();stopTimer();selectedVehicleId.value=null;showAllPois.value=false;requestedLoop.value=1;await model.switchExecution(id);schedule()})
onMounted(refresh);onBeforeUnmount(()=>{disposed=true;stopTimer();roadReplay.reset();playback.dispose();model.dispose()})
</script>
<style scoped>
.sandbox-map-page{display:grid;grid-template-columns: minmax(220px, 280px) minmax(0, 1fr) minmax(220px, 340px);width:100%;min-height:0;flex:1;padding:0;max-width:none;margin:0}
.sandbox-map-controls,.sandbox-map-surface,.sandbox-map-inspector{min-width:0;padding:16px}.sandbox-map-controls,.sandbox-map-inspector{background:#f7f8fa}
.sandbox-map-controls{border-right:1px solid #e6e6e6}.sandbox-map-inspector{border-left:1px solid #e6e6e6}
.sandbox-map-page p,.sandbox-map-page a{overflow-wrap:anywhere}.navigation{display:flex;flex-wrap:wrap;align-items:center;gap:10px;margin:16px 0}.navigation .el-input{width:100px}.vehicle-list{display:flex;flex-wrap:wrap;gap:8px}.vehicle-list button{border:1px solid #bac8d7;background:#f4f7fa;padding:8px;border-radius:6px;cursor:pointer}.vehicle-list button.selected{border-color:#b64214;background:#fff3ec}article{margin-top:16px;padding:16px;background:#f4f7fa;border-radius:8px}pre{white-space:pre-wrap;overflow-wrap:anywhere}
.sandbox-map-surface :deep(.map-canvas){height:clamp(440px, 70vh, 1100px)}
.sandbox-map-bottom{grid-column:1/-1}
.sandbox-map-layer{position:relative}.sandbox-vehicle-popup{position:absolute;z-index:10;top:90px;left:12px;width:min(320px,calc(100% - 24px));max-height:60vh;overflow:auto;padding:12px;background:#fff;border:1px solid #dfe7f1;border-radius:8px;box-shadow:0 8px 24px #26344533;box-sizing:border-box}
.sandbox-vehicle-popup header{display:flex;justify-content:space-between;align-items:center;gap:12px}.sandbox-vehicle-popup header button{border:0;background:none;cursor:pointer;font-size:20px}
@media (max-width:1100px){.sandbox-map-page{grid-template-columns:minmax(210px,250px) minmax(0,1fr)}.sandbox-map-inspector{grid-column:2;border-left:0}}
@media (max-width:680px){.sandbox-map-page{grid-template-columns:minmax(0,1fr)}.sandbox-map-inspector{grid-column:auto}.sandbox-map-surface :deep(.map-canvas){height:52vh}}
.road-time{display:flex;align-items:center;gap:12px}.road-time .el-slider{width:180px}
</style>
