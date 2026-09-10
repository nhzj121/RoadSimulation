<template>
  <!-- Phase 6C：独立评价主视图覆盖地图工作区，但保留全局导航栏。 -->
  <section class="evaluation-dashboard-shell" aria-label="实时评价看板">
    <div class="evaluation-dashboard">
      <header class="evaluation-topbar">
        <div>
          <div class="evaluation-eyebrow">REAL-TIME EVALUATION SNAPSHOT</div>
          <h2>实时运输评价</h2>
          <p>数据来自每轮业务推进结束后生成的同版本后端快照</p>
        </div>
        <div class="evaluation-topbar-actions">
          <span class="evaluation-connection" :class="connectionClass">
            <i></i>{{ connectionText }}
          </span>
          <ElButton type="primary" plain @click="emit('back')">返回地图</ElButton>
        </div>
      </header>

      <div v-if="networkError" class="evaluation-alert evaluation-alert--error">
        {{ networkError }}；已保留最近一次成功快照，下一轮将自动重试。
      </div>

      <div v-if="waitingForSnapshot" class="evaluation-empty-state">
        <div class="evaluation-empty-icon">∿</div>
        <h3>等待首个评价快照</h3>
        <p>仿真完成至少一个业务循环后，此页面会自动显示并持续更新。</p>
      </div>

      <template v-else-if="snapshot">
        <section class="evaluation-run-summary">
          <div class="evaluation-run-heading">
            <div>
              <span class="evaluation-label">运行标识</span>
              <strong :title="snapshot.simulationRunId">{{ snapshot.simulationRunId }}</strong>
            </div>
            <span class="evaluation-status-badge" :class="snapshotStatusClass">
              {{ snapshotStatusText }}
            </span>
          </div>
          <div class="evaluation-meta-grid">
            <div><span>运行类型</span><strong>{{ runKindText }}</strong></div>
            <div><span>循环序号</span><strong>{{ snapshot.loopIndex }}</strong></div>
            <div><span>快照版本</span><strong>{{ snapshot.snapshotRevision }}</strong></div>
            <div><span>仿真时间</span><strong>{{ formattedSimTime }}</strong></div>
            <div><span>已处理任务</span><strong>{{ snapshot.processedAssignmentCount }}</strong></div>
            <div><span>失败任务</span><strong>{{ snapshot.failedAssignmentCount }}</strong></div>
          </div>
          <div class="evaluation-thresholds">
            <span>低载阈值 {{ formatRatio(snapshot.thresholds.lowLoadRatioThreshold) }}</span>
            <span>满载阈值 {{ formatRatio(snapshot.thresholds.fullLoadRatioThreshold) }}</span>
            <span>最大服务等待 {{ formatPlainNumber(snapshot.thresholds.maxServiceWaitSeconds, 0) }} s</span>
            <span>契约 v{{ snapshot.contractVersion }}</span>
          </div>
          <div
            v-if="evaluationWarnings.length"
            class="evaluation-warning-stack"
            aria-live="polite"
          >
            <!-- Phase 7E-R：机器码、用户提示和事实详情分层展示，避免一行红字承担全部诊断职责。 -->
            <article
              v-for="warning in evaluationWarnings"
              :key="warning.key"
              class="evaluation-warning"
              :class="`evaluation-warning--${warning.level}`"
            >
              <div class="evaluation-warning-heading">
                <span>{{ warningLevelText(warning.level) }}</span>
                <strong>{{ warning.title }}</strong>
                <code v-if="warning.code">{{ warning.code }}</code>
              </div>
              <p>{{ warning.message }}</p>
              <details v-if="warning.details.length" class="evaluation-warning-details">
                <summary>查看详细信息（{{ warning.details.length }}）</summary>
                <ul>
                  <li
                    v-for="(detail, detailIndex) in warning.details"
                    :key="`${warning.key}-${detailIndex}`"
                  >
                    {{ detail }}
                  </li>
                </ul>
              </details>
            </article>
          </div>
        </section>

        <section class="evaluation-section">
          <div class="evaluation-section-heading">
            <div>
              <h3>全局目标与服务约束</h3>
              <p>仅展示后端已判定可用的值；缺失事实不会被显示为 0。</p>
            </div>
          </div>
          <div class="evaluation-objective-grid">
            <article
              v-for="metricId in globalMetricIds"
              :key="metricId"
              class="evaluation-objective-card"
            >
              <span>{{ metricById(metricId)?.displayName || metricId }}</span>
              <strong>{{ formatMetric(metricById(metricId)) }}</strong>
              <small
                class="evaluation-card-state"
                :class="metricStatusClass(metricById(metricId))"
                :title="metricById(metricId)?.reason || ''"
              >
                {{ metricById(metricId)?.reason || metricStatusText(metricById(metricId)) }}
              </small>
            </article>
          </div>
        </section>

        <section
          v-for="group in coreMetricGroups"
          :key="group.key"
          class="evaluation-section"
        >
          <div class="evaluation-section-heading">
            <div>
              <h3>{{ group.title }}</h3>
              <p>{{ group.description }}</p>
            </div>
          </div>
          <div class="evaluation-metric-table">
            <div class="evaluation-metric-row evaluation-metric-row--head">
              <span>指标</span><span>当前值</span><span>状态/说明</span>
            </div>
            <div
              v-for="metricId in group.metricIds"
              :key="metricId"
              class="evaluation-metric-row"
              :class="metricRowClass(metricById(metricId))"
            >
              <span class="evaluation-metric-name">{{ metricById(metricId)?.displayName || metricId }}</span>
              <strong class="evaluation-metric-value">{{ formatMetric(metricById(metricId)) }}</strong>
              <div class="evaluation-metric-state">
                <span
                  class="evaluation-metric-status-pill"
                  :class="metricStatusClass(metricById(metricId))"
                >
                  {{ metricStatusText(metricById(metricId)) }}
                </span>
                <details
                  v-if="hasLongMetricReason(metricById(metricId))"
                  class="evaluation-metric-reason-details"
                >
                  <summary>查看说明</summary>
                  <small>{{ metricById(metricId)?.reason }}</small>
                </details>
                <small v-else-if="metricById(metricId)?.reason" class="evaluation-metric-reason">
                  {{ metricById(metricId)?.reason }}
                </small>
              </div>
            </div>
          </div>
        </section>

        <section class="evaluation-section evaluation-deferred-section">
          <div class="evaluation-section-heading">
            <div>
              <h3>待接入事实维度</h3>
              <p>本阶段只诚实展示契约状态，不用前端估算替代后续模型。</p>
            </div>
          </div>
          <div class="evaluation-deferred-grid">
            <div v-for="summary in deferredSummaries" :key="summary.key" class="evaluation-deferred-card">
              <span>{{ summary.title }}</span>
              <strong>{{ summary.available }}/{{ summary.total }} 项可用</strong>
              <div class="evaluation-deferred-counts">
                <span v-if="summary.pending">{{ summary.pending }} 项待接入</span>
                <span v-if="summary.unsupported">{{ summary.unsupported }} 项不支持</span>
                <span v-if="summary.invalid">{{ summary.invalid }} 项无效</span>
              </div>
              <small :title="summary.reason">{{ summary.reason }}</small>
            </div>
          </div>
        </section>

        <footer class="evaluation-footer">
          <span>每 1 s 自动读取最新完整快照</span>
          <span>最近成功读取：{{ lastSuccessfulReadText }}</span>
        </footer>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { ElButton } from 'element-plus';
