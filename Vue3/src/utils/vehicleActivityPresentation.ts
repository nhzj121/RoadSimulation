import type { DomainChartModel } from './evaluationDomainPresentation';
import { VISIBLE_VEHICLE_GROUPS } from './simulationWorkspacePresentation';

export const VEHICLE_ACTIVITY_WINDOW_MS = 5 * 60 * 1000;
export interface VehicleDisplayTransition {
  vehicleId: string;
  assignmentId: string;
  previousStatus: string;
  currentStatus: string;
  observedAt: number;
  kind?: 'task-start' | 'transition';
}

/** 本页只读观察窗口，不是后端业务账本；不按日志条数截断，不推断完成或状态时长。 */
export function createVehicleActivityWindow() {
  let events: VehicleDisplayTransition[] = [];
  let head = 0;
  const counts = VISIBLE_VEHICLE_GROUPS.map(() => 0);
  const groupIndex = (status: string) => {
    const index = VISIBLE_VEHICLE_GROUPS.findIndex(group => group.statuses.includes(status));
    return index < 0 ? counts.length - 1 : index;
  };
  function prune(now: number): void {
    // 窗口为 (now - W, now]；事件按同一单调页面时钟写入。
    while (head < events.length && events[head].observedAt <= now - VEHICLE_ACTIVITY_WINDOW_MS) {
      counts[groupIndex(events[head++].currentStatus)]--;
    }
    if (head && (head === events.length || head > 1024)) {
      events = events.slice(head);
      head = 0;
    }
  }
  function record(event: VehicleDisplayTransition): boolean {
    if (!event.vehicleId || !event.assignmentId || !event.previousStatus || !event.currentStatus
      || (event.previousStatus === event.currentStatus && event.kind !== 'task-start') || !Number.isFinite(event.observedAt)) return false;
    prune(event.observedAt);
    // 新任务起始回调可能等待绘制确认后才入窗，按原观察时间插入，避免过期顺序错误。
    let index = events.length;
    while (index > head && events[index - 1].observedAt > event.observedAt) index--;
    events.splice(index, 0, { ...event });
    counts[groupIndex(event.currentStatus)]++;
    return true;
  }
  function clear(): void { events = []; head = 0; counts.fill(0); }
  function chart(now: number): DomainChartModel {
    prune(now);
    const total = counts.reduce((sum, value) => sum + value, 0);
    return {
      id: 'map-vehicle-activity', title: '最近5分钟状态进入活动',
      description: '本页观察到的地图任务阶段进入次数，含新任务起始阶段；恢复不重复计数。实际时间窗口，暂停仍过期；不是后端事件账本或工作量。',
      kind: 'ring', unit: 'count', state: total ? 'ready' : 'empty',
      message: '最近5分钟暂无已观察到的状态进入活动', notices: [], total, inner: [],
      items: VISIBLE_VEHICLE_GROUPS.map((group, index) => ({ label: group.label, color: group.color, value: counts[index],
        note: ['前端阶段进入次数，含新任务起始；同车可多次进入，不代表时长或工作量', group.note].filter(Boolean).join('；') }))
    };
  }
  return { record, clear, chart };
}
