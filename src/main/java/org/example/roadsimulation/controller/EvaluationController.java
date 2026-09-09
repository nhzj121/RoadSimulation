package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.evaluation.EvaluationSnapshot;
import org.example.roadsimulation.evaluation.EvaluationSnapshotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 6B：评价快照只读接口；动态展示接入留在 Phase 6C。 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationSnapshotService snapshotService;

    public EvaluationController(EvaluationSnapshotService snapshotService) {
        // Phase 6B：构造器强制注入，禁止控制器回退为按请求临时计算。
        this.snapshotService = snapshotService;
    }

    /** Phase 6B：没有已完成 tick 时返回 404；存在时原样返回最新不可变 revision。 */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<EvaluationSnapshot>> latest() {
        return snapshotService.latest()
                .map(snapshot -> ResponseEntity.ok(ApiResponse.success("evaluation snapshot found", snapshot)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("evaluation snapshot is not available")));
    }
}