import {
  fetchLatestEvaluationSnapshot,
  isEvaluationRequestCanceled
} from '../api/evaluationApi';
import { shouldAcceptEvaluationSnapshot } from '../utils/evaluationSnapshotPolicy';
import type {
  EvaluationMetricValue,
  EvaluationMetricValueStatus,
  EvaluationSnapshot
} from '../types/evaluation';

type EvaluationWarningLevel = 'error' | 'warning';

interface EvaluationWarning {
  key: string;
  level: EvaluationWarningLevel;
  title: string;
  message: string;
  code: string | null;
  details: string[];
}

/** Phase 6C：由父级切回地图；评价页本身不控制仿真生命周期。 */
const emit = defineEmits<{ (event: 'back'): void }>();

const POLL_INTERVAL_MS = 1000;
const snapshot = ref<EvaluationSnapshot | null>(null);
const waitingForSnapshot = ref(true);
const networkError = ref('');
const polling = ref(false);
const lastSuccessfulReadAt = ref<Date | null>(null);
let pollingTimer: number | null = null;
let activeRequest: AbortController | null = null;

/** Phase 6C：本阶段仅展示计划确认的核心项，完整 69 项表留到 Phase 9。 */
const globalMetricIds = Object.freeze([
  'emptyMileageRatio',
  'capacityWasteRatio',
  'unmetDemandRatio',
  'carbonIntensity',
  'waitingServiceCompliant'
]);

