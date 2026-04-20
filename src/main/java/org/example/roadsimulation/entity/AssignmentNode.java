package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_nodes")
@Getter
@Setter
public class AssignmentNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 所属的运输任务
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    // 节点执行的顺序索引 (例如: 0为起点, 1为途经点1, 2为途经点2, 3为终点)
    @Column(nullable = false)
    private Integer sequenceIndex;

    // 目标POI点
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poi_id", nullable = false)
    private POI poi;

    // 节点动作类型
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeActionType actionType;

    // 重量的变化量 (吨)。装货为正数，卸货为负数，仅途径不装卸为0
    @Column(nullable = false)
    private Double weightDelta = 0.0;

    // 体积的变化量 (方)。装货为正数，卸货为负数，仅途径不装卸为0
    @Column(nullable = false)
    private Double volumeDelta = 0.0;

    // 是否已到达并完成该节点动作
    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;

    // 实际到达/完成时间
    private LocalDateTime actualArrivalTime;

    // 定义节点动作类型的枚举
    public enum NodeActionType {
        LOAD,       // 装货
        UNLOAD,     // 卸货
        PASS_BY     // 仅途径 (未来可能需要在此加油或休息)
    }

    public AssignmentNode() {
    }

    public AssignmentNode(Assignment assignment, Integer sequenceIndex, POI poi, NodeActionType actionType, Double weightDelta, Double volumeDelta) {
        this.assignment = assignment;
        this.sequenceIndex = sequenceIndex;
        this.poi = poi;
        this.actionType = actionType;
        this.weightDelta = weightDelta;
        this.volumeDelta = volumeDelta;
    }
}