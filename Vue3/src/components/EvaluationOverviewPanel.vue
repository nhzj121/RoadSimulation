<template>
  <section class="evaluation-overview" aria-label="实时评价概览">
    <header class="evaluation-overview__header">
      <div>
        <span class="evaluation-overview__eyebrow">REAL-TIME EVALUATION</span>
        <h3>评价概览</h3>
      </div>
      <span class="evaluation-overview__connection" :class="connectionClass">
        <i></i>{{ connectionText }}
      </span>
    </header>

    <div v-if="networkError" class="evaluation-overview__message evaluation-overview__message--error" role="alert">
      {{ networkError }}；保留最近一次成功快照并自动重试。
    </div>

    <div v-if="waitingForSnapshot" class="evaluation-overview__empty">
      <span class="evaluation-overview__empty-mark">∿</span>
      <strong>等待首个评价快照</strong>
      <small>仿真完成一个业务循环后自动显示。</small>
    </div>

    <template v-else-if="snapshot">
      <div class="evaluation-overview__run">
        <div>
          <span>Loop</span>
          <strong>{{ snapshot.loopIndex }}</strong>
        </div>
        <div>
          <span>仿真时间</span>
          <strong>{{ compactSimTime }}</strong>
        </div>
        <span class="evaluation-overview__snapshot-status" :class="snapshotStatusClass">
          {{ snapshotStatusText }}
        </span>
      </div>

      <section
        v-if="overviewWarnings.length"
        class="evaluation-overview__warnings"
        aria-label="本轮评价提示"
        aria-live="polite"
      >
        <div class="evaluation-overview__warnings-head">
          <strong>本轮提示</strong>
          <span>{{ overviewWarnings.length }} 项</span>
        </div>
        <div
          v-for="warning in overviewWarnings"
          :key="warning.key"
          class="evaluation-overview__warning"
          :class="`evaluation-overview__warning--${warning.level}`"
        >
          <strong>{{ warning.title }}</strong>
          <small :title="warning.detail">{{ warning.detail }}</small>
        </div>
      </section>

      <div class="evaluation-overview__objectives">
        <article
          v-for="(metricId, index) in objectiveMetricIds"
          :key="metricId"
          class="evaluation-overview__objective"
          :class="metricCardClass(metricById(metricId))"
        >
          <div class="evaluation-overview__objective-head">
            <span>目标 {{ index + 1 }}</span>
            <small>{{ metricStatusText(metricById(metricId)) }}</small>
          </div>
          <strong>{{ formatMetric(metricById(metricId)) }}</strong>
          <p :title="metricById(metricId)?.reason || ''">
            {{ metricById(metricId)?.displayName || metricId }}
          </p>
        </article>
      </div>

      <section class="evaluation-overview__constraint" :class="constraintClass">
        <div class="evaluation-overview__constraint-head">
          <div>
            <span>服务约束</span>
            <strong>{{ serviceConstraintText }}</strong>
          </div>
          <small>阈值 {{ formatSeconds(snapshot.thresholds.maxServiceWaitSeconds) }}</small>
        </div>
        <div class="evaluation-overview__constraint-values">
          <div>
            <span>货物 P95</span>
            <strong>{{ formatMetric(metricById('cargoP95WaitSeconds')) }}</strong>
          </div>
          <div>
            <span>任务 P95</span>
            <strong>{{ formatMetric(metricById('taskP95ServiceWaitSeconds')) }}</strong>
          </div>
        </div>
      </section>

      <section class="evaluation-overview__trend">
        <div class="evaluation-overview__section-head">
          <div>
            <span>本次打开期间的快照趋势</span>
            <small>最多保留当前面板接收的 30 个后端快照</small>
          </div>
          <select v-model="selectedTrendMetricId" aria-label="趋势指标">
            <option v-for="metricId in objectiveMetricIds" :key="metricId" :value="metricId">
              {{ metricById(metricId)?.displayName || objectiveFallbackNames[metricId] }}
            </option>
          </select>
        </div>

        <div v-if="trendFiniteValues.length" class="evaluation-overview__chart-wrap">
          <div class="evaluation-overview__chart-scale">
            <span>{{ trendMaxLabel }}</span>
            <span>{{ trendMinLabel }}</span>
          </div>
          <svg
            class="evaluation-overview__chart"
            viewBox="0 0 280 84"
            role="img"
            :aria-label="`${selectedTrendLabel}本次打开期间的快照趋势`"
          >
            <line x1="0" y1="12" x2="280" y2="12" class="evaluation-overview__grid-line" />
            <line x1="0" y1="42" x2="280" y2="42" class="evaluation-overview__grid-line" />
            <line x1="0" y1="72" x2="280" y2="72" class="evaluation-overview__grid-line" />
            <polyline
              v-for="(segment, index) in trendSegments"
              :key="index"
              :points="segment"
              class="evaluation-overview__trend-line"
            />
            <circle
              v-if="trendLastPoint"
              :cx="trendLastPoint.x"
              :cy="trendLastPoint.y"
              r="3.5"
              class="evaluation-overview__trend-dot"
            />
          </svg>
          <div class="evaluation-overview__chart-caption">
            <span>{{ trendStartLoopLabel }}</span>
            <strong>{{ formatMetric(metricById(selectedTrendMetricId)) }}</strong>
            <span>{{ trendEndLoopLabel }}</span>
          </div>
        </div>
        <div v-else class="evaluation-overview__trend-empty">当前指标尚无可绘制的连续快照。</div>
      </section>

      <div class="evaluation-overview__facts">
        <div><span>本轮处理任务</span><strong>{{ snapshot.processedAssignmentCount }}</strong></div>
        <div><span>本轮失败任务</span><strong>{{ snapshot.failedAssignmentCount }}</strong></div>
        <div><span>快照版本</span><strong>{{ snapshot.snapshotRevision }}</strong></div>
      </div>
    </template>

    <footer class="evaluation-overview__footer">
      <span>每 1 s 更新</span>
      <ElButton type="primary" plain size="small" @click="emit('open-full')">查看完整评价</ElButton>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { ElButton } from 'element-plus';