const coreMetricGroups = Object.freeze([
  {
    key: 'vehicle',
    title: '车辆运行',
    description: '车辆状态与后端逐路段实际执行里程。',
    metricIds: [
      'vehicleTotalCount',
      'vehicleAvailableCount',
      'vehicleInTransportCount',
      'vehicleTotalExecutedDistanceKm',
      'vehicleEmptyExecutedDistanceKm',
      'vehicleDistanceWeightedLoadRatio'
    ]
  },
  {
    key: 'cargo',
    title: '货物履约',
    description: '当前运行内需求、在途、交付与实际吨公里。',
    metricIds: [
      'cargoRequiredTonnes',
      'cargoUnassignedTonnes',
      'cargoInTransitTonnes',
      'cargoDeliveredTonnes',
      'cargoDeliveryAchievementRatio',
      'cargoExecutedTonneKm'
    ]
  },
  {
    key: 'task',
    title: '运输任务',
    description: '任务生命周期、装载水平与实际执行结果。',
    metricIds: [
      'taskTotalCount',
      'taskAssignedPendingCount',
      'taskInProgressCount',
      'taskCompletedCount',
      'taskCompletionRatio',
      'taskAverageLoadRatio',
      'taskEmptyPickupDistanceKm',
      'taskTonneKm'
    ]
  },
  {
    key: 'environment',
    title: '外部环境',
    description: '可复现场景、后端实际推进因子及节点装卸观察事实。',
    metricIds: [
      'environmentNetworkAverageSpeedKph',
      'environmentCongestionIndex',
      'environmentRoadPassabilityRatio',
      'environmentClosedRoadCount',
      'environmentAbnormalEventCount',
      'environmentWeatherRiskLevel',
      'environmentTravelTimeFactor',
      'environmentNodeAverageServiceSeconds',
      'environmentNodeThroughputTonnes',
      'environmentRoadRealtimeSpeedKph',
      'environmentEnergyFactor',
      'environmentDistanceFactor',
      'environmentNodeQueueLength'
    ]
  }
]);

/** Phase 7E：从同一快照提取 INVALID 指标，不在前端推断或改写后端诊断。 */
const invalidMetricDiagnostics = computed(() =>
  Object.values(snapshot.value?.metrics || {})
    .filter(metric => metric.status === 'INVALID')
);

/** Phase 7E-R：机器错误码保留原样，同时提供稳定中文解释；未知码也必须如实显示。 */
const evaluationWarnings = computed<EvaluationWarning[]>(() => {
  const current = snapshot.value;
  if (!current) return [];

  const warningCopy: Record<string, Omit<EvaluationWarning, 'key' | 'code' | 'details'>> = {
    EVALUATION_INVALID_FACT: {
      level: 'error',
      title: '评价事实无效',
      message: '部分指标依赖的事实违反可信性约束。本轮运输不一定失败，但对应评价值不能继续使用。'
    },
    EVALUATION_READY_METRIC_UNAVAILABLE: {
      level: 'error',
      title: '已就绪指标缺失',
      message: '按契约应当生成的指标没有形成有效值，需要检查本轮事实采集或指标计算。'
    },
    EVALUATION_FACT_COLLECTION_FAILED: {
      level: 'error',
      title: '评价事实采集失败',
      message: '评价层未能取得一致的本轮事实，当前快照不应作为运行结果依据。'
    },
    TRANSPORT_PROGRESS_ASSIGNMENT_FAILED: {
      level: 'warning',
      title: '部分运输任务推进失败',
      message: '本轮其余任务仍继续推进，请结合失败任务数和后端日志定位具体任务。'
    }
  };

  const warnings = current.errorCodes.map((code): EvaluationWarning => {
    const known = warningCopy[code];
    const details = code === 'EVALUATION_INVALID_FACT'
      ? invalidMetricDiagnostics.value.map(metric =>
        `${metric.displayName}：${metric.reason || '评价事实无效'}`)
      : [];
    return {
      key: code,
      code,
      level: known?.level || 'warning',
      title: known?.title || '未分类运行警告',
      message: known?.message || '后端返回了尚未配置中文解释的机器错误码，请结合运行日志检查。',
      details
    };
  });

  // Phase 7E-R：防御旧快照未携带机器码的情况；只补展示，不在前端改写后端快照。
  if (invalidMetricDiagnostics.value.length
      && !current.errorCodes.includes('EVALUATION_INVALID_FACT')) {
    warnings.push({
      key: 'INVALID_METRIC_WITHOUT_ERROR_CODE',
      code: null,
      level: 'error',
      title: '发现无效评价指标',
      message: '快照包含无效指标，但没有对应机器错误码。',
      details: invalidMetricDiagnostics.value.map(metric =>
        `${metric.displayName}：${metric.reason || '评价事实无效'}`)
    });
  }
  return warnings;
});

