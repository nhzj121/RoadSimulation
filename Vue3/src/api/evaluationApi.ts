import axios from 'axios';
import request from '../utils/request';
import type { EvaluationApiResponse, EvaluationSnapshot } from '../types/evaluation';

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
    && typeof candidate.metrics === 'object'
    && candidate.metrics !== null;
}
