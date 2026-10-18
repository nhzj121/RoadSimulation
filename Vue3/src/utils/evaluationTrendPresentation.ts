import type { EvaluationSnapshot } from '../types/evaluation';

/** 只为绘图插入一个缺口标记，不生成遗漏轮次的快照或指标。 */
export function evaluationTrendPoints(snapshot: EvaluationSnapshot, history: EvaluationSnapshot[]) {
  const ordered = history.filter(item => item.simulationRunId === snapshot.simulationRunId).slice()
    .sort((a, b) => a.snapshotRevision - b.snapshotRevision);
  const points: Array<{ snapshot: EvaluationSnapshot | null; label: string }> = [];
  ordered.forEach((item, index) => {
    const previous = ordered[index - 1];
    if (previous && item.loopIndex > previous.loopIndex + 1) {
      points.push({ snapshot: null, label: `未采集 Loop ${previous.loopIndex + 1}–${item.loopIndex - 1}` });
    }
    points.push({ snapshot: item, label: `Loop ${item.loopIndex}` });
  });
  return points;
}
