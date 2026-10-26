import type { EvaluationMetricValueStatus, EvaluationSnapshot } from '../types/evaluation';

/** 仅用于实时评价对象区域；不重算评价公式、不修改快照、不维护历史。 */
export type DomainKey = 'vehicle' | 'cargo' | 'task';
type DisplayStatus = EvaluationMetricValueStatus | 'MISSING';
export interface DomainMetricDisplay {
  id: string;
  label: string;
  unit: string;
  status: DisplayStatus;
  value: number | null;
  text: string;
  reason: string;
  note: string;
  issue: string;
}
export interface DomainChartDatum {
  label: string;
  value: number;
  color: string;
  note?: string;
  threshold?: number;
}
export interface DomainChartModel {
  id: string;
  title: string;
  description: string;
  kind: 'nested-ring' | 'ring' | 'stack' | 'bars';
  state: 'ready' | 'empty' | 'unavailable' | 'invalid';
  message: string;
  notices: string[];
  unit: string;
  total: number;
  items: DomainChartDatum[];
  inner: DomainChartDatum[];
}
export interface DomainMetricSection {
  title: string;
  description: string;
  metrics: DomainMetricDisplay[];
}
export interface DomainViewModel {
  primary: DomainChartModel;
  charts: DomainChartModel[];
  statistics: DomainMetricDisplay[];
  ratios: DomainMetricDisplay[];
  sections: DomainMetricSection[];
}

export const DOMAIN_COLORS = Object.freeze({
  active: '#337ecc', complete: '#2f946b', pending: '#c68a2e',
  unassigned: '#8066b3', empty: '#168baf', loaded: '#278577',
  activeOther: '#657b98', other: '#a3adbb', warning: '#c68a2e'
});
export function domainStatusText(status: DisplayStatus): string {
  return ({ AVAILABLE: '可用', NOT_AVAILABLE: '暂不可用', NOT_SUPPORTED: '不支持',
    NOT_APPLICABLE: '不适用', INVALID: '无效', MISSING: '字段缺失' })[status];
}
export function domainNumber(value: number, digits = 2): string {
  return Number.isFinite(value)
    ? new Intl.NumberFormat('zh-CN', { maximumFractionDigits: digits }).format(value)
    : String(value);
}
export function domainUnit(unit: string): string {
  return ({ vehicle: '辆', task: '项', count: '次' } as Record<string, string>)[unit] || unit;
}
const countUnits = new Set(['vehicle', 'task', 'road', 'event', 'count']);
function tolerance(total: number): number { return Math.max(1e-9, Math.abs(total) * 1e-9); }

/** 保留原始状态和原值；数值范围问题只形成局部展示提示。 */
export function domainMetric(snapshot: EvaluationSnapshot, id: string, label: string, note = ''): DomainMetricDisplay {
  const raw = snapshot.metrics[id];
  const status = raw?.status || 'MISSING';
  const value = raw?.value ?? null;
  const unit = raw?.unit || '';
  let issue = '';
  if (status === 'AVAILABLE') {
    if (value === null || !Number.isFinite(value) || value < 0) issue = '数据不一致：数值须为非负有限值';
    else if (countUnits.has(unit) && !Number.isInteger(value)) issue = '数据不一致：数量须为整数';
    else if (unit === 'ratio' && value > 1) issue = '数据不一致：比例超出 0～100%';
  }
  const text = status !== 'AVAILABLE' || value === null ? '--'
    : unit === 'ratio' ? `${domainNumber(value * 100)}%`
    : `${domainNumber(value, countUnits.has(unit) ? 0 : 2)}${unit ? ` ${domainUnit(unit)}` : ''}`;
  return { id, label: raw?.displayName || label, unit, status, value, text,
    reason: raw?.reason || '', note, issue };
}
function usable(metric: DomainMetricDisplay): boolean {
  return metric.status === 'AVAILABLE' && metric.value !== null && !metric.issue;
}
function chart(id: string, title: string, description: string, kind: DomainChartModel['kind'], unit: string): DomainChartModel {
  return { id, title, description, kind, unit, state: 'ready', message: '', notices: [], total: 0, items: [], inner: [] };
}
function validateDependencies(model: DomainChartModel, metrics: DomainMetricDisplay[]): boolean {
  const failures = metrics.filter(metric => !usable(metric));
  if (!failures.length) return true;
  model.state = failures.some(metric => metric.issue || metric.status === 'INVALID') ? 'invalid' : 'unavailable';
  model.message = failures.map(metric => `${metric.label}：${metric.issue || domainStatusText(metric.status)}${metric.reason ? `；${metric.reason}` : ''}`).join('。');
  return false;
}
function invalidChart(model: DomainChartModel, message: string): DomainChartModel {
  return { ...model, state: 'invalid', message: `数据不一致：${message}。请展开完整指标明细核对原值。` };
}
function finishComposition(model: DomainChartModel, emptyText: string): DomainChartModel {
  if (model.total === 0) { model.state = 'empty'; model.message = emptyText; }
  return model;
}
function datum(label: string, value: number, color: string, note?: string): DomainChartDatum {
  return { label, value, color, note };
}