/** Phase 6C：单请求互斥，慢请求期间不叠加第二次读取。 */
async function pollLatestEvaluationSnapshot(): Promise<void> {
  if (polling.value) {
    return;
  }
  polling.value = true;
  activeRequest = new AbortController();
  try {
    const result = await fetchLatestEvaluationSnapshot(activeRequest.signal);
    networkError.value = '';
    lastSuccessfulReadAt.value = new Date();
    if (result.kind === 'empty') {
      // Phase 6C：404 也可能表示 reset，必须清除上一运行的陈旧快照。
      snapshot.value = null;
      waitingForSnapshot.value = true;
      return;
    }
    if (shouldAcceptEvaluationSnapshot(snapshot.value, result.snapshot)) {
      snapshot.value = result.snapshot;
      waitingForSnapshot.value = false;
    }
  } catch (error: unknown) {
    if (!isEvaluationRequestCanceled(error)) {
      networkError.value = readErrorMessage(error);
    }
  } finally {
    activeRequest = null;
    polling.value = false;
  }
}

/** Phase 6C：组件挂载即读取，卸载即停止，避免后台隐藏轮询。 */
onMounted(() => {
  void pollLatestEvaluationSnapshot();
  pollingTimer = window.setInterval(() => void pollLatestEvaluationSnapshot(), POLL_INTERVAL_MS);
});

onUnmounted(() => {
  if (pollingTimer !== null) {
    window.clearInterval(pollingTimer);
    pollingTimer = null;
  }
  activeRequest?.abort();
});

function metricById(metricId: string): EvaluationMetricValue | null {
  return snapshot.value?.metrics?.[metricId] || null;
}

function formatMetric(metric: EvaluationMetricValue | null): string {
  if (!metric || metric.status !== 'AVAILABLE' || metric.value === null) {
    return '--';
  }
  if (metric.unit === 'ratio') {
    return formatRatio(metric.value);
  }
  if (metric.unit === 'boolean') {
    return metric.value >= 0.5 ? '满足' : '不满足';
  }
  const digits = ['vehicle', 'task', 'road', 'event'].includes(metric.unit) ? 0 : 2;
  return `${formatPlainNumber(metric.value, digits)} ${metric.unit}`;
}

function formatRatio(value: number): string {
  return `${formatPlainNumber(value * 100, 2)}%`;
}

function formatPlainNumber(value: number, maximumFractionDigits: number): string {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits
  }).format(value);
}

function metricStatusText(metric: EvaluationMetricValue | null): string {
  if (!metric) return '字段缺失';
  const labels: Record<EvaluationMetricValueStatus, string> = {
    AVAILABLE: '可用',
    NOT_AVAILABLE: '暂不可用',
    NOT_SUPPORTED: '不支持',
    NOT_APPLICABLE: '不适用',
    INVALID: '无效'
  };
  return labels[metric.status];
}

function metricRowClass(metric: EvaluationMetricValue | null): string {
  return `evaluation-metric-row--${(metric?.status || 'MISSING').toLowerCase().replace('_', '-')}`;
}

