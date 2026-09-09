package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.core.SimulationTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 6B：管理评价运行生命周期、快照版本和内存中的最新不可变快照。
 *
 * <p>stop/start 不调用清理方法，因而自然保留 runId 与 revision。</p>
 */
@Service
public class EvaluationSnapshotService {

    public static final String FACT_COLLECTION_FAILED = "EVALUATION_FACT_COLLECTION_FAILED";
    private static final Logger log = LoggerFactory.getLogger(EvaluationSnapshotService.class);

    private final Object lifecycleMonitor = new Object();
    private final AtomicReference<EvaluationSnapshot> latestSnapshot = new AtomicReference<>();
    private final EvaluationSnapshotCalculator calculator;
    private final EvaluationMetricPolicy policy;
    private RunState activeRun;

    public EvaluationSnapshotService(
            EvaluationSnapshotCalculator calculator,
            EvaluationMetricPolicy policy
    ) {
        // Phase 6B：快照服务只依赖只读计算器与阈值，不持有或修改运输实体。
        this.calculator = calculator;
        this.policy = policy;
    }

    /** Phase 6B：普通仿真首次 start/step 创建 UUID；暂停后恢复不会重建运行。 */
    public String beginRegularRunIfAbsent() {
        synchronized (lifecycleMonitor) {
            if (activeRun != null) {
                if (activeRun.kind != EvaluationRunKind.REGULAR) {
                    throw new IllegalStateException("dispatch comparison evaluation run is still active");
                }
                return activeRun.runId;
            }
            activeRun = new RunState(UUID.randomUUID().toString(), EvaluationRunKind.REGULAR);
            // Phase 6B：新运行在首轮完成前没有 latest，禁止暴露上一运行的陈旧快照。
            latestSnapshot.set(null);
            return activeRun.runId;
        }
    }

    /** Phase 6B：每个 ORIGINAL/HEURISTIC 策略运行使用独立、可追溯的评价 runId。 */
    public String beginComparisonRun(long experimentRunId, long strategyRunId) {
        if (experimentRunId <= 0L || strategyRunId <= 0L) {
            throw new IllegalArgumentException("comparison run ids must be positive");
        }
        synchronized (lifecycleMonitor) {
            String runId = "comparison:" + experimentRunId + ":" + strategyRunId;
            activeRun = new RunState(runId, EvaluationRunKind.DISPATCH_COMPARISON);
            // Phase 6B：latest 只代表当前策略，策略切换时不能继续返回上一策略快照。
            latestSnapshot.set(null);
            return runId;
        }
    }

    /** Phase 6B：实验结束后关闭活动上下文，但保留最后一轮快照供读取。 */
    public void endCurrentRunPreservingLatest() {
        synchronized (lifecycleMonitor) {
            activeRun = null;
        }
    }

    /** Phase 6B：显式 reset 同时清除运行上下文与最新快照；下一次 revision 从 1 开始。 */
    public void reset() {
        synchronized (lifecycleMonitor) {
            activeRun = null;
            latestSnapshot.set(null);
        }
    }

    /**
     * Phase 6B：在业务 tick 完成后生成且原子发布一个快照。
     *
     * <p>采集异常不会回滚已完成的运输业务；服务改为发布结构完整的 FAILED 快照。</p>
     */
    public EvaluationSnapshot captureCompletedTick(
            SimulationTick tick,
            EvaluationLoopExecutionReport executionReport
    ) {
        if (tick == null) {
            throw new IllegalArgumentException("simulation tick is required");
        }
        EvaluationLoopExecutionReport report = executionReport == null
                ? EvaluationLoopExecutionReport.fromResults(List.of())
                : executionReport;

        synchronized (lifecycleMonitor) {
            // Phase 6B：单步接口可在没有 start 的情况下直接建立普通运行。
            if (activeRun == null) {
                activeRun = new RunState(UUID.randomUUID().toString(), EvaluationRunKind.REGULAR);
                latestSnapshot.set(null);
            }

            long revision = ++activeRun.revision;
            EvaluationSnapshotCalculator.Calculation calculation;
            boolean collectionFailed = false;
            try {
                // Phase 7B：把当前窗口显式交给计算器，节点吞吐量不从墙上时间或 revision 反推。
                calculation = calculator.calculate(tick);
            } catch (Exception ex) {
                collectionFailed = true;
                log.error("[Phase6B Evaluation] fact collection failed at loop={}", tick.loopIndex(), ex);
                Map<String, EvaluationMetricValue> failedValues =
                        calculator.failedMetricValues("评价事实采集失败，本轮无法计算指标");
                calculation = new EvaluationSnapshotCalculator.Calculation(
                        failedValues, true, List.of(FACT_COLLECTION_FAILED));
            }

            Set<String> errorCodes = new LinkedHashSet<>(report.errorCodes());
            errorCodes.addAll(calculation.errorCodes());
            EvaluationSnapshotStatus status = resolveStatus(report, calculation, collectionFailed);
            EvaluationSnapshot snapshot = new EvaluationSnapshot(
                    EvaluationMetricCatalog.CONTRACT_VERSION,
                    activeRun.runId,
                    activeRun.kind,
                    tick.loopIndex(),
                    revision,
                    // Phase 6B：快照描述本轮处理结束状态，因此逻辑时刻固定为 tickEnd。
                    tick.tickEnd(),
                    status,
                    report.processedAssignmentCount(),
                    report.failedAssignmentCount(),
                    List.copyOf(errorCodes),
                    new EvaluationThresholdSnapshot(
                            policy.getLowLoadRatioThreshold(),
                            policy.getFullLoadRatioThreshold(),
                            policy.getMaxServiceWaitSeconds()
                    ),
                    calculation.metrics()
            );
            // Phase 6B：单次原子替换使并发 GET 只能看到旧完整快照或新完整快照。
            latestSnapshot.set(snapshot);
            return snapshot;
        }
    }

    /** Phase 6B：HTTP 层只读取内存对象，不触发数据库查询和二次计算。 */
    public Optional<EvaluationSnapshot> latest() {
        return Optional.ofNullable(latestSnapshot.get());
    }

    private EvaluationSnapshotStatus resolveStatus(
            EvaluationLoopExecutionReport report,
            EvaluationSnapshotCalculator.Calculation calculation,
            boolean collectionFailed
    ) {
        if (collectionFailed || report.candidateQueryFailed()) {
            return EvaluationSnapshotStatus.FAILED;
        }
        if (report.failedAssignmentCount() > 0 || calculation.degraded()) {
            return EvaluationSnapshotStatus.PARTIAL;
        }
        return EvaluationSnapshotStatus.COMPLETE;
    }

    /** Phase 6B：revision 只在成功发布一轮（含 FAILED 快照）时递增，且永不跨 run 复用。 */
    private static final class RunState {
        private final String runId;
        private final EvaluationRunKind kind;
        private long revision;

        private RunState(String runId, EvaluationRunKind kind) {
            this.runId = runId;
            this.kind = kind;
        }
    }
}