export function buildDomainViewModel(snapshot: EvaluationSnapshot, key: string): DomainViewModel | null {
  if (!['vehicle', 'cargo', 'task'].includes(key)) return null;
  const m = (id: string, label: string, note = '') => domainMetric(snapshot, id, label, note);
  const c = DOMAIN_COLORS;

  if (key === 'vehicle') {
    const total = m('vehicleTotalCount', '车辆总数');
    const active = m('vehicleInTransportCount', '运输中车辆数');
    const empty = m('vehicleEmptyDrivingCount', '空载行驶车辆数');
    const loaded = m('vehicleLoadedDrivingCount', '有载行驶车辆数');
    let primary = chart('vehicle-state', '车辆执行状态', '内环表示任务执行状态；外环细分执行中车辆。各层均以全部车辆为分母。', 'nested-ring', 'vehicle');
    if (validateDependencies(primary, [total, active, empty, loaded])) {
      primary.total = total.value!;
      const inactive = total.value! - active.value!;
      const otherActive = active.value! - empty.value! - loaded.value!;
      if (inactive < 0 || otherActive < 0) primary = invalidChart(primary, '执行中数量超过车辆总数，或行驶分类超过执行中数量');
      else {
        primary.inner = [datum('执行中', active.value!, c.active), datum('非执行中', inactive, c.other, '全部车辆减执行中车辆；不等同于可用车辆。')];
        primary.items = [datum('空载行驶', empty.value!, c.empty), datum('有载行驶', loaded.value!, c.loaded),
          datum('执行中其他', otherActive, c.activeOther, '执行中减空载行驶和有载行驶；未进一步区分装卸或等待状态。'),
          datum('非执行中', inactive, c.other, '全部车辆减执行中车辆；不等同于可用车辆。')];
        finishComposition(primary, '暂无车辆');
      }
    }
    const distance = m('vehicleTotalExecutedDistanceKm', '总实际行驶里程');
    const emptyDistance = m('vehicleEmptyExecutedDistanceKm', '空载实际行驶里程');
    let mileage = chart('vehicle-mileage', '实际行驶里程构成', '当前运行累计实际里程；非空载里程为总实际里程减空载里程。', 'stack', 'km');
    if (validateDependencies(mileage, [distance, emptyDistance])) {
      mileage.total = distance.value!;
      const rest = distance.value! - emptyDistance.value!;
      if (rest < -tolerance(mileage.total)) mileage = invalidChart(mileage, '空载里程超过总实际里程');
      else {
        mileage.items = [datum('空载里程', emptyDistance.value!, c.empty), datum('非空载里程', Math.max(0, rest), c.loaded, '总实际里程减空载里程，仅为展示差值。')];
        finishComposition(mileage, '暂无实际行驶里程');
      }
    }
    const capacity = m('vehicleRatedCapacityTonnes', '总额定载重');
    const load = m('vehicleCurrentLoadTonnes', '当前总载重');
    const remaining = m('vehicleRemainingCapacityTonnes', '当前剩余运力');
    let payload = chart('vehicle-capacity', '当前载重与剩余吨位', '剩余吨位不等于可立即分配的运力；包含当前仍在执行任务的车辆。', 'stack', 't');
    if (validateDependencies(payload, [capacity, load, remaining])) {
      payload.total = capacity.value!;
      if (Math.abs(load.value! + remaining.value! - capacity.value!) > tolerance(payload.total)) payload = invalidChart(payload, '当前载重与剩余运力之和不等于总额定载重');
      else {
        payload.items = [datum('当前载重', load.value!, c.loaded), datum('剩余吨位', remaining.value!, c.other)];
        finishComposition(payload, '暂无额定载重');
      }
    }
    return { primary, charts: [mileage, payload],
      statistics: [m('vehicleAvailableCount', '可用车辆数', '当前状态为 IDLE，不等同于非执行中车辆。'),
        m('vehicleEmptyMileageRatio', '车辆空载里程率', '当前运行累计空载里程占总实际里程比例。')],
      ratios: [m('vehicleDistanceWeightedLoadRatio', '里程加权载重率', '基于有载路段实际里程加权。'),
        m('vehicleLowLoadRatio', '低载车辆比例', `分母为当前正载重且正额定载重车辆；低载阈值 ${domainNumber(snapshot.thresholds.lowLoadRatioThreshold * 100)}%。`),
        m('vehicleFullLoadRatio', '满载车辆比例', `分母为当前正载重且正额定载重车辆；满载阈值 ${domainNumber(snapshot.thresholds.fullLoadRatioThreshold * 100)}%。`)],
      sections: [
        { title: '可用空闲等待', description: '车辆空闲等待用于解释资源利用，不参与货物和任务服务约束。', metrics: [m('vehicleCumulativeWaitSeconds', '车辆累计等待时间'), m('vehicleP95WaitSeconds', '车辆 P95 等待时间')] },
        { title: '累计能耗与碳排', description: '直接展示当前运行的后端累计事实。', metrics: [m('vehicleTotalEnergy', '车辆总能耗'), m('vehicleTotalEmissionKg', '车辆总碳排放'), m('vehicleEmissionIntensity', '车辆单位吨公里排放')] }
      ] };
  }

  if (key === 'cargo') {
    const total = m('cargoRequiredTonnes', '总运输需求吨位');
    const stages = [m('cargoUnassignedTonnes', '待分配货物吨位'), m('cargoAssignedNotLoadedTonnes', '已分配待装货吨位'), m('cargoInTransitTonnes', '运输中货物吨位'), m('cargoDeliveredTonnes', '已完成运输吨位')];
    let primary = chart('cargo-stages', '运输需求阶段构成', '当前运行全部需求吨位。超时未运输为交叉子集，不重复计入构成。', 'stack', 't');
    if (validateDependencies(primary, [total, ...stages])) {
      primary.total = total.value!;
      const rest = total.value! - stages.reduce((sum, metric) => sum + metric.value!, 0);
      if (rest < -tolerance(primary.total)) primary = invalidChart(primary, '阶段吨位之和超过总运输需求吨位');
      else {
        primary.items = stages.map((metric, index) => datum(['待分配', '待装货', '运输中', '已完成'][index], metric.value!, [c.unassigned, c.pending, c.active, c.complete][index]));
        primary.items.push(datum('其他需求', Math.max(0, rest), c.other, '总需求减已单列四类吨位；不进一步推断具体业务状态。'));
        finishComposition(primary, '暂无运输需求');
      }
    }
    const wait = waitingChart(snapshot, 'cargo-wait', '货物首次运输等待', '平均值与 P95 的样本口径不同；阈值仅用于 P95 服务等待对照。', [
      m('cargoAverageWaitSeconds', '平均货物等待时间'), m('cargoP95WaitSeconds', 'P95 货物等待时间')], 1);
    return { primary, charts: [wait],
      statistics: [m('cargoUnmetTonnes', '未达成吨位'), m('cargoOverdueUntransportedTonnes', '超时未运输吨位', '尚未开始首次有载运输且等待超过服务阈值；不是交付逾期。'), m('cargoExecutedTonneKm', '实际运输吨公里')],
      ratios: [m('cargoDeliveryAchievementRatio', '运输需求达成率', '已交付吨位占当前运行全部需求吨位比例。')], sections: [] };
  }

  const total = m('taskTotalCount', '当前任务总数');
  const states = [m('taskAssignedPendingCount', '已分配待执行任务数'), m('taskInProgressCount', '执行中任务数'), m('taskCompletedCount', '已完成任务数')];
  let primary = chart('task-states', '运输任务状态构成', '当前运行任务状态；其他任务包含未单列状态，不等同于未分配或失败任务。', 'ring', 'task');
  if (validateDependencies(primary, [total, ...states])) {
    primary.total = total.value!;
    const rest = total.value! - states.reduce((sum, metric) => sum + metric.value!, 0);
    if (rest < 0) primary = invalidChart(primary, '已单列任务数量之和超过任务总数');
    else {
      primary.items = states.map((metric, index) => datum(['已分配待执行', '执行中', '已完成'][index], metric.value!, [c.pending, c.active, c.complete][index]));
      primary.items.push(datum('其他任务', rest, c.other, '总任务数减已单列三类任务数；不推断具体状态。'));
      finishComposition(primary, '暂无运输任务');
    }
  }
  return { primary,
    charts: [waitingChart(snapshot, 'task-response', '任务响应与启动等待', '两项为不同阶段的独立平均值，样本可能不同；不相加、不堆叠。', [m('taskAverageResponseSeconds', '平均任务响应时间'), m('taskAverageStartWaitSeconds', '平均启动等待时间')]),
      waitingChart(snapshot, 'task-p95', '任务 P95 服务等待', '最早关联需求至首次实际路段推进的服务等待；与交付逾期不同。', [m('taskP95ServiceWaitSeconds', '任务 P95 服务等待时间')], 0)],
    statistics: [m('taskOverdueCount', '超时任务数', '当前仍有未交付货物且已超过其交付截止时刻的活动任务数。'), m('taskLowLoadCount', '低载任务数', '不以全部任务数推算比例。')],
    ratios: [m('taskCompletionRatio', '任务完成率', '已完成任务占当前运行全部任务比例。'), m('taskOnTimeCompletionRatio', '准时完成率', '分母为具有完整交付事实的已完成任务，不是全部任务。'), m('taskAverageLoadRatio', '任务平均载重率', '具有有效有载执行事实的任务载重率平均值。')],
    sections: [
      { title: '实际执行与空驶接驳', description: '当前运行累计事实；平均接驳距离使用具有实际接驳路段的任务样本。', metrics: [m('taskEmptyPickupDistanceKm', '任务空驶接驳距离'), m('taskAverageEmptyPickupDistanceKm', '平均空驶接驳距离'), m('taskTonneKm', '任务吨公里')] },
      { title: '累计碳排', description: '后端任务路段累计排放及单位有效吨公里排放。', metrics: [m('taskEmissionKg', '任务碳排放'), m('taskEmissionIntensity', '任务单位吨公里排放')] }
    ] };
}

