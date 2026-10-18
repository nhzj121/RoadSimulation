import { onScopeDispose, shallowRef, watch } from 'vue';
import type { Ref } from 'vue';
import { createVehicleActivityWindow } from '../utils/vehicleActivityPresentation';
import type { VehicleDisplayTransition } from '../utils/vehicleActivityPresentation';

type StatusCallback = (vehicleId: number | string, previousStatus: string, currentStatus: string) => void;
interface StatusSource {
  onStatusChange: (callback: StatusCallback) => void;
  assignmentData?: Map<number | string, unknown>;
}
interface ActivityTaskContext {
  assignmentId: number | string;
  render: object;
  ready: boolean;
  recovery: boolean;
}
interface ActivityOptions {
  manager: Ref<StatusSource | null>;
  epoch: Ref<number>;
  visible: Ref<boolean>;
  runIds: () => Array<string | null>;
  suppressed: () => boolean;
  // 只读已有地图任务归属；初始化/恢复时返回 null，不依赖评价轮询。
  assignmentFor: (vehicleId: number | string, source: StatusSource) => number | string | null;
  // 只读注册上下文：真实起始回调先暂存，确认绘制成功才计数；不从状态快照推断事件。
  taskFor?: (vehicleId: number | string, source: StatusSource) => ActivityTaskContext | null;
  readiness?: () => string;
  now?: () => number;
  schedule?: (callback: () => void, ms: number) => unknown;
  unschedule?: (timer: unknown) => void;
}

