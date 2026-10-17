import type { EvaluationSnapshot } from '../types/evaluation';
import type { DomainChartModel } from './evaluationDomainPresentation';
import { toChartValue } from './evaluationPresentation';
import { evaluationTrendPoints } from './evaluationTrendPresentation';

// 展示专用分组：两种车辆图模式使用相同颜色；不新增业务状态。
export const VISIBLE_VEHICLE_GROUPS = [
  { label: '空驶', statuses: ['ORDER_DRIVING'], color: '#3B82F6', note: '' },
  { label: '有载', statuses: ['TRANSPORT_DRIVING'], color: '#16A34A', note: '' },
  { label: '装货', statuses: ['LOADING'], color: '#F97316', note: '' },
  { label: '卸货', statuses: ['UNLOADING'], color: '#EAB308', note: '' },
  { label: '等待', statuses: ['WAITING'], color: '#94A3B8', note: '本图主要为前端卸货后收尾等待，不等同于后端任务完成次数' },
  { label: '异常', statuses: ['BREAKDOWN', 'SCRAPPED', 'RESERVED_REPLACEMENT'], color: '#DC2626', note: '含故障、报废和替换准备，不仅是正在故障' },
  { label: '其他', statuses: [] as string[], color: '#8B5CF6', note: '' }
];

export interface VisibleVehicleDisplay { id?: number | string; vehicleId?: number | string; status?: string }

/** 只对既有地图可见车辆投影计数，不读取业务接口、不修改车辆或动画。 */
export function buildVisibleVehicleChart(vehicles: VisibleVehicleDisplay[]): DomainChartModel {
  const groups = VISIBLE_VEHICLE_GROUPS;
  const seen = new Set<string>(), counts = groups.map(() => 0), statuses = groups.map(() => new Set<string>());
  for (const vehicle of vehicles) {
    const id = vehicle.vehicleId ?? vehicle.id;
    if (id === null || id === undefined || !String(id).trim() || seen.has(String(id))) continue;
    seen.add(String(id));
    const status = vehicle.status || 'UNKNOWN';
    const matched = groups.findIndex(group => group.statuses.includes(status));
    const index = matched < 0 ? groups.length - 1 : matched;
    counts[index]++;
    statuses[index].add(status);
  }
  return {
    id: 'map-visible-vehicles', title: '地图可见车辆状态', description: '按地图已绘制任务的可见车辆去重；含装卸、暂停及异常中的可见车辆，不是后端车队总数。',
    kind: 'ring', unit: 'vehicle', state: seen.size ? 'ready' : 'empty', message: '地图上暂无可见任务车辆', notices: [],
    total: seen.size, inner: [],
    items: groups.map((group, index) => ({ label: group.label, value: counts[index], color: group.color,
      note: [counts[index] ? `已有展示状态：${[...statuses[index]].sort().join('、')}` : '当前为 0，不绘制扇区', group.note].filter(Boolean).join('；') }))
  };
}

/** 只覆盖主仿真左栏空态文字；保留原成本监控选项、数据、Tooltip 和 P95 线。 */
export function mainWorkspaceCostOption(option: Record<string, unknown>): Record<string, unknown> {
  return { ...option, graphic: [] };
}

/** 四目标小趋势仅转换当前运行的已有快照；缺失/异常点保留 null，不补零。 */
export function objectiveSparkline(snapshot: EvaluationSnapshot, history: EvaluationSnapshot[], metricId: string, color: string): Record<string, unknown> | null {
  const ordered = evaluationTrendPoints(snapshot, history);
  const values = ordered.map(item => {
    const metric = item.snapshot?.metrics?.[metricId];
    return metric?.status === 'AVAILABLE' && metric.value !== null && Number.isFinite(metric.value)
      ? toChartValue(metric.value, metric.unit) : null;
  });
  if (!values.some(value => value !== null)) return null;
  const unit = snapshot.metrics?.[metricId]?.unit || ordered.map(item => item.snapshot?.metrics?.[metricId]?.unit).find(Boolean) || '';
  const ratio = unit === 'ratio';
  return {
    animation: false, color: [color], grid: { left: 3, right: 3, top: 5, bottom: 3 },
    tooltip: { trigger: 'axis', confine: true, valueFormatter: (value: number | null) => value === null ? '无可用值'
      : `${Number(value).toFixed(2)}${ratio ? '%' : ` ${unit}`}` },
    xAxis: { type: 'category', boundaryGap: false, show: false, data: ordered.map(item => item.label) },
    yAxis: { type: 'value', show: false, min: 0, ...(ratio ? { max: 100 } : {}) },
    series: [{ name: snapshot.metrics?.[metricId]?.displayName || metricId, type: 'line', smooth: false, connectNulls: false,
      showSymbol: values.length === 1, symbolSize: 4, lineStyle: { width: 1.5 }, data: values }]
  };
}
