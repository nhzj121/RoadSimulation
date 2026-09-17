package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.evaluation.EvaluationHistoryView;
import org.example.roadsimulation.evaluation.EvaluationSnapshot;
import org.example.roadsimulation.evaluation.EvaluationSnapshotHistoryService;
import org.example.roadsimulation.evaluation.EvaluationSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/** Phase 6B：评价快照只读接口；动态展示接入留在 Phase 6C。 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationSnapshotService snapshotService;
    private final EvaluationSnapshotHistoryService historyService;

    public EvaluationController(EvaluationSnapshotService snapshotService) {
        this(snapshotService, null);
    }

    @Autowired
    public EvaluationController(
            EvaluationSnapshotService snapshotService,
            EvaluationSnapshotHistoryService historyService
    ) {
        // Phase 6B：构造器强制注入，禁止控制器回退为按请求临时计算。
        this.snapshotService = snapshotService;
        // Phase 9C：历史接口只读取持久化快照，不扫描当前运输实体。
        this.historyService = historyService;
    }

    /** Phase 6B：没有已完成 tick 时返回 404；存在时原样返回最新不可变 revision。 */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<EvaluationSnapshot>> latest() {
        return snapshotService.latest()
                .map(snapshot -> ResponseEntity.ok(ApiResponse.success("evaluation snapshot found", snapshot)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("evaluation snapshot is not available")));
    }

    /** Phase 9C：列出跨 reset 保留的评价运行摘要，最近运行优先。 */
    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<EvaluationHistoryView.RunSummary>>> runs() {
        return ResponseEntity.ok(ApiResponse.success(
                "evaluation runs found", requireHistoryService().listRuns()));
    }

    /** Phase 9C：读取一个运行的完整逐轮快照，不在接口线程重算指标。 */
    @GetMapping("/runs/{simulationRunId}/history")
    public ResponseEntity<ApiResponse<List<EvaluationSnapshot>>> history(
            @PathVariable String simulationRunId
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "evaluation history found", requireHistoryService().history(simulationRunId)));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
        }
    }

    /** Phase 9C：趋势结果只裁剪指定指标，不执行时间插值或前端口径计算。 */
    @GetMapping("/runs/{simulationRunId}/trend")
    public ResponseEntity<ApiResponse<EvaluationHistoryView.Trend>> trend(
            @PathVariable String simulationRunId,
            @RequestParam(name = "metricId", required = false) List<String> metricIds
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "evaluation trend found",
                    requireHistoryService().trend(simulationRunId, metricIds)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
        }
    }

    /** Phase 9C：比较两个运行各自最终 revision，差值统一为右侧减左侧。 */
    @GetMapping("/compare")
    public ResponseEntity<ApiResponse<EvaluationHistoryView.RunComparison>> compare(
            @RequestParam String leftRunId,
            @RequestParam String rightRunId
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "evaluation run comparison found",
                    requireHistoryService().compareFinalSnapshots(leftRunId, rightRunId)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
        }
    }

    /** Phase 9C：导出 CSV 宽表或保留全部元数据的 JSON；导出不改变任何运行状态。 */
    @GetMapping("/runs/{simulationRunId}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String simulationRunId,
            @RequestParam(defaultValue = "csv") String format
    ) {
        try {
            String normalized = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);
            byte[] body;
            MediaType contentType;
            String extension;
            if ("csv".equals(normalized)) {
                body = requireHistoryService().exportCsv(simulationRunId);
                contentType = MediaType.parseMediaType("text/csv;charset=UTF-8");
                extension = "csv";
            } else if ("json".equals(normalized)) {
                body = requireHistoryService().exportJson(simulationRunId);
                contentType = MediaType.APPLICATION_JSON;
                extension = "json";
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "format must be csv or json");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("evaluation-" + safeFilePart(simulationRunId) + "." + extension,
                            StandardCharsets.UTF_8)
                    .build());
            return new ResponseEntity<>(body, headers, HttpStatus.OK);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    private EvaluationSnapshotHistoryService requireHistoryService() {
        if (historyService == null) {
            throw new IllegalStateException("evaluation history service is unavailable");
        }
        return historyService;
    }

    private String safeFilePart(String runId) {
        String safe = runId == null ? "unknown" : runId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.isBlank() ? "unknown" : safe;
    }
}
