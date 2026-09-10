package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.service.TransportProgressResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Phase 6B：把运输推进结果转换为评价快照可消费的结构化本轮执行报告。 */
public record EvaluationLoopExecutionReport(
        int processedAssignmentCount,
        int failedAssignmentCount,
        boolean candidateQueryFailed,
        List<String> errorCodes
) {
    public static final String ASSIGNMENT_PROGRESS_FAILED = "TRANSPORT_PROGRESS_ASSIGNMENT_FAILED";
    public static final String CANDIDATE_QUERY_FAILED = "TRANSPORT_PROGRESS_CANDIDATE_QUERY_FAILED";

    public EvaluationLoopExecutionReport {
        if (processedAssignmentCount < 0 || failedAssignmentCount < 0
                || failedAssignmentCount > processedAssignmentCount) {
            throw new IllegalArgumentException("invalid assignment processing counters");
        }
        errorCodes = List.copyOf(errorCodes == null ? List.of() : errorCodes);
    }

    /** Phase 6B：单任务隔离失败形成 PARTIAL 所需计数；正常暂停和幂等跳过仍属于已处理。 */
    public static EvaluationLoopExecutionReport fromResults(List<TransportProgressResult> results) {
        List<TransportProgressResult> safeResults = results == null ? List.of() : results;
        int failures = Math.toIntExact(safeResults.stream()
                // Phase 6B：异常 null 结果同样视为任务级失败，不能让报告构造异常伪装成查询级故障。
                .filter(result -> result == null
                        || result.outcome() == TransportProgressResult.Outcome.FAILED)
                .count());
        Set<String> codes = new LinkedHashSet<>();
        if (failures > 0) {
            codes.add(ASSIGNMENT_PROGRESS_FAILED);
        }
        return new EvaluationLoopExecutionReport(safeResults.size(), failures, false, List.copyOf(codes));
    }

    /** Phase 6B：候选集合本身无法读取时，本轮整体推进覆盖范围未知，快照必须标记 FAILED。 */
    public static EvaluationLoopExecutionReport candidateQueryFailure() {
        return new EvaluationLoopExecutionReport(0, 0, true, List.of(CANDIDATE_QUERY_FAILED));
    }
}
