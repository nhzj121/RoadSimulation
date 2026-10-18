import { reactive, ref, watch, onScopeDispose } from 'vue';
import type { Ref } from 'vue';
import { fetchLatestEvaluationSnapshot, isEvaluationRequestCanceled } from '../api/evaluationApi';
import type { LatestEvaluationResult } from '../api/evaluationApi';
import type { EvaluationSnapshot } from '../types/evaluation';
import type { RequestObservation } from '../utils/simulationSessionRecords';
import { shouldAcceptEvaluationSnapshot } from '../utils/evaluationSnapshotPolicy';

export interface SimulationEvaluationFeedState {
  snapshot: EvaluationSnapshot | null;
  initialLoading: boolean;
  waitingForSnapshot: boolean;
  networkError: string;
  polling: boolean;
  lastSuccessfulReadAt?: string | null;
}
interface FeedOptions {
  enabled: Ref<boolean>;
  epoch: Ref<number>;
  requestBase: () => number;
  onObservation: (observation: RequestObservation) => void;
  onSuspend: () => void;
  // 定向测试注入只读请求与时钟；生产调用不提供这些参数。
  fetchLatest?: (signal?: AbortSignal) => Promise<LatestEvaluationResult>;
  schedule?: (callback: () => void, ms: number) => unknown;
  unschedule?: (timer: unknown) => void;
}
const initialState = (): SimulationEvaluationFeedState => ({ snapshot: null, initialLoading: true,
  waitingForSnapshot: true, networkError: '', polling: false, lastSuccessfulReadAt: null });

/** 单份评价展示数据源，由父页面按运行/查阅状态启停，不驱动仿真或评价公式。 */
export function useSimulationEvaluationFeed(options: FeedOptions) {
  const feedState = reactive(initialState());
  const trendSnapshots = ref<EvaluationSnapshot[]>([]);
  const fetchLatest = options.fetchLatest ?? fetchLatestEvaluationSnapshot;
  const schedule = options.schedule ?? ((callback, ms) => globalThis.setInterval(callback, ms));
  const unschedule = options.unschedule ?? (timer => globalThis.clearInterval(timer as ReturnType<typeof setInterval>));
  let timer: unknown = null;
  let controller: AbortController | null = null;
  let generation = 0;
  let requestId = 0;
  let disposed = false;

  async function pollLatestSnapshot(): Promise<void> {
    if (disposed || !options.enabled.value || feedState.polling) return;
    const currentGeneration = generation;
    const currentEpoch = options.epoch.value;
    const current = new AbortController();
    controller = current;
    feedState.polling = true;
    requestId = Math.max(requestId, options.requestBase()) + 1;
    const observation = { source: 'evaluation' as const, epoch: currentEpoch, requestId };
    const isCurrent = () => !disposed && options.enabled.value && generation === currentGeneration
      && options.epoch.value === currentEpoch;
    options.onObservation({ ...observation, phase: 'start', receivedAt: new Date().toISOString() });
    try {
      const result = await fetchLatest(current.signal);
      // 工作区切换不再停用运行中的采集；暂停采集或 reset 仍隔离迟到响应。
      if (!isCurrent()) return;
      options.onObservation({ ...observation, phase: 'success', receivedAt: new Date().toISOString(),
        data: result.kind === 'empty' ? null : result.snapshot });
      feedState.networkError = '';
      feedState.lastSuccessfulReadAt = new Date().toISOString();
      if (result.kind === 'empty') {
        feedState.snapshot = null; trendSnapshots.value = []; feedState.waitingForSnapshot = true;
      } else if (shouldAcceptEvaluationSnapshot(feedState.snapshot, result.snapshot)) {
        const previous = trendSnapshots.value[trendSnapshots.value.length - 1];
        if (previous && previous.simulationRunId !== result.snapshot.simulationRunId) trendSnapshots.value = [];
        trendSnapshots.value = [...trendSnapshots.value, result.snapshot].slice(-30);
        feedState.snapshot = result.snapshot;
        feedState.waitingForSnapshot = false;
      }
    } catch (error: unknown) {
      if (!isCurrent() || isEvaluationRequestCanceled(error)) return;
      const candidate = error as { message?: string; response?: { data?: { message?: string } } } | null;
      feedState.networkError = candidate?.response?.data?.message || candidate?.message || '评价快照读取失败';
      options.onObservation({ ...observation, phase: 'failure', receivedAt: new Date().toISOString(), error: feedState.networkError });
    } finally {
      if (isCurrent()) { controller = null; feedState.polling = false; feedState.initialLoading = false; }
    }
  }
  // 展示修复：暂停请求不等于 reset；切换工作区时缓存继续属于父页面。
  function stop(clearData = false): void {
    generation++;
    if (timer !== null) unschedule(timer);
    timer = null;
    controller?.abort(); controller = null;
    feedState.polling = false;
    if (clearData) {
      Object.assign(feedState, initialState());
      trendSnapshots.value = [];
    }
  }
  watch([options.enabled, options.epoch], ([enabled, epoch], previous) => {
    stop(previous !== undefined && epoch !== previous[1]);
    if (enabled) {
      void pollLatestSnapshot();
      timer = schedule(() => void pollLatestSnapshot(), 1000);
    } else options.onSuspend();
  }, { immediate: true, flush: 'post' });
  onScopeDispose(() => { disposed = true; stop(true); });
  return { feedState, trendSnapshots };
}