function hasLongMetricReason(metric: EvaluationMetricValue | null): boolean {
  return Boolean(metric?.reason && metric.reason.length > 56);
}

function warningLevelText(level: EvaluationWarningLevel): string {
  return level === 'error' ? '异常' : '警告';
}

function metricStatusClass(metric: EvaluationMetricValue | null): string {
  return `evaluation-metric-state--${(metric?.status || 'MISSING').toLowerCase().replace('_', '-')}`;
}

const snapshotStatusText = computed(() => ({
  COMPLETE: '完整',
  PARTIAL: '部分完成',
  FAILED: '失败'
}[snapshot.value?.snapshotStatus || 'FAILED']));

const snapshotStatusClass = computed(() =>
  `evaluation-status-badge--${(snapshot.value?.snapshotStatus || 'FAILED').toLowerCase()}`
);

const runKindText = computed(() =>
  snapshot.value?.runKind === 'DISPATCH_COMPARISON' ? '调度对比实验' : '普通仿真'
);

const formattedSimTime = computed(() => {
  const value = snapshot.value?.simTime;
  return value ? value.replace('T', ' ') : '--';
});

const connectionText = computed(() => {
  if (networkError.value) return '连接异常';
  if (polling.value && !snapshot.value) return '正在读取';
  if (waitingForSnapshot.value) return '等待快照';
  return '动态更新中';
});

const connectionClass = computed(() => ({
  'evaluation-connection--error': Boolean(networkError.value),
  'evaluation-connection--waiting': waitingForSnapshot.value && !networkError.value
}));

const lastSuccessfulReadText = computed(() =>
  lastSuccessfulReadAt.value?.toLocaleTimeString('zh-CN', { hour12: false }) || '--'
);

/** Phase 6C：聚合展示后续阶段依赖，不展开完整契约表。 */
const deferredSummaries = computed(() => {
  const allMetrics = Object.values(snapshot.value?.metrics || {});
  return [
    summarizeDeferred('environment', '外部环境', allMetrics.filter(metric => metric.category === 'ENVIRONMENT')),
    summarizeDeferred(
      'energy',
      '能耗与碳排',
      allMetrics.filter(metric => metric.metricId === 'carbonIntensity'
        || metric.metricId === 'vehicleTotalEnergy'
        || metric.metricId === 'vehicleTotalEmissionKg'
        || metric.metricId === 'vehicleEmissionIntensity'
        || metric.metricId === 'taskEmissionKg'
        || metric.metricId === 'taskEmissionIntensity')
    )
  ];
});

function summarizeDeferred(key: string, title: string, metrics: EvaluationMetricValue[]) {
  const available = metrics.filter(metric => metric.status === 'AVAILABLE').length;
  const pending = metrics.filter(metric => metric.status === 'NOT_AVAILABLE').length;
  const unsupported = metrics.filter(metric => metric.status === 'NOT_SUPPORTED').length;
  const invalid = metrics.filter(metric => metric.status === 'INVALID').length;
  // Phase 7E-R：详情优先暴露真实异常，其次是待接入事实；明确不支持不再伪装成开发缺口。
  const reason = metrics.find(metric => metric.status === 'INVALID')?.reason
    || metrics.find(metric => metric.status === 'NOT_AVAILABLE')?.reason
    || metrics.find(metric => metric.status === 'NOT_SUPPORTED')?.reason
    || (metrics.length ? '本轮事实已可用' : '快照中没有对应字段');
  return { key, title, available, pending, unsupported, invalid, total: metrics.length, reason };
}

function readErrorMessage(error: unknown): string {
  if (error && typeof error === 'object') {
    const candidate = error as {
      message?: string;
      response?: { data?: { message?: string } };
    };
    return candidate.response?.data?.message || candidate.message || '评价快照读取失败';
  }
  return '评价快照读取失败';
}
</script>

<style scoped>
/* Phase 6C：样式完全限定在独立评价页面，不覆盖地图、路线、图标或动画类。 */
.evaluation-dashboard-shell {
  position: fixed;
  inset: 60px 0 0;
  z-index: 3200;
  overflow: hidden;
  background:
    radial-gradient(circle at 12% 5%, rgba(64, 158, 255, 0.12), transparent 28%),
    #f4f7fb;
  color: #263445;
}

