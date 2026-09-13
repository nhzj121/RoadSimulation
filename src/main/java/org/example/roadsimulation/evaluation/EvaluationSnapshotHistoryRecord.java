package org.example.roadsimulation.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Phase 9C：持久化一轮完整评价快照，供趋势、跨运行对比和导出使用。
 *
 * <p>该表只保存评价投影，不建立业务外键，也不参与仿真 reset。运输业务表被清理后，
 * 历史快照仍可按当时的契约版本独立读取。</p>
 */
@Entity
@Table(
        name = "evaluation_snapshot_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_evaluation_history_run_revision",
                columnNames = {"simulation_run_id", "snapshot_revision"}
        ),
        indexes = {
                @Index(name = "idx_evaluation_history_run_loop",
                        columnList = "simulation_run_id,loop_index"),
                @Index(name = "idx_evaluation_history_run_time",
                        columnList = "simulation_run_id,sim_time")
        }
)
public class EvaluationSnapshotHistoryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_version", nullable = false, length = 24)
    private String contractVersion;

    @Column(name = "simulation_run_id", nullable = false, length = 100)
    private String simulationRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_kind", nullable = false, length = 32)
    private EvaluationRunKind runKind;

    @Column(name = "loop_index", nullable = false)
    private Integer loopIndex;

    @Column(name = "snapshot_revision", nullable = false)
    private Long snapshotRevision;

    @Column(name = "sim_time", nullable = false)
    private LocalDateTime simTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_status", nullable = false, length = 16)
    private EvaluationSnapshotStatus snapshotStatus;

    @Lob
    @Column(name = "snapshot_payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotPayloadJson;

    protected EvaluationSnapshotHistoryRecord() {
        // Phase 9C：仅供 JPA 反射构造；历史记录必须通过 of(...) 创建。
    }

    public static EvaluationSnapshotHistoryRecord of(EvaluationSnapshot snapshot, String payloadJson) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("snapshot payload is required");
        }
        EvaluationSnapshotHistoryRecord record = new EvaluationSnapshotHistoryRecord();
        record.contractVersion = snapshot.contractVersion();
        record.simulationRunId = snapshot.simulationRunId();
        record.runKind = snapshot.runKind();
        record.loopIndex = snapshot.loopIndex();
        record.snapshotRevision = snapshot.snapshotRevision();
        record.simTime = snapshot.simTime();
        record.snapshotStatus = snapshot.snapshotStatus();
        record.snapshotPayloadJson = payloadJson;
        return record;
    }

    public Long getId() { return id; }
    public String getContractVersion() { return contractVersion; }
    public String getSimulationRunId() { return simulationRunId; }
    public EvaluationRunKind getRunKind() { return runKind; }
    public Integer getLoopIndex() { return loopIndex; }
    public Long getSnapshotRevision() { return snapshotRevision; }
    public LocalDateTime getSimTime() { return simTime; }
    public EvaluationSnapshotStatus getSnapshotStatus() { return snapshotStatus; }
    public String getSnapshotPayloadJson() { return snapshotPayloadJson; }
}
