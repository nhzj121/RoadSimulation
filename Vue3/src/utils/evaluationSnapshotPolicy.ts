import type { EvaluationSnapshot } from '../types/evaluation';

/**
 * Phase 6C：判断轮询结果是否代表一个新的可展示快照。
 *
 * 同一 simulationRunId 只接受更大的 revision；运行切换时后端 revision 可以重新从 1 开始。
 * 该规则只负责前端防倒退，不生成、修正或合并任何评价指标。
 */
export function shouldAcceptEvaluationSnapshot(
  current: EvaluationSnapshot | null,
  incoming: EvaluationSnapshot
): boolean {
  return current === null
    || current.simulationRunId !== incoming.simulationRunId
    || incoming.snapshotRevision > current.snapshotRevision;
}
