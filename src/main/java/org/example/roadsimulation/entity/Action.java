package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "Action")
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "动作名称不能为空")
    @Column(name = "action_name", nullable = false, length = 100)
    private String actionName; // 行为名称，如："长途运输", "市内配送", "紧急卸货"

    // 动作类型枚举
    public enum ActionType {
        MOVE_TO,    // 移动到指定位置
        LOAD,       // 装货
        UNLOAD,     // 卸货
        WAIT,       // 等待
        REPORT,     // 报告状态
        REFUEL,     // 加油
        REST,       // 休息
        MAINTENANCE // 维护
    }

    // 创建的来源以及创建的时间
    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    @NotNull(message = "动作类型不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;

    @Positive(message = "持续时间必须为正数")
    @Column(name = "duration_minutes")
    private Integer durationMinutes; // 动作持续时间（分钟）

    @Column(name = "target_poi_id")
    private Long targetPoiId; // 目标POI ID（对于MOVE_TO动作）

    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 动作描述

    @Column(name = "is_locked")
    private Boolean isLocked = false;

    // 进行修改的对象和时间
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    // 必须的无参构造函数
    public Action() {
    }

    // 带所有参数的构造函数，方便测试和初始化
    public Action(String actionName, ActionType actionType, Integer duration) {
        this.actionName = actionName;
        this.actionType = actionType;
        this.durationMinutes = duration;
    }

    // Getter 和 Setter 方法
    public Long getId() {return id;}
    public void setId(Long id) {this.id = id;}
    public String getActionName() {return actionName;}
    public void setName(String name) {
        // 一旦锁定，不允许修改名称
        if (Boolean.TRUE.equals(this.isLocked)) {
            throw new IllegalStateException("Action已被锁定，无法修改名称");
        }
        this.actionName = name;
    }
    public ActionType getActionType() {return actionType;}
    public void setType(ActionType type) {
        // 一旦锁定，不允许修改类型
        if (Boolean.TRUE.equals(this.isLocked)) {
            throw new IllegalStateException("Action已被锁定，无法修改类型");
        }
        this.actionType = type;
    }
    public Integer getDurationMinutes() {return durationMinutes;}
    public void setDurationMinutes(Integer durationMinutes) {
        // 一旦锁定，不允许修改持续时间
        if (Boolean.TRUE.equals(this.isLocked)) {
            throw new IllegalStateException("Action已被锁定，无法修改持续时间");
        }
        this.durationMinutes = durationMinutes;
    }
    public Long getTargetPoiId() {return targetPoiId;}
    public void setTargetPoiId(Long targetPoiId) {this.targetPoiId = targetPoiId;}
    public String getDescription() {return description;}
    public void setDescription(String description) {this.description = description;}
    public Boolean getIsLocked() {return isLocked;}
    public void setIsLocked(Boolean isLocked) {this.isLocked = isLocked;}

    // 四元组字段的getter和setter
    public String getCreatedBy() {return createdBy;}
    public void setCreatedBy(String createdBy) {this.createdBy = createdBy;}
    public LocalDateTime getCreatedTime() {return createdTime;}
    public void setCreatedTime(LocalDateTime createdTime) {this.createdTime = createdTime;}
    public String getUpdatedBy() {return updatedBy;}
    public void setUpdatedBy(String updatedBy) {this.updatedBy = updatedBy;}
    public LocalDateTime getUpdatedTime() {return updatedTime;}
    public void setUpdatedTime(LocalDateTime updatedTime) {this.updatedTime = updatedTime;}


    // 业务方法

    /**
     * 锁定Action，防止后续修改
     */
    public void lock() {
        this.isLocked = true;
    }

    /**
     * 解锁Action，允许修改（谨慎使用）
     */
    public void unlock() {
        this.isLocked = false;
    }

    /**
     * 检查Action是否已被锁定
     */
    public boolean isLocked() {
        return Boolean.TRUE.equals(this.isLocked);
    }

    /**
     * 获取持续时间的Duration对象
     */
    public Duration getDuration() {
        return Duration.ofMinutes(durationMinutes);
    }

    /**
     * 检查是否是移动动作
     */
    public boolean isMoveAction() {
        return ActionType.MOVE_TO.equals(this.actionType);
    }

    /**
     * 检查是否是装卸货动作
     */
    public boolean isCargoAction() {
        return ActionType.LOAD.equals(this.actionType) || ActionType.UNLOAD.equals(this.actionType);
    }

    /**
     * 生成描述信息（如果未设置自定义描述）
     */
    public String generateDescription() {
        if (this.description != null && !this.description.trim().isEmpty()) {
            return this.description;
        }

        switch (this.actionType) {
            case MOVE_TO:
                return "移动到目标位置";
            case LOAD:
                return "装货 (" + durationMinutes + "分钟)";
            case UNLOAD:
                return "卸货 (" + durationMinutes + "分钟)";
            case WAIT:
                return "等待 (" + durationMinutes + "分钟)";
            case REPORT:
                return "发送状态报告";
            case REFUEL:
                return "加油 (" + durationMinutes + "分钟)";
            case REST:
                return "休息 (" + durationMinutes + "分钟)";
            case MAINTENANCE:
                return "维护 (" + durationMinutes + "分钟)";
            default:
                return this.actionName;
        }
    }

    // 重写toString方法，便于日志打印
    @Override
    public String toString() {
        return "Action{" +
                "id=" + id +
                ", actionName='" + actionName + '\'' +
                ", actionType='" + actionType + '\'' +
                ", duration=" + durationMinutes +
                ", isLocked=" + isLocked +
                '}';
    }
}