import { fetchLatestEvaluationSnapshot, isEvaluationRequestCanceled } from '../api/evaluationApi';
import type { EvaluationMetricValue, EvaluationSnapshot } from '../types/evaluation';
import { shouldAcceptEvaluationSnapshot } from '../utils/evaluationSnapshotPolicy';

const emit = defineEmits<{ (event: 'open-full'): void }>();

const POLL_INTERVAL_MS = 1000;
const MAX_TREND_SNAPSHOTS = 30;
const objectiveMetricIds = Object.freeze([
  'emptyMileageRatio',
  'capacityWasteRatio',
  'unmetDemandRatio',
  'carbonIntensity'
]);
const objectiveFallbackNames: Record<string, string> = {
  emptyMileageRatio: '全局车辆空载里程率',
  capacityWasteRatio: '全局里程加权运力浪费率',
  unmetDemandRatio: '全局运输需求未达成率',
  carbonIntensity: '全局单位有效吨公里碳排放'
};

const snapshot = ref<EvaluationSnapshot | null>(null);
const waitingForSnapshot = ref(true);
const networkError = ref('');
const polling = ref(false);
const selectedTrendMetricId = ref('emptyMileageRatio');
const trendSnapshots = ref<EvaluationSnapshot[]>([]);
let pollingTimer: number | null = null;
let activeRequest: AbortController | null = null;

function metricById(metricId: string): EvaluationMetricValue | null {
  return snapshot.value?.metrics?.[metricId] || null;
}

function formatMetric(metric: EvaluationMetricValue | null): string {
  if (!metric || metric.status !== 'AVAILABLE' || metric.value === null) return '--';
  if (metric.unit === 'ratio') {
    return `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(metric.value * 100)}%`;
  }
  if (metric.unit === 'boolean') return metric.value >= 0.5 ? '满足' : '不满足';
  const digits = ['vehicle', 'task', 'road', 'event'].includes(metric.unit) ? 0 : 2;
  return `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: digits }).format(metric.value)} ${metric.unit}`;
}

function formatSeconds(value: number): string {
  return `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(value)} s`;
}