function waitingChart(snapshot: EvaluationSnapshot, id: string, title: string, description: string,
  metrics: DomainMetricDisplay[], thresholdIndex?: number): DomainChartModel {
  const model = chart(id, title, description, 'bars', 's');
  const available = metrics.filter(usable);
  if (!available.length) { validateDependencies(model, metrics); return model; }
  // 独立等待统计不要求同时具备样本；不可用条不补零，可用条仍能展示。
  model.notices = metrics.filter(metric => !usable(metric)).map(metric =>
    `${metric.label}：${metric.issue || domainStatusText(metric.status)}${metric.reason ? `；${metric.reason}` : ''}`);
  const threshold = snapshot.thresholds.maxServiceWaitSeconds;
  if (thresholdIndex !== undefined && usable(metrics[thresholdIndex]) && (!Number.isFinite(threshold) || threshold < 0)) return invalidChart(model, '服务等待阈值不是非负有限值');
  model.items = metrics.filter(usable).map(metric => ({
    label: metric.label, value: metric.value!,
    color: metric === metrics[thresholdIndex ?? -1] && metric.value! > threshold ? DOMAIN_COLORS.warning : DOMAIN_COLORS.active,
    threshold: metric === metrics[thresholdIndex ?? -1] ? threshold : undefined
  }));
  // 0 秒是有效观察值，应绘制零基线并保留 0，不进入无数据状态。
  return model;
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[character]!);
}

