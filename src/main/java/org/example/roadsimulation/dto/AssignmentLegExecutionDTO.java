package org.example.roadsimulation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Phase 2：AssignmentLeg 的只读执行投影。
 *
 * <p>该 DTO 同时公开计划值和实际值，避免前端或后续评价服务把
 * {@code plannedDistanceMeters} 误当成已经行驶的距离。Phase 4 继续保持只读，
 * 权威进度只能由后端主循环写入，不提供修改路段执行状态的 HTTP 入口。</p>
 */
@Data
public class AssignmentLegExecutionDTO {

    // Phase 2：数据库路段标识，只用于定位和并发诊断。
    private Long id;

    // Phase 2：任务内部从零开始的稳定路段序号，与 Assignment.currentLegIndex 对应。
    private Integer sequenceIndex;

    // Phase 2：路段起点 POI；车辆当前位置起步的首段允许为 null。
    private Long fromPoiId;

    // Phase 2：路段终点 POI；现有实体约束要求非 null。
    private Long toPoiId;

    // Phase 2：路线规划得到的总距离，规范单位为米。
    private Double plannedDistanceMeters;

    // Phase 2：路线规划得到的总有效行驶时间，规范单位为秒。
    private Long plannedDrivingSeconds;

    // Phase 2：后端实际累计执行距离，规范单位为米；Phase 2 初始恒为零。
    // Phase 3：主循环开始动态更新该值，但 DTO 仍然只是读取投影。
    private Double executedDistanceMeters;

    // Phase 2：后端实际累计有效行驶时间，规范单位为秒；Phase 2 初始恒为零。
    // Phase 3：该值是距离反算的唯一累计时间基准。
    private Long executedDrivingSeconds;

    // Phase 2：路段开始的仿真时间，不是系统墙上时间。
    private LocalDateTime startedSimTime;

    // Phase 2：路段完成的仿真时间，不是前端动画完成时间。
    private LocalDateTime completedSimTime;

    // Phase 2：独立的路段状态，取值为 PENDING/RUNNING/COMPLETED。
    private String progressStatus;

    // Phase 2：进入路段时携带的载重快照，单位为吨。
    private Double currentLoadTonnes;

    // Phase 8：按实际路段推进累计的柴油当量能耗，单位 L；只读公开用于诊断。
    private Double executedEnergyLiters;

    // Phase 8：按实际能耗累计的直接运行碳排，单位 kgCO2e；只读公开用于诊断。
    private Double executedEmissionKg;

    // Phase 8：路段首次产生能耗时冻结的版本化代理模型标识。
    private String emissionModelId;

    // Phase 8：由车辆额定载重解析得到的 L1/L2/M/H 代理档位。
    private String vehicleEmissionClassCode;

    // Phase 8：PENDING/VALID/INVALID，仅描述能耗事实完整性，不是运输状态。
    private String energyFactStatus;

    // Phase 2：JPA 乐观锁版本，用于发现主循环与 HTTP 并发覆盖。
    private Long version;

    // Phase 4：只读公开后端权威进度的去重序号，用于诊断重复 tick，不向前端开放写权限。
    private Integer lastProcessedLoopIndex;
}
