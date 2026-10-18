/** Phase 5：前端会话观察记录。只接收已有接口的观察结果，不请求或修改业务数据。 */
import { describeBreakdown } from './breakdownPresentation';
export type RecordCategory = 'random' | 'exception' | 'monitor';
export type RecordFilter = RecordCategory | 'all';
export type DataSource = 'transport' | 'evaluation';
export interface SessionRecord {
  id: number;
  category: RecordCategory;
  source: DataSource;
  title: string;
  detail: string;
  vehicleText: string;
  eventId: string | null;
  observedAt: string;
  eventTime: string | null;
  timeLabel: string;
}
export interface SourceHealth {
  phase: 'waiting' | 'updating' | 'ready' | 'failed' | 'inactive';
  hasData: boolean;
  lastSuccessAt: string | null;
  lastFailure: string;
  currentError: string;
  requestId: number;
}
interface ObservedEvent {
  id: string;
  fingerprint: string;
  vehicleText: string;
  eventType: string;
}
export interface SessionRecordsState {
  epoch: number;
  nextId: number;
  records: SessionRecord[];
  health: Record<DataSource, SourceHealth>;
  runIds: Record<DataSource, string | null>;
  events: Record<string, ObservedEvent>;
  errorCodes: string[];
  evaluationRevision: number | null;
  routeCount: number;
}
export interface RequestObservation {
  source: DataSource;
  phase: 'start' | 'success' | 'failure';
  epoch: number;
  requestId: number;
  receivedAt: string;
  error?: string;
  data?: unknown;
}
type ObjectData = Record<string, unknown>;
function object(value: unknown): ObjectData {
  return value !== null && typeof value === 'object' ? value as ObjectData : {};
}
function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}
function identifier(value: unknown): string | null {
  return typeof value === 'number' || (typeof value === 'string' && value.trim()) ? String(value) : null;
}
function initialHealth(): SourceHealth {
  return { phase: 'waiting', hasData: false, lastSuccessAt: null, lastFailure: '', currentError: '', requestId: 0 };
}
export function createSessionRecords(epoch = 0): SessionRecordsState {
  return { epoch, nextId: 1, records: [], health: { transport: initialHealth(), evaluation: initialHealth() },
    runIds: { transport: null, evaluation: null }, events: {}, errorCodes: [], evaluationRevision: null, routeCount: 0 };
}
function copy(state: SessionRecordsState): SessionRecordsState {
  return { ...state, records: [...state.records], health: { transport: { ...state.health.transport }, evaluation: { ...state.health.evaluation } },
    runIds: { ...state.runIds }, events: { ...state.events }, errorCodes: [...state.errorCodes] };
}
function append(state: SessionRecordsState, category: RecordCategory, source: DataSource, title: string,
  detail: string, observedAt: string, extra: Partial<SessionRecord> = {}): void {
  state.records.push({ id: state.nextId++, category, source, title, detail, observedAt,
    vehicleText: '', eventId: null, eventTime: null, timeLabel: '页面观察时间', ...extra });
  state.records = state.records.slice(-50);
}
/** 两个数据源独立维护运行标识；缺失标识不是 reset，不互相比较不同域的 ID。 */
function acceptRun(state: SessionRecordsState, source: DataSource, runId: string | null): void {
  if (!runId) return;
  const previous = state.runIds[source];
  if (previous && previous !== runId) {
    state.records = state.records.filter(record => record.source !== source);
    state.health[source] = initialHealth();
    if (source === 'transport') { state.events = {}; state.routeCount = 0; }
    else { state.errorCodes = []; state.evaluationRevision = null; }
  }
  state.runIds[source] = runId;
}
function eventFingerprint(event: ObjectData): string {
  // 只比较状态和阶段事实；延迟/速度等连续数值不用于写日志。
  return JSON.stringify(['status', 'breakdownLevel', 'breakdownPhase', 'recoveryOutcome',
    'replacementOutcome', 'replacementVehicleId'].map(key => event[key] ?? null));
}
function observeEvents(state: SessionRecordsState, values: unknown[], receivedAt: string): void {
  // 不完整集合不能用于推断旧事件已离开；保留上一次可靠比较基线。
  if (values.some(value => !identifier(object(value).eventId))) return;
  const next: Record<string, ObservedEvent> = Object.create(null);
  for (const value of values) {
    const event = object(value);
    const id = identifier(event.eventId);
    // 没有事件 ID 无法可靠去重，不制造替代业务 ID 或错误合并两辆车的事件。
    if (!id || Object.prototype.hasOwnProperty.call(next, id)) continue;
    const vehicleId = identifier(event.vehicleId);
    const vehicleText = text(event.licensePlate) || (vehicleId ? `车辆 ${vehicleId}` : '未指定车辆');
    const eventType = text(event.eventTypeText) || text(event.eventType) || '随机事件';
    const fingerprint = eventFingerprint(event);
    const previous = Object.prototype.hasOwnProperty.call(state.events, id) ? state.events[id] : undefined;
    next[id] = { id, fingerprint, vehicleText, eventType };
    if (!previous || previous.fingerprint !== fingerprint) {
      const stage = [event.status, event.breakdownLevel, event.breakdownPhase, event.recoveryOutcome, event.replacementOutcome]
        .map(text).filter(Boolean).join(' · ');
      // 复用现有故障展示解释；缺少时长字段时保留事实字段，不显示 undefined 分钟。
      const canDescribe = event.breakdownLevel === 'REPLACEMENT_REQUIRED' || event.breakdownPhase === 'RECOVERED'
        || (event.breakdownPhase === 'REPAIRING' && typeof event.repairMinutes === 'number')
        || (event.breakdownPhase === 'WAITING_RESCUE' && typeof event.rescueWaitMinutes === 'number');
      const detail = event.eventType === 'VEHICLE_BREAKDOWN' && canDescribe ? describeBreakdown(event)
        : text(event.description) || stage || '活动事件';
      // 首次观察只使用开始时间；更新仅使用与该阶段相符的后端时间，不能用计划结束时间冒充事实。
      const phaseTime = event.breakdownPhase === 'REPAIRING' ? text(event.repairStartTime)
        : event.breakdownPhase === 'REPLACEMENT_PREPARING' ? text(event.replacementSelectedTime)
        : event.breakdownPhase === 'REPLACED' ? text(event.replacementProcessedTime)
        : event.breakdownPhase === 'RECOVERED' ? text(event.recoveryProcessedTime)
        : event.status === 'RESOLVED' ? text(event.resolvedTime) : '';
      const eventTime = previous ? phaseTime : text(event.startTime);
      append(state, 'random', 'transport', previous ? `${eventType}阶段更新` : `首次观察到${eventType}`,
        detail, receivedAt,
        { vehicleText, eventId: id, eventTime: eventTime || null,
          timeLabel: eventTime ? previous ? '后端事件仿真时间' : '后端开始仿真时间' : '页面观察时间' });
    }
  }
  for (const [id, event] of Object.entries(state.events)) {
    if (!Object.prototype.hasOwnProperty.call(next, id)) append(state, 'random', 'transport', `${event.eventType}已离开活动集合`,
      '本次成功采样已不包含该事件；最终结果和结束时间未取得。', receivedAt,
      { vehicleText: event.vehicleText, eventId: id });
  }
  state.events = next;
}
function observeEvaluation(state: SessionRecordsState, data: ObjectData, receivedAt: string): void {
  const revision = data.snapshotRevision;
  if (typeof revision !== 'number' || !Number.isFinite(revision)) return;
  if (state.evaluationRevision !== null && revision <= state.evaluationRevision) return;
  state.evaluationRevision = revision;
  if (!Array.isArray(data.errorCodes)) return;
  const next = [...new Set(data.errorCodes.map(text).filter(Boolean))].sort();
  const added = next.filter(code => !state.errorCodes.includes(code));
  const removed = state.errorCodes.filter(code => !next.includes(code));
  if (added.length || removed.length) append(state, 'exception', 'evaluation', '评价提示变化',
    [added.length ? `出现：${added.join('、')}` : '', removed.length ? `解除：${removed.join('、')}` : ''].filter(Boolean).join('；'), receivedAt,
    { eventTime: text(data.simTime) || null, timeLabel: text(data.simTime) ? '评价快照仿真时间' : '页面观察时间' });
  state.errorCodes = next;
}
/** 不变更输入；过期 epoch 或较旧并发请求只在展示观察层被丢弃。 */
export function observeSessionRequest(input: SessionRecordsState, observation: RequestObservation): SessionRecordsState {
  if (observation.epoch !== input.epoch || observation.requestId < input.health[observation.source].requestId) return input;
  const state = copy(input);
  const { source, phase, receivedAt } = observation;
  const data = object(observation.data);
  if (phase === 'success') acceptRun(state, source, source === 'transport'
    ? identifier(object(data.weather).runId) : identifier(data.simulationRunId));
  const health = state.health[source];
  health.requestId = observation.requestId;
  if (phase === 'start') { health.phase = 'updating'; return state; }
  if (phase === 'failure') {
    const error = observation.error || '数据读取失败';
    if (!health.currentError) append(state, 'monitor', source, `${source === 'transport' ? '运输监控' : '评价快照'}读取失败`,
      `${error}${health.hasData ? '；保留最近一次成功数据' : '；当前没有成功数据'}`, receivedAt);
    health.phase = 'failed'; health.currentError = error; health.lastFailure = error;
    return state;
  }
  if (health.currentError) append(state, 'monitor', source, `${source === 'transport' ? '运输监控' : '评价快照'}连接恢复`,
    '请求成功；数据是否有新变化以对应内容为准。', receivedAt);
  health.phase = 'ready'; health.currentError = ''; health.lastSuccessAt = receivedAt;
  if (source === 'transport') {
    health.hasData = Array.isArray(data.activeEvents);
    if (Array.isArray(data.activeEvents)) observeEvents(state, data.activeEvents, receivedAt);
  } else {
    health.hasData = typeof data.snapshotRevision === 'number';
    if (health.hasData) observeEvaluation(state, data, receivedAt);
    else { state.errorCodes = []; state.evaluationRevision = null; }
  }
  return state;
}
export function observeRouteWaiting(input: SessionRecordsState, count: number, receivedAt: string): SessionRecordsState {
  if (!Number.isInteger(count) || count < 0 || count === input.routeCount) return input;
  const state = copy(input);
  append(state, 'exception', 'transport', count > 0 ? '路线恢复等待变化' : '路线恢复等待解除',
    `${count} 个任务等待路线恢复`, receivedAt);
  state.routeCount = count;
  return state;
}
/** 离开数据视图只暂停评价观察，不清空会话日志，也不伪造连接恢复。 */
export function suspendEvaluationObservation(input: SessionRecordsState): SessionRecordsState {
  const state = copy(input);
  state.health.evaluation.phase = 'inactive';
  return state;
}
export function filterSessionRecords(records: SessionRecord[], filter: RecordFilter): SessionRecord[] {
  return records.filter(record => filter === 'all' || record.category === filter).slice().reverse();
}
export function sourceHealthText(health: SourceHealth): string {
  if (health.phase === 'failed') return health.hasData ? '连接异常 · 显示缓存' : '连接异常 · 无成功数据';
  if (health.phase === 'inactive') return '当前视图未采样';
  if (health.phase === 'updating') return health.currentError ? health.hasData ? '重试中 · 显示缓存' : '重试中 · 无成功数据' : '读取中';
  if (health.phase === 'ready') return health.hasData ? '最近请求成功' : '请求成功 · 暂无数据';
  return '等待首次采样';
}
