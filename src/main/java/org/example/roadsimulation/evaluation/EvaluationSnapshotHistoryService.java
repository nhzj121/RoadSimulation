package org.example.roadsimulation.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.repository.EvaluationSnapshotHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase 9C：保存和查询评价快照历史，并提供趋势、最终快照对比和导出。
 *
 * <p>该服务只访问 {@code evaluation_snapshot_history}。它不读取当前运输实体，因此历史查询
 * 不会因 reset 后业务表被清理而改变结果。</p>
 */
@Service
public class EvaluationSnapshotHistoryService {

    private static final List<String> DEFAULT_TREND_METRICS = List.of(
            "emptyMileageRatio",
            "capacityWasteRatio",
            "unmetDemandRatio",
            "carbonIntensity",
            "waitingServiceCompliant"
    );

    private final EvaluationSnapshotHistoryRepository repository;
    private final ObjectMapper objectMapper;
    private final Set<String> knownMetricIds;

    public EvaluationSnapshotHistoryService(
            EvaluationSnapshotHistoryRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.knownMetricIds = Arrays.stream(EvaluationMetricId.values())
                .map(EvaluationMetricId::getMetricId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Phase 9C：独立事务保存一轮快照，失败不得回滚已经完成的运输业务。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(EvaluationSnapshot snapshot) {
        try {
            String payload = objectMapper.writeValueAsString(snapshot);
            repository.saveAndFlush(EvaluationSnapshotHistoryRecord.of(snapshot, payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("evaluation snapshot serialization failed", ex);
        }
    }

    /** Phase 9C：运行列表由持久化元数据聚合，不依赖当前内存 latest。 */
    @Transactional(readOnly = true)
    public List<EvaluationHistoryView.RunSummary> listRuns() {
        Map<String, List<EvaluationSnapshotHistoryRepository.MetadataProjection>> grouped = repository
                .findAllMetadataOrderByRunAndRevision()
                .stream()
                .collect(Collectors.groupingBy(
                        EvaluationSnapshotHistoryRepository.MetadataProjection::getSimulationRunId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return grouped.values().stream()
                .map(this::summarizeMetadata)
                .sorted(Comparator.comparing(EvaluationHistoryView.RunSummary::lastSimTime)
                        .reversed()
                        .thenComparing(EvaluationHistoryView.RunSummary::simulationRunId))
                .toList();
    }

    /** Phase 9C：返回一个运行的全部完整快照，顺序固定为 revision 升序。 */
    @Transactional(readOnly = true)
    public List<EvaluationSnapshot> history(String simulationRunId) {
        return recordsFor(simulationRunId).stream().map(this::decode).toList();
    }

    /** Phase 9C：趋势只裁剪指标列，不重新计算或插值任何后端指标。 */
    @Transactional(readOnly = true)
    public EvaluationHistoryView.Trend trend(String simulationRunId, List<String> requestedMetricIds) {
        List<EvaluationSnapshotHistoryRecord> records = recordsFor(simulationRunId);
        List<String> metricIds = normalizeMetricIds(requestedMetricIds);
        List<EvaluationHistoryView.TrendPoint> points = records.stream()
                .map(this::decode)
                .map(snapshot -> {
                    LinkedHashMap<String, EvaluationMetricValue> selected = new LinkedHashMap<>();
                    metricIds.forEach(id -> selected.put(id, snapshot.metrics().get(id)));
                    return new EvaluationHistoryView.TrendPoint(
                            snapshot.loopIndex(),
                            snapshot.snapshotRevision(),
                            snapshot.simTime(),
                            snapshot.snapshotStatus(),
                            Map.copyOf(selected)
                    );
                })
                .toList();
        return new EvaluationHistoryView.Trend(summarize(records), metricIds, points);
    }

    /** Phase 9C：只比较两个运行的最终 revision，不对不同长度运行做时间轴对齐。 */
    @Transactional(readOnly = true)
    public EvaluationHistoryView.RunComparison compareFinalSnapshots(String leftRunId, String rightRunId) {
        if (requireRunId(leftRunId).equals(requireRunId(rightRunId))) {
            throw new IllegalArgumentException("comparison requires two different simulation runs");
        }
        List<EvaluationSnapshotHistoryRecord> leftRecords = recordsFor(leftRunId);
        List<EvaluationSnapshotHistoryRecord> rightRecords = recordsFor(rightRunId);
        EvaluationSnapshot left = decode(leftRecords.get(leftRecords.size() - 1));
        EvaluationSnapshot right = decode(rightRecords.get(rightRecords.size() - 1));

        // Phase 9C：不同契约版本可能具有不同指标公式或单位，禁止把跨口径差值伪装成可比结果。
        if (!left.contractVersion().equals(right.contractVersion())) {
            throw new IllegalArgumentException(
                    "comparison requires the same evaluation contract version: left="
                            + left.contractVersion() + ", right=" + right.contractVersion());
        }

        List<EvaluationHistoryView.MetricComparison> comparisons = Arrays.stream(EvaluationMetricId.values())
                .map(id -> compareMetric(id, left.metrics().get(id.getMetricId()),
                        right.metrics().get(id.getMetricId())))
                .toList();
        return new EvaluationHistoryView.RunComparison(
                summarize(leftRecords),
                summarize(rightRecords),
                left,
                right,
                comparisons
        );
    }

    /** Phase 9C：JSON 导出保留指标状态、原因、阈值、模型版本和错误码。 */
    @Transactional(readOnly = true)
    public byte[] exportJson(String simulationRunId) {
        List<EvaluationSnapshotHistoryRecord> records = recordsFor(simulationRunId);
        EvaluationHistoryView.HistoryExport export = new EvaluationHistoryView.HistoryExport(
                summarize(records), records.stream().map(this::decode).toList());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("evaluation history JSON export failed", ex);
        }
    }

    /** Phase 9C：CSV 每轮一行，每项指标输出数值列和状态列，便于 Excel 横向分析。 */
    @Transactional(readOnly = true)
    public byte[] exportCsv(String simulationRunId) {
        List<EvaluationSnapshot> snapshots = history(simulationRunId);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("contractVersion,simulationRunId,runKind,loopIndex,snapshotRevision,simTime,")
                .append("snapshotStatus,processedAssignmentCount,failedAssignmentCount,errorCodes");
        for (EvaluationMetricId id : EvaluationMetricId.values()) {
            csv.append(',').append(id.getMetricId())
                    .append(',').append(id.getMetricId()).append("__status");
        }
        csv.append("\r\n");

        for (EvaluationSnapshot snapshot : snapshots) {
            appendCsvCell(csv, snapshot.contractVersion());
            appendCsvCell(csv, snapshot.simulationRunId());
            appendCsvCell(csv, snapshot.runKind().name());
            appendCsvCell(csv, snapshot.loopIndex());
            appendCsvCell(csv, snapshot.snapshotRevision());
            appendCsvCell(csv, snapshot.simTime());
            appendCsvCell(csv, snapshot.snapshotStatus().name());
            appendCsvCell(csv, snapshot.processedAssignmentCount());
            appendCsvCell(csv, snapshot.failedAssignmentCount());
            appendCsvCell(csv, String.join("|", snapshot.errorCodes()));
            for (EvaluationMetricId id : EvaluationMetricId.values()) {
                EvaluationMetricValue metric = snapshot.metrics().get(id.getMetricId());
                appendCsvCell(csv, metric.status() == EvaluationMetricValueStatus.AVAILABLE
                        ? metric.value() : "");
                appendCsvCell(csv, metric.status().name());
            }
            csv.append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<EvaluationSnapshotHistoryRecord> recordsFor(String simulationRunId) {
        String runId = requireRunId(simulationRunId);
        List<EvaluationSnapshotHistoryRecord> records = repository
                .findAllBySimulationRunIdOrderBySnapshotRevisionAsc(runId);
        if (records.isEmpty()) {
            throw new NoSuchElementException("evaluation run history not found: " + runId);
        }
        return records;
    }

    private EvaluationHistoryView.RunSummary summarize(List<EvaluationSnapshotHistoryRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("history records are required");
        }
        EvaluationSnapshotHistoryRecord first = records.get(0);
        EvaluationSnapshotHistoryRecord last = records.get(records.size() - 1);
        return new EvaluationHistoryView.RunSummary(
                first.getSimulationRunId(),
                last.getRunKind(),
                last.getContractVersion(),
                first.getLoopIndex(),
                last.getLoopIndex(),
                records.size(),
                first.getSimTime(),
                last.getSimTime(),
                last.getSnapshotStatus()
        );
    }

    private EvaluationHistoryView.RunSummary summarizeMetadata(
            List<EvaluationSnapshotHistoryRepository.MetadataProjection> records
    ) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("history metadata records are required");
        }
        EvaluationSnapshotHistoryRepository.MetadataProjection first = records.get(0);
        EvaluationSnapshotHistoryRepository.MetadataProjection last = records.get(records.size() - 1);
        return new EvaluationHistoryView.RunSummary(
                first.getSimulationRunId(),
                last.getRunKind(),
                last.getContractVersion(),
                first.getLoopIndex(),
                last.getLoopIndex(),
                records.size(),
                first.getSimTime(),
                last.getSimTime(),
                last.getSnapshotStatus()
        );
    }

    private EvaluationSnapshot decode(EvaluationSnapshotHistoryRecord record) {
        try {
            EvaluationSnapshot snapshot = objectMapper.readValue(
                    record.getSnapshotPayloadJson(), EvaluationSnapshot.class);
            boolean metadataMatches = record.getSimulationRunId().equals(snapshot.simulationRunId())
                    && record.getContractVersion().equals(snapshot.contractVersion())
                    && record.getRunKind() == snapshot.runKind()
                    && record.getLoopIndex() == snapshot.loopIndex()
                    && record.getSnapshotRevision() == snapshot.snapshotRevision()
                    && record.getSimTime().equals(snapshot.simTime())
                    && record.getSnapshotStatus() == snapshot.snapshotStatus();
            if (!metadataMatches) {
                throw new IllegalStateException("evaluation history metadata does not match payload");
            }
            return snapshot;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("evaluation history payload is unreadable", ex);
        }
    }

    private EvaluationHistoryView.MetricComparison compareMetric(
            EvaluationMetricId id,
            EvaluationMetricValue left,
            EvaluationMetricValue right
    ) {
        if (left == null || right == null) {
            throw new IllegalStateException("comparison snapshot is missing metric " + id.getMetricId());
        }
        Double delta = null;
        Double relative = null;
        if (left.status() == EvaluationMetricValueStatus.AVAILABLE
                && right.status() == EvaluationMetricValueStatus.AVAILABLE) {
            delta = right.value() - left.value();
            if (left.value() != 0.0) {
                relative = delta / Math.abs(left.value());
            }
        }
        return new EvaluationHistoryView.MetricComparison(
                id.getMetricId(), left.displayName(), left.category(), left.unit(),
                left.status(), left.value(), right.status(), right.value(), delta, relative);
    }

    private List<String> normalizeMetricIds(List<String> requestedMetricIds) {
        List<String> candidates = requestedMetricIds == null || requestedMetricIds.isEmpty()
                ? DEFAULT_TREND_METRICS
                : requestedMetricIds;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            String metricId = candidate.trim();
            if (!knownMetricIds.contains(metricId)) {
                throw new IllegalArgumentException("unknown evaluation metric: " + metricId);
            }
            normalized.add(metricId);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("at least one metricId is required");
        }
        return List.copyOf(normalized);
    }

    private String requireRunId(String simulationRunId) {
        if (simulationRunId == null || simulationRunId.isBlank()) {
            throw new IllegalArgumentException("simulationRunId must not be blank");
        }
        return simulationRunId.trim();
    }

    private void appendCsvCell(StringBuilder csv, Object value) {
        if (csv.charAt(csv.length() - 1) != '\n' && csv.charAt(csv.length() - 1) != '\uFEFF') {
            csv.append(',');
        }
        String text = value == null ? "" : String.valueOf(value);
        // Phase 9C：文本首字符可能被 Excel 当作公式；导出前显式转为文本。
        if (value instanceof String && !text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        csv.append('"').append(text.replace("\"", "\"\"")).append('"');
    }
}