.evaluation-dashboard {
  height: 100%;
  overflow-y: auto;
  padding: 22px clamp(16px, 3vw, 42px) 28px;
  box-sizing: border-box;
}

.evaluation-topbar,
.evaluation-run-heading,
.evaluation-section-heading,
.evaluation-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.evaluation-topbar h2,
.evaluation-section h3,
.evaluation-empty-state h3 {
  margin: 0;
}

.evaluation-topbar p,
.evaluation-section-heading p,
.evaluation-empty-state p {
  margin: 5px 0 0;
  color: #718096;
}

.evaluation-eyebrow,
.evaluation-label {
  color: #337ecc;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.evaluation-topbar-actions,
.evaluation-thresholds {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.evaluation-connection {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #3e8e64;
  font-size: 13px;
}

.evaluation-connection i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #50b47b;
  box-shadow: 0 0 0 4px rgba(80, 180, 123, 0.14);
}

.evaluation-connection--waiting { color: #b17b19; }
.evaluation-connection--waiting i { background: #d9a441; box-shadow: 0 0 0 4px rgba(217, 164, 65, 0.14); }
.evaluation-connection--error { color: #c45656; }
.evaluation-connection--error i { background: #d85b5b; box-shadow: 0 0 0 4px rgba(216, 91, 91, 0.14); }

.evaluation-alert,
.evaluation-run-summary,
.evaluation-section,
.evaluation-empty-state {
  margin-top: 18px;
  border: 1px solid #e0e8f2;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 8px 28px rgba(45, 68, 95, 0.06);
}

.evaluation-alert { padding: 12px 16px; }
.evaluation-alert--error { border-color: #f3c5c5; background: #fff5f5; color: #b94b4b; }
.evaluation-run-summary,
.evaluation-section { padding: 20px; }

.evaluation-run-heading strong {
  display: block;
  max-width: 650px;
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.evaluation-status-badge {
  padding: 5px 11px;
  border-radius: 999px;
  background: #edf7f1;
  color: #38845b;
  font-size: 12px;
  font-weight: 700;
}

.evaluation-status-badge--partial { background: #fff7e6; color: #a86d0c; }
.evaluation-status-badge--failed { background: #fff0f0; color: #bd4545; }

.evaluation-meta-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(110px, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.evaluation-meta-grid div {
  padding: 12px;
  border-radius: 9px;
  background: #f6f8fb;
}

.evaluation-meta-grid span,
.evaluation-objective-card > span,
.evaluation-deferred-card > span {
  display: block;
  color: #7b8798;
  font-size: 12px;
}

.evaluation-meta-grid strong { display: block; margin-top: 6px; }
.evaluation-thresholds { margin-top: 14px; color: #64748b; font-size: 12px; }
.evaluation-thresholds span { padding: 5px 8px; border-radius: 6px; background: #f1f5f9; }
/* Phase 7E-R：运行警告使用独立卡片承载严重程度、机器码和可展开诊断。 */
.evaluation-warning-stack {
  display: grid;
  gap: 9px;
  margin-top: 14px;
}

.evaluation-warning {
  padding: 11px 13px;
  border: 1px solid #f0d5a4;
  border-left: 4px solid #d9a441;
  border-radius: 9px;
  background: #fffbf2;
  color: #76541c;
}

.evaluation-warning--error {
  border-color: #f1c5c5;
  border-left-color: #d85b5b;
  background: #fff6f6;
  color: #8f3535;
}

.evaluation-warning-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.evaluation-warning-heading > span {
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(217, 164, 65, 0.16);
  font-size: 11px;
  font-weight: 700;
}

.evaluation-warning--error .evaluation-warning-heading > span {
  background: rgba(216, 91, 91, 0.13);
}

.evaluation-warning-heading code {
  color: inherit;
  font-size: 11px;
  opacity: 0.72;
}

.evaluation-warning p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.55;
}

.evaluation-warning-details,
.evaluation-metric-reason-details {
  margin-top: 7px;
  font-size: 12px;
}

.evaluation-warning-details summary,
.evaluation-metric-reason-details summary {
  width: fit-content;
  cursor: pointer;
  font-weight: 600;
  user-select: none;
}

.evaluation-warning-details ul {
  margin: 7px 0 0;
  padding-left: 18px;
}

.evaluation-warning-details li {
  margin-top: 4px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.evaluation-objective-grid,
.evaluation-deferred-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.evaluation-objective-card,
.evaluation-deferred-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid #e8edf3;
  border-radius: 10px;
  background: #fbfcfe;
}

.evaluation-objective-card strong,
.evaluation-deferred-card strong {
  display: block;
  margin: 8px 0;
  color: #1f3652;
  font-size: 24px;
}

.evaluation-card-state,
.evaluation-deferred-card small {
  display: block;
  overflow: hidden;
  color: #718096;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.evaluation-metric-table {
  margin-top: 15px;
  overflow: hidden;
  border: 1px solid #e6ecf3;
  border-radius: 10px;
  background: #fff;
}

.evaluation-metric-row {
  display: grid;
  grid-template-columns: minmax(180px, 1.2fr) minmax(130px, 0.7fr) minmax(240px, 2fr);
  align-items: center;
  gap: 16px;
  min-height: 42px;
  padding: 8px 14px;
  border-top: 1px solid #edf1f5;
  box-sizing: border-box;
  font-size: 13px;
  transition: background-color 0.16s ease;
}

.evaluation-metric-row:first-child { border-top: 0; }
.evaluation-metric-row:not(.evaluation-metric-row--head):nth-child(odd) { background: #fafbfd; }
.evaluation-metric-row:not(.evaluation-metric-row--head):hover { background: #f3f7fb; }
.evaluation-metric-row--invalid { background: #fff8f8 !important; }
.evaluation-metric-row--not-supported { background: #f8fafc !important; }

.evaluation-metric-row--head {
  min-height: 36px;
  background: #edf3f8;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.evaluation-metric-name { color: #34465a; }
.evaluation-metric-value { color: #1f3652; font-variant-numeric: tabular-nums; }
.evaluation-metric-state { min-width: 0; }

.evaluation-metric-status-pill {
  display: inline-flex;
  align-items: center;
  min-width: 52px;
  justify-content: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: #edf7f1;
  color: #438460;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.evaluation-metric-state--not-available {
  background: #fff7e6;
  color: #a2731d;
}

.evaluation-metric-state--not-supported {
  background: #eef2f6;
  color: #64748b;
}

.evaluation-metric-state--not-applicable {
  background: #f4f0fa;
  color: #795b9f;
}

.evaluation-metric-state--invalid,
.evaluation-metric-state--missing {
  background: #fff0f0;
  color: #bd4545;
}

.evaluation-metric-reason,
.evaluation-metric-reason-details small {
  display: block;
  margin-top: 5px;
  color: #8994a3;
  font-size: 11px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.evaluation-metric-reason-details {
  color: #64748b;
}

.evaluation-deferred-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.evaluation-deferred-card strong { font-size: 19px; }
.evaluation-deferred-counts { display: flex; gap: 6px; flex-wrap: wrap; margin: 0 0 8px; }
.evaluation-deferred-counts span {
  padding: 3px 7px;
  border-radius: 6px;
  background: #eef3f8;
  color: #66778a;
  font-size: 11px;
}

.evaluation-empty-state {
  padding: 70px 20px;
  text-align: center;
}

.evaluation-empty-icon { color: #409eff; font-size: 44px; line-height: 1; }
.evaluation-footer { margin-top: 18px; color: #8793a3; font-size: 11px; }

@media (max-width: 1100px) {
  .evaluation-meta-grid { grid-template-columns: repeat(3, minmax(110px, 1fr)); }
  .evaluation-objective-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 720px) {
  .evaluation-topbar,
  .evaluation-run-heading,
  .evaluation-section-heading,
  .evaluation-footer { align-items: flex-start; flex-direction: column; }
  .evaluation-meta-grid,
  .evaluation-objective-grid,
  .evaluation-deferred-grid { grid-template-columns: 1fr; }
  .evaluation-metric-row { grid-template-columns: 1fr; gap: 7px; padding: 12px; }
  .evaluation-metric-row--head { display: none; }
  .evaluation-metric-value { font-size: 16px; }
}
</style>
