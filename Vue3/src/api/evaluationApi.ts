import axios from 'axios';
import request from '../utils/request';
import type {
  EvaluationApiResponse,
  EvaluationRunComparison,
  EvaluationRunSummary,
  EvaluationSnapshot,
  EvaluationTrend
} from '../types/evaluation';

/** Phase 6C：404 是“尚无完整 tick 快照”，不是网络故障。 */
export type LatestEvaluationResult =
  | { kind: 'snapshot'; snapshot: EvaluationSnapshot }
  | { kind: 'empty' };

/**
 * Phase 6C：只读取 Phase 6B 已发布的最新不可变快照。
 * 禁止在前端调用车辆、货物或任务接口拼装另一套评价结果。
 */
export async function fetchLatestEvaluationSnapshot(
  signal?: AbortSignal
): Promise<LatestEvaluationResult> {
  try {
    const response = await request.get<EvaluationApiResponse<EvaluationSnapshot>>(
      '/api/evaluation/latest',
      { signal }
    );
    const envelope = response.data;
    if (!envelope?.success || !isEvaluationSnapshot(envelope.data)) {
      throw new Error(envelope?.message || '评价快照响应结构无效');
    }
    return { kind: 'snapshot', snapshot: envelope.data };
  } catch (error: unknown) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return { kind: 'empty' };
    }
    throw error;
  }
}

/** Phase 6C：页面卸载导致的主动取消不显示为连接故障。 */
export function isEvaluationRequestCanceled(error: unknown): boolean {
  return axios.isCancel(error) || (axios.isAxiosError(error) && error.code === 'ERR_CANCELED');
}

/** Phase 9C：读取跨 reset 保留的运行摘要。 */
export async function fetchEvaluationRuns(): Promise<EvaluationRunSummary[]> {
  const response = await request.get<EvaluationApiResponse<EvaluationRunSummary[]>>(
    '/api/evaluation/runs'
  );
  return requireEvaluationData(response.data, '评价运行列表响应无效');
}

/** Phase 9C：完整历史用于快照表和指标目录，不从其它业务接口拼接。 */
export async function fetchEvaluationHistory(
  simulationRunId: string
): Promise<EvaluationSnapshot[]> {
  const response = await request.get<EvaluationApiResponse<EvaluationSnapshot[]>>(
    `/api/evaluation/runs/${encodeURIComponent(simulationRunId)}/history`
  );
  return requireEvaluationData(response.data, '评价历史响应无效');
}

/** Phase 9C：趋势接口只返回指定后端指标，不在浏览器中重算历史值。 */
export async function fetchEvaluationTrend(
  simulationRunId: string,
  metricIds: string[]
): Promise<EvaluationTrend> {
  const response = await request.get<EvaluationApiResponse<EvaluationTrend>>(
    `/api/evaluation/runs/${encodeURIComponent(simulationRunId)}/trend`,
    // Phase 9C：逗号分隔可被 Spring 稳定绑定为 List，避免 Axios 的 metricId[] 方言差异。
    { params: { metricId: metricIds.join(',') } }
  );
  return requireEvaluationData(response.data, '评价趋势响应无效');
}

/** Phase 9C：最终快照比较由后端完成，delta 统一为右侧减左侧。 */
export async function fetchEvaluationRunComparison(
  leftRunId: string,
  rightRunId: string
): Promise<EvaluationRunComparison> {
  const response = await request.get<EvaluationApiResponse<EvaluationRunComparison>>(
    '/api/evaluation/compare',
    { params: { leftRunId, rightRunId } }
  );
  return requireEvaluationData(response.data, '评价运行对比响应无效');
}

/** Phase 9C：下载只读取持久化历史；CSV 为宽表，JSON 保留完整快照契约。 */
export async function downloadEvaluationHistory(
  simulationRunId: string,
  format: 'csv' | 'json'
): Promise<Blob> {
  const response = await request.get(
    `/api/evaluation/runs/${encodeURIComponent(simulationRunId)}/export`,
    { params: { format }, responseType: 'blob' }
  );
  return response.data as Blob;
}

function requireEvaluationData<T>(
  envelope: EvaluationApiResponse<T>,
  fallbackMessage: string
): T {
  if (!envelope?.success || envelope.data === null || envelope.data === undefined) {
    throw new Error(envelope?.message || fallbackMessage);
  }
  return envelope.data;
}

/** Phase 6C：在进入展示层前校验决定版本顺序的最小必需字段。 */
function isEvaluationSnapshot(value: unknown): value is EvaluationSnapshot {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const candidate = value as Partial<EvaluationSnapshot>;
  return typeof candidate.simulationRunId === 'string'
    && candidate.simulationRunId.length > 0
    && Number.isInteger(candidate.snapshotRevision)
    && Number(candidate.snapshotRevision) > 0
    && Number.isInteger(candidate.loopIndex)
    && Number(candidate.loopIndex) >= 0
    // Phase 8：看板会直接读取模型版本；缺失模型元数据的旧快照不能进入展示层。
    && typeof candidate.energyEmissionModel === 'object'
    && candidate.energyEmissionModel !== null
    && typeof candidate.energyEmissionModel.modelId === 'string'
    && candidate.energyEmissionModel.modelId.length > 0
    && typeof candidate.metrics === 'object'
    && candidate.metrics !== null;
}
