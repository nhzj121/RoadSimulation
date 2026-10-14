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
  waitingForSnapshot: true, networkError: '', polling: false });

/** Phase 6：单份评价数据源属于数据工作区，与侧栏隐藏、抽屉和图表生命周期无关。 */
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
      // 隐藏侧栏不改变 generation；离开工作区或 reset 才取消并隔离迟到响应。
      if (!isCurrent()) return;
      options.onObservation({ ...observation, phase: 'success', receivedAt: new Date().toISOString(),
        data: result.kind === 'empty' ? null : result.snapshot });
      feedState.networkError = '';
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
  function stop(): void {
    generation++;
    if (timer !== null) unschedule(timer);
    timer = null;
    controller?.abort(); controller = null;
    Object.assign(feedState, initialState());
    trendSnapshots.value = [];
  }
  watch([options.enabled, options.epoch], ([enabled]) => {
    stop();
    if (enabled) {
      void pollLatestSnapshot();
      timer = schedule(() => void pollLatestSnapshot(), 1000);
    } else options.onSuspend();
  }, { immediate: true, flush: 'post' });
  onScopeDispose(() => { disposed = true; stop(); });
  return { feedState, trendSnapshots };
}