/** ECharts 配置只投影展示模型，禁止隐藏图例后重新计算构成占比。 */
export function domainChartOption(model: DomainChartModel): Record<string, unknown> {
  const tooltip = (params: any): string => {
    const item: DomainChartDatum | undefined = params.data?.domainDatum;
    if (!item) return '';
    const isThreshold = params.seriesName === 'P95 阈值';
    const amount = isThreshold ? item.threshold! : item.value;
    const percent = model.kind !== 'bars' && model.total > 0 && !isThreshold
      ? `<br/>占总量：${domainNumber(item.value / model.total * 100)}%` : '';
    return `<strong>${escapeHtml(isThreshold ? `${item.label}阈值` : item.label)}</strong><br/>${domainNumber(amount)} ${escapeHtml(domainUnit(model.unit))}${percent}${item.note ? `<br/>${escapeHtml(item.note)}` : ''}`;
  };
  const base = { animation: false, aria: { enabled: true, description: model.title }, tooltip: { trigger: 'item', confine: true, formatter: tooltip } };
  if (model.kind === 'ring' || model.kind === 'nested-ring') {
    const series = (items: DomainChartDatum[], radius: string[]) => ({
      type: 'pie', radius, center: ['50%', '50%'], startAngle: 90, clockwise: true,
      selectedMode: false, stillShowZeroSum: false, minAngle: 0,
      label: { show: false }, labelLine: { show: false }, emphasis: { scale: false },
      data: items.map(item => ({ name: item.label, value: item.value, domainDatum: item, itemStyle: { color: item.color } }))
    });
    return { ...base,
      title: { text: domainNumber(model.total, 0), subtext: model.unit === 'vehicle' ? '车辆总数' : '任务总数', left: 'center', top: '40%', textStyle: { color: '#24394f', fontSize: 25, fontWeight: 600 }, subtextStyle: { color: '#718096', fontSize: 12 } },
      series: model.kind === 'nested-ring'
        ? [series(model.inner, ['38%', '57%']), series(model.items, ['62%', '80%'])]
        : [series(model.items, ['54%', '80%'])] };
  }
  const maximum = model.kind === 'stack' ? Math.max(model.total, model.items.reduce((sum, item) => sum + item.value, 0))
    : Math.max(0, ...model.items.map(item => Math.max(item.value, item.threshold ?? 0))) * 1.18;
  const xAxis = { type: 'value', min: 0, max: maximum || 1, name: model.unit, nameLocation: 'end', axisLabel: { hideOverlap: true, formatter: (value: number) => domainNumber(value) } };
  if (model.kind === 'stack') {
    return { ...base, grid: { left: 8, right: 38, top: 12, bottom: 32, containLabel: true }, xAxis,
      yAxis: { type: 'category', data: [''], axisLine: { show: false }, axisTick: { show: false } },
      series: model.items.map(item => ({ name: item.label, type: 'bar', stack: model.id, barMaxWidth: 38,
        data: [{ value: item.value, domainDatum: item }], itemStyle: { color: item.color }, emphasis: { disabled: true } })) };
  }
  const thresholds = model.items.filter(item => item.threshold !== undefined);
  return { ...base, grid: { left: 8, right: 55, top: 18, bottom: 34, containLabel: true }, xAxis,
    yAxis: { type: 'category', inverse: true, data: model.items.map(item => item.label), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { width: 140, overflow: 'break', fontSize: 11 } },
    series: [{ type: 'bar', barMaxWidth: 25,
      label: { show: true, position: 'right', formatter: (params: any) => domainNumber(Number(params.value)) },
      data: model.items.map(item => ({ value: item.value, domainDatum: item, itemStyle: { color: item.color } })) },
      ...(thresholds.length ? [{ name: 'P95 阈值', type: 'scatter', symbol: 'rect', symbolSize: [3, 25], z: 5,
        itemStyle: { color: DOMAIN_COLORS.warning }, data: thresholds.map(item => ({ value: [item.threshold, item.label], domainDatum: item })) }] : [])] };
}