function metricStatusText(metric: EvaluationMetricValue | null): string {
  if (!metric) return '字段缺失';
  return ({
    AVAILABLE: '可用',
    NOT_AVAILABLE: '暂不可用',
    NOT_SUPPORTED: '不支持',
    NOT_APPLICABLE: '不适用',
    INVALID: '无效'
  })[metric.status];
}

function metricCardClass(metric: EvaluationMetricValue | null): string {
  return `evaluation-overview__objective--${(metric?.status || 'MISSING').toLowerCase().replace('_', '-')}`;
}

function rememberTrendSnapshot(incoming: EvaluationSnapshot): void {
  const previous = trendSnapshots.value[trendSnapshots.value.length - 1];
  if (previous && previous.simulationRunId !== incoming.simulationRunId) {
    trendSnapshots.value = [];
  }
  if (previous
      && previous.simulationRunId === incoming.simulationRunId
      && previous.snapshotRevision >= incoming.snapshotRevision) {
    return;
  }
  trendSnapshots.value = [...trendSnapshots.value, incoming].slice(-MAX_TREND_SNAPSHOTS);
}

async function pollLatestSnapshot(): Promise<void> {
  if (polling.value) return;
  polling.value = true;
  activeRequest = new AbortController();
  try {
    const result = await fetchLatestEvaluationSnapshot(activeRequest.signal);
    networkError.value = '';
    if (result.kind === 'empty') {
      snapshot.value = null;
      trendSnapshots.value = [];
      waitingForSnapshot.value = true;
      return;
    }
    if (shouldAcceptEvaluationSnapshot(snapshot.value, result.snapshot)) {
      rememberTrendSnapshot(result.snapshot);
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

onMounted(() => {
  void pollLatestSnapshot();
  pollingTimer = window.setInterval(() => void pollLatestSnapshot(), POLL_INTERVAL_MS);
});

onUnmounted(() => {
  if (pollingTimer !== null) window.clearInterval(pollingTimer);
  activeRequest?.abort();
});

const compactSimTime = computed(() => {
  const value = snapshot.value?.simTime;
  if (!value) return '--';
  return value.replace('T', ' ').slice(5, 19);
});

const snapshotStatusText = computed(() => ({
  COMPLETE: '完整',
  PARTIAL: '部分完成',
  FAILED: '失败'
}[snapshot.value?.snapshotStatus || 'FAILED']));

const snapshotStatusClass = computed(() =>
  `evaluation-overview__snapshot-status--${(snapshot.value?.snapshotStatus || 'FAILED').toLowerCase()}`
);

const connectionText = computed(() => {
  if (networkError.value) return '连接异常';
  if (polling.value && !snapshot.value) return '读取中';
  if (waitingForSnapshot.value) return '等待快照';
  return '动态更新';
});

const connectionClass = computed(() => ({
  'evaluation-overview__connection--error': Boolean(networkError.value),
  'evaluation-overview__connection--waiting': waitingForSnapshot.value && !networkError.value
}));

const serviceConstraint = computed(() => metricById('waitingServiceCompliant'));
const serviceConstraintText = computed(() => {
  const metric = serviceConstraint.value;
  if (!metric || metric.status !== 'AVAILABLE' || metric.value === null) return metricStatusText(metric);
  return metric.value >= 0.5 ? '满足' : '不满足';
});
const constraintClass = computed(() => {
  const metric = serviceConstraint.value;
  if (!metric || metric.status !== 'AVAILABLE' || metric.value === null) return 'evaluation-overview__constraint--unknown';
  return metric.value >= 0.5
    ? 'evaluation-overview__constraint--ok'
    : 'evaluation-overview__constraint--warning';
});

interface OverviewWarning {
  key: string;
  level: 'error' | 'warning';
  title: string;
  detail: string;
}

/**
 * 地图侧只做紧凑提示：机器错误码和服务约束均直接读取后端快照，
 * 不在此处推断错误原因，也不重新计算 P95。
 */
const overviewWarnings = computed<OverviewWarning[]>(() => {
  const current = snapshot.value;
  if (!current) return [];
  const knownTitles: Record<string, string> = {
    EVALUATION_INVALID_FACT: '评价事实无效',
    EVALUATION_READY_METRIC_UNAVAILABLE: '已就绪指标缺失',
    EVALUATION_FACT_COLLECTION_FAILED: '评价事实采集失败',
    EVALUATION_HISTORY_PERSIST_FAILED: '评价历史保存失败',
    TRANSPORT_PROGRESS_ASSIGNMENT_FAILED: '部分运输任务推进失败'
  };
  const warnings = current.errorCodes.map((code): OverviewWarning => ({
    key: code,
    level: code.startsWith('EVALUATION_') && code !== 'EVALUATION_HISTORY_PERSIST_FAILED'
      ? 'error'
      : 'warning',
    title: knownTitles[code] || '未分类运行警告',
    detail: code
  }));
  const constraint = current.metrics?.waitingServiceCompliant;
  if (constraint?.status === 'AVAILABLE' && constraint.value !== null && constraint.value < 0.5) {
    warnings.push({
      key: 'WAITING_SERVICE_CONSTRAINT_VIOLATED',
      level: 'warning',
      title: 'P95 服务等待超过阈值',
      detail: `阈值 ${formatSeconds(current.thresholds.maxServiceWaitSeconds)}，详细数值见下方服务约束。`
    });
  }
  return warnings;
});

interface TrendSample {
  loopIndex: number;
  value: number | null;
}

const trendSamples = computed<TrendSample[]>(() => trendSnapshots.value.map(item => {
  const metric = item.metrics?.[selectedTrendMetricId.value];
  return {
    loopIndex: item.loopIndex,
    value: metric?.status === 'AVAILABLE' && metric.value !== null && Number.isFinite(metric.value)
      ? metric.value
      : null
  };
}));

const trendFiniteValues = computed(() =>
  trendSamples.value.map(item => item.value).filter((value): value is number => value !== null)
);

const trendBounds = computed(() => {
  if (!trendFiniteValues.value.length) return { min: 0, max: 0 };
  return {
    min: Math.min(...trendFiniteValues.value),
    max: Math.max(...trendFiniteValues.value)
  };
});

function trendCoordinate(index: number, value: number): { x: number; y: number } {
  const width = 280;
  const top = 12;
  const bottom = 72;
  const x = trendSamples.value.length <= 1
    ? width / 2
    : index * width / (trendSamples.value.length - 1);
  const { min, max } = trendBounds.value;
  const y = max === min ? (top + bottom) / 2 : bottom - (value - min) * (bottom - top) / (max - min);
  return { x, y };
}

const trendSegments = computed(() => {
  const segments: string[] = [];
  let current: string[] = [];
  trendSamples.value.forEach((sample, index) => {
    if (sample.value === null) {
      if (current.length > 1) segments.push(current.join(' '));
      current = [];
      return;
    }
    const point = trendCoordinate(index, sample.value);
    current.push(`${point.x.toFixed(2)},${point.y.toFixed(2)}`);
  });
  if (current.length > 1) segments.push(current.join(' '));
  return segments;
});

const trendLastPoint = computed(() => {
  for (let index = trendSamples.value.length - 1; index >= 0; index -= 1) {
    const sample = trendSamples.value[index];
    if (sample.value !== null) return trendCoordinate(index, sample.value);
  }
  return null;
});

const selectedTrendMetric = computed(() => metricById(selectedTrendMetricId.value));
const selectedTrendLabel = computed(() =>
  selectedTrendMetric.value?.displayName || objectiveFallbackNames[selectedTrendMetricId.value]
);
const trendMaxLabel = computed(() => formatTrendValue(trendBounds.value.max));
const trendMinLabel = computed(() => formatTrendValue(trendBounds.value.min));
const trendStartLoopLabel = computed(() => trendSamples.value.length ? `Loop ${trendSamples.value[0].loopIndex}` : '--');
const trendEndLoopLabel = computed(() => trendSamples.value.length ? `Loop ${trendSamples.value[trendSamples.value.length - 1].loopIndex}` : '--');

function formatTrendValue(value: number): string {
  const unit = selectedTrendMetric.value?.unit;
  if (unit === 'ratio') return `${(value * 100).toFixed(1)}%`;
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value);
}

function readErrorMessage(error: unknown): string {
  if (error && typeof error === 'object') {
    const candidate = error as { message?: string; response?: { data?: { message?: string } } };
    return candidate.response?.data?.message || candidate.message || '评价快照读取失败';
  }
  return '评价快照读取失败';
}
</script>

<style scoped>
.evaluation-overview {
  height: 100%;
  overflow-y: auto;
  box-sizing: border-box;
  padding: 14px;
  color: #263445;
  background: #f5f7fa;
}

.evaluation-overview__header,
.evaluation-overview__run,
.evaluation-overview__objective-head,
.evaluation-overview__constraint-head,
.evaluation-overview__section-head,
.evaluation-overview__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.evaluation-overview__header { margin-bottom: 12px; }
.evaluation-overview__header h3 { margin: 2px 0 0; font-size: 19px; }
.evaluation-overview__eyebrow { color: #337ecc; font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.evaluation-overview__connection { display: inline-flex; align-items: center; gap: 6px; color: #28784a; font-size: 12px; white-space: nowrap; }
.evaluation-overview__connection i { width: 7px; height: 7px; border-radius: 50%; background: #31a66a; box-shadow: 0 0 0 4px rgba(49,166,106,.12); }
.evaluation-overview__connection--waiting { color: #9b6a14; }
.evaluation-overview__connection--waiting i { background: #d9a441; box-shadow: 0 0 0 4px rgba(217,164,65,.14); }
.evaluation-overview__connection--error { color: #c45656; }
.evaluation-overview__connection--error i { background: #d85b5b; box-shadow: 0 0 0 4px rgba(216,91,91,.14); }

.evaluation-overview__message,
.evaluation-overview__empty {
  padding: 12px;
  border-radius: 9px;
  background: #fff;
  border: 1px solid #e3e9f1;
}
.evaluation-overview__message--error { margin-bottom: 10px; border-color: #f1c4c4; background: #fff6f6; color: #b94b4b; font-size: 12px; }
.evaluation-overview__empty { min-height: 180px; display: grid; place-content: center; gap: 8px; text-align: center; color: #718096; }
.evaluation-overview__empty-mark { color: #409eff; font-size: 36px; }

.evaluation-overview__run {
  padding: 10px 12px;
  margin-bottom: 10px;
  border: 1px solid #dfe7f1;
  border-radius: 10px;
  background: #fff;
}
.evaluation-overview__run > div { display: grid; gap: 2px; }
.evaluation-overview__run span { color: #7b8797; font-size: 11px; }
.evaluation-overview__run strong { color: #263445; font-size: 13px; font-variant-numeric: tabular-nums; }
.evaluation-overview__snapshot-status { padding: 4px 8px; border-radius: 999px; font-weight: 700; }
.evaluation-overview__snapshot-status--complete { background: #eaf7ef; color: #28784a !important; }
.evaluation-overview__snapshot-status--partial { background: #fff5df; color: #9b6a14 !important; }
.evaluation-overview__snapshot-status--failed { background: #fff0f0; color: #bd4545 !important; }

.evaluation-overview__objectives { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
.evaluation-overview__objective {
  min-width: 0;
  padding: 11px;
  border: 1px solid #dfe7f1;
  border-radius: 10px;
  background: #fff;
}
.evaluation-overview__objective-head > span { color: #337ecc; font-size: 10px; font-weight: 700; letter-spacing: .06em; }
.evaluation-overview__objective-head small { color: #7b8797; font-size: 10px; }
.evaluation-overview__objective > strong { display: block; margin: 9px 0 5px; color: #1f3652; font-size: 19px; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.evaluation-overview__objective p { min-height: 30px; margin: 0; color: #68778a; font-size: 11px; line-height: 1.4; }
.evaluation-overview__objective--invalid { border-color: #efb5b5; background: #fff8f8; }
.evaluation-overview__objective--not-available,
.evaluation-overview__objective--not-supported,
.evaluation-overview__objective--not-applicable,
.evaluation-overview__objective--missing { background: #f8fafc; }

.evaluation-overview__warnings {
  display: grid;
  gap: 7px;
  margin-bottom: 10px;
  padding: 10px 11px;
  border: 1px solid #efd6a6;
  border-radius: 9px;
  background: #fffbf2;
}
.evaluation-overview__warnings-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.evaluation-overview__warnings-head strong { color: #7b571d; font-size: 12px; }
.evaluation-overview__warnings-head span { color: #99702c; font-size: 10px; }
.evaluation-overview__warning { min-width: 0; padding-left: 8px; border-left: 3px solid #d9a441; }
.evaluation-overview__warning--error { border-left-color: #d85b5b; }
.evaluation-overview__warning strong,
.evaluation-overview__warning small { display: block; }
.evaluation-overview__warning strong { color: #6f5120; font-size: 11px; }
.evaluation-overview__warning--error strong { color: #9a4141; }
.evaluation-overview__warning small {
  margin-top: 2px;
  overflow: hidden;
  color: #8a7350;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.evaluation-overview__constraint,
.evaluation-overview__trend {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #dfe7f1;
  border-radius: 10px;
  background: #fff;
}
.evaluation-overview__constraint { border-left: 4px solid #67c23a; }
.evaluation-overview__constraint--warning { border-left-color: #e6a23c; background: #fffaf0; }
.evaluation-overview__constraint--unknown { border-left-color: #a8b1bd; }
.evaluation-overview__constraint-head span,
.evaluation-overview__section-head span { color: #59697d; font-size: 12px; font-weight: 700; }
.evaluation-overview__constraint-head > div { display: grid; gap: 3px; }
.evaluation-overview__constraint-head > div strong { font-size: 17px; }
.evaluation-overview__constraint-head > small { color: #7b8797; font-size: 11px; }
.evaluation-overview__constraint-values { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 10px; }
.evaluation-overview__constraint-values div { padding: 8px; border-radius: 7px; background: rgba(242,246,252,.85); }
.evaluation-overview__constraint-values span { display: block; color: #7b8797; font-size: 10px; }
.evaluation-overview__constraint-values strong { display: block; margin-top: 4px; font-size: 13px; font-variant-numeric: tabular-nums; }

.evaluation-overview__section-head { align-items: flex-start; }
.evaluation-overview__section-head > div { display: grid; gap: 2px; }
.evaluation-overview__section-head small { color: #8a96a6; font-size: 10px; }
.evaluation-overview__section-head select { max-width: 132px; min-width: 0; padding: 5px 7px; border: 1px solid #dcdfe6; border-radius: 6px; color: #42546a; background: #fff; font-size: 11px; }
.evaluation-overview__chart-wrap { position: relative; margin-top: 10px; padding-left: 34px; }
.evaluation-overview__chart-scale { position: absolute; left: 0; top: 1px; bottom: 17px; display: flex; flex-direction: column; justify-content: space-between; color: #8a96a6; font-size: 9px; }
.evaluation-overview__chart { display: block; width: 100%; height: 84px; overflow: visible; }
.evaluation-overview__grid-line { stroke: #e6ebf2; stroke-width: 1; }
.evaluation-overview__trend-line { fill: none; stroke: #409eff; stroke-width: 2.4; stroke-linecap: round; stroke-linejoin: round; }
.evaluation-overview__trend-dot { fill: #409eff; stroke: #fff; stroke-width: 2; }
.evaluation-overview__chart-caption { display: flex; justify-content: space-between; gap: 6px; color: #8a96a6; font-size: 9px; }
.evaluation-overview__chart-caption strong { color: #337ecc; font-size: 11px; font-variant-numeric: tabular-nums; }
.evaluation-overview__trend-empty { margin-top: 10px; padding: 18px 8px; color: #8a96a6; background: #f8fafc; border-radius: 7px; text-align: center; font-size: 11px; }

.evaluation-overview__facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; margin-top: 10px; }
.evaluation-overview__facts div { min-width: 0; padding: 8px; border-radius: 8px; background: #edf3fa; text-align: center; }
.evaluation-overview__facts span { display: block; color: #748296; font-size: 9px; }
.evaluation-overview__facts strong { display: block; margin-top: 3px; color: #263445; font-size: 14px; font-variant-numeric: tabular-nums; }

.evaluation-overview__footer { margin-top: 12px; color: #8a96a6; font-size: 10px; }
</style>
