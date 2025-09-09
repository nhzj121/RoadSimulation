package org.example.roadsimulation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "Action")
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_name", nullable = false, unique = true)
    private String actionName; // 行为名称，如："长途运输", "市内配送", "紧急卸货"

    @Column(name = "action_type", nullable = false)
    private String actionType; // 行为类型：运输中、装货、卸货、保养、加油、休息、事故

    @Column(nullable = false)
    private Integer duration; // 持续时长（单位为仿真时间单位，如：分钟）

    @Column(name = "accident_rate")
    private Double accidentRate; // 异常率或事故发生率（0.0 - 1.0 之间的小数，例如0.01表示1%的概率）

    // 必须的无参构造函数
    public Action() {
    }

    // 带所有参数的构造函数，方便测试和初始化
    public Action(String actionName, String actionType, Integer duration, Double accidentRate) {
        this.actionName = actionName;
        this.actionType = actionType;
        this.duration = duration;
        this.accidentRate = accidentRate;
    }

    // Getter 和 Setter 方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Double getAccidentRate() {
        return accidentRate;
    }

    public void setAccidentRate(Double accidentRate) {
        this.accidentRate = accidentRate;
    }

    // 重写toString方法，便于日志打印
    @Override
    public String toString() {
        return "Action{" +
                "id=" + id +
                ", actionName='" + actionName + '\'' +
                ", actionType='" + actionType + '\'' +
                ", duration=" + duration +
                ", accidentRate=" + accidentRate +
                '}';
    }
}