/** 展示观察者：不改状态管理器/动画方法，不建立网络请求，隐藏期间继续记录。 */
export function useVehicleDisplayActivity(options: ActivityOptions) {
  const window = createVehicleActivityWindow();
  const now = options.now ?? (() => globalThis.performance.now());
  const schedule = options.schedule ?? ((callback, ms) => globalThis.setInterval(callback, ms));
  const unschedule = options.unschedule ?? (timer => globalThis.clearInterval(timer as ReturnType<typeof setInterval>));
  const activityChart = shallowRef(window.chart(now()));
  const registered = new WeakSet<StatusSource>();
  let disposed = false;
  let timer: unknown = null;
  let knownRunIds = options.runIds().slice();
  let observationFailed = false;
  const tasks = new Map<string, { vehicleId: number | string; render: object; lastStatus: string;
    ignoreRegistration: boolean; pending: VehicleDisplayTransition[] }>();
  const seenTasks = new Set<string>(); // 仅保留身份，已清理路线/Marker 不被观察器长期引用。
  const lastCallbacks = new Map<string, { source: StatusSource; binding: unknown; status: string }>();
  const publish = () => {
    if (disposed) return;
    const chart = window.chart(now());
    activityChart.value = observationFailed ? { ...chart, state: 'unavailable', message: '车辆活动观察暂不可用' } : chart;
  };
  function flushPending(): void {
    if (disposed || !options.taskFor || !options.manager.value || options.suppressed()) return;
    try {
      let changed = false;
      for (const [key, state] of tasks) {
        const context = options.taskFor(state.vehicleId, options.manager.value);
        if (!context || context.render !== state.render) { tasks.delete(key); lastCallbacks.delete(String(state.vehicleId)); continue; }
        if (!context.ready) continue;
        // 恢复/重绘只建立展示基线；新任务则提交其真实起始回调及后续短时回调。
        state.ignoreRegistration = false;
        seenTasks.add(key);
        state.pending.forEach(event => { changed = window.record(event) || changed; });
        state.pending = [];
      }
      if (changed) observationFailed = false;
      if (changed && options.visible.value) publish();
    } catch { observationFailed = true; publish(); }
  }
  function observeTask(source: StatusSource, vehicleId: number | string, previousStatus: string, currentStatus: string): boolean {
    const context = options.taskFor!(vehicleId, source);
    if (!context) return false;
    const key = JSON.stringify([String(context.assignmentId), String(vehicleId)]);
    let state = tasks.get(key);
    const suppressed = options.suppressed();
    if (!state || state.render !== context.render) {
      const redrawn = seenTasks.has(key);
      state = { vehicleId, render: context.render, lastStatus: currentStatus,
        ignoreRegistration: suppressed || context.recovery || redrawn, pending: [] };
      tasks.set(key, state);
      if (!state.ignoreRegistration) {
        // 初始业务数组可能已经是 ORDER_DRIVING；仍是这个新任务第一次真正进入该阶段。
        const initial = ['ORDER_DRIVING', 'TRANSPORT_DRIVING'].includes(currentStatus);
        if (initial || (previousStatus && previousStatus !== currentStatus)) state.pending.push({
          vehicleId: String(vehicleId), assignmentId: String(context.assignmentId), previousStatus: previousStatus || currentStatus,
          currentStatus, observedAt: now(), kind: initial ? 'task-start' : 'transition'
        });
      }
    } else if (suppressed || (state.ignoreRegistration && !context.ready)) {
      state.lastStatus = currentStatus;
      state.pending = [];
    } else {
      state.ignoreRegistration = false;
      // 以已观察的前端回调为去重基线，避免后台刷新业务数组时伪造同状态进入。
      if (state.lastStatus !== currentStatus) {
        state.pending.push({ vehicleId: String(vehicleId), assignmentId: String(context.assignmentId),
          previousStatus: state.lastStatus, currentStatus, observedAt: now(), kind: 'transition' });
        state.lastStatus = currentStatus;
      }
    }
    observationFailed = false;
    if (context.ready || suppressed || context.recovery) seenTasks.add(key);
    if (context.ready && !suppressed && (state.pending.length || state.ignoreRegistration)) flushPending();
    return true;
  }
  watch(options.manager, source => {
    if (!source || registered.has(source)) return;
    registered.add(source);
    // 仅使用既有订阅入口；旧管理器无需改写，其回调通过身份和卸载标识失效。
    source.onStatusChange((vehicleId, previousStatus, currentStatus) => {
      if (disposed || source !== options.manager.value) return;
      try {
        if (options.taskFor) {
          const binding = source.assignmentData?.get(vehicleId);
          const last = lastCallbacks.get(String(vehicleId));
          // 逐帧的同状态更新走常数时间快路；任务重新注册时 binding 改变，不会吞掉新起始。
          if (binding && last?.source === source && last.binding === binding
            && last.status === currentStatus && previousStatus === currentStatus && !observationFailed) return;
          if (observeTask(source, vehicleId, previousStatus, currentStatus)) {
            lastCallbacks.set(String(vehicleId), { source, binding, status: currentStatus });
          }
          return;
        }
        // 兼容旧的只读接入方；主地图使用上方带任务注册边界的观察路径。
        if (previousStatus === currentStatus) return;
        if (options.suppressed()) return;
        const assignmentId = options.assignmentFor(vehicleId, source);
        if (assignmentId === null || assignmentId === undefined) return;
        if (window.record({ vehicleId: String(vehicleId), assignmentId: String(assignmentId),
          previousStatus, currentStatus, observedAt: now() })) {
          observationFailed = false;
          if (options.visible.value) publish();
        }
      } catch {
        // 展示观察故障不得打断运输回调；不把观察故障伪装成有效零值。
        observationFailed = true;
        publish();
      }
    });
  }, { immediate: true, flush: 'sync' });
  if (options.readiness) watch(options.readiness, flushPending, { flush: 'post' });
  watch(options.epoch, () => { window.clear(); tasks.clear(); seenTasks.clear(); lastCallbacks.clear(); knownRunIds = []; observationFailed = false; publish(); }, { flush: 'sync' });
  watch(options.runIds, ids => {
    // 运输/评价运行 ID 是不同域，分别比较；首次取得标识不删除此前已观察的活动。
    if (ids.some((id, index) => id && knownRunIds[index] && id !== knownRunIds[index])) {
      window.clear(); tasks.clear(); seenTasks.clear(); lastCallbacks.clear(); observationFailed = false; publish();
    }
    knownRunIds = ids.map((id, index) => id || knownRunIds[index] || null);
  }, { flush: 'sync' });
  watch(options.visible, visible => {
    if (timer !== null) unschedule(timer);
    timer = null;
    if (visible) {
      publish();
      // 只驱动窗口过期展示，不轮询接口、不驱动仿真；隐藏后停止绘图计时。
      timer = schedule(publish, 1000);
    }
  }, { immediate: true, flush: 'sync' });
  onScopeDispose(() => {
    disposed = true;
    if (timer !== null) unschedule(timer);
    window.clear();
    tasks.clear();
    seenTasks.clear();
    lastCallbacks.clear();
  });
  return { activityChart };
}
