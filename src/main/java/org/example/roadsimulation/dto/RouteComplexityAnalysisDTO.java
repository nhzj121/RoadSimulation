package org.example.roadsimulation.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)

/**
 * 路线复杂度分析结果 DTO
 *
 * 说明：
 * 这个对象用于承载“某一条推荐路线”的复杂度分析结果。
 *
 * 注意：
 * 当前复杂度算法不直接把“总时间、总距离”作为复杂度主维度，
 * 但可以把某些与距离相关的比值作为辅助归一化参考。
 */
@Data
public class RouteComplexityAnalysisDTO {

    /**
     * 路段数量
     * 也就是 steps 的数量
     */
    private Integer stepCount;

    /**
     * 平均每个 step 的距离（米）
     * 用于辅助判断路线是否碎片化
     */
    private Double averageStepDistance;

    /**
     * 短步骤数量
     * 当前定义：距离小于 300 米的 step
     */
    private Integer shortStepCount;

    /**
     * 短步骤占比
     * shortStepCount / stepCount
     */
    private Double shortStepRatio;


    /**
     * 左转次数
     */
    private Integer leftTurnCount;

    /**
     * 右转次数
     */
    private Integer rightTurnCount;

    /**
     * 调头次数
     */
    private Integer uTurnCount;

    /**
     * 匝道相关动作次数
     */
    private Integer rampCount;

    /**
     * 环岛相关动作次数
     */
    private Integer roundaboutCount;

    /**
     * 加权转向动作分值原始值
     */
    private Double maneuverRawScore;

    /**
     * 方向变化次数
     * 相邻 step 的 orientation 不一致，就算一次变化
     */
    private Integer directionChangeCount;

    /**
     * 大幅方向变化次数
     * 例如：北->南、东->西、东北->西南
     */
    private Integer majorDirectionChangeCount;

    /**
     * 方向变化复杂度得分（0~100）
     */
    private Double directionChangeScore;




    /**
     * polyline 点数量
     */
    private Integer polylinePointCount;

    /**
     * 折线点密度（每公里点数）
     */
    private Double polylineDensity;

    /**
     * 路段碎片复杂度得分（0~100）
     */
    private Double fragmentScore;

    /**
     * 转向操作复杂度得分（0~100）
     */
    private Double maneuverScore;


    /**
     * 路线形态复杂度得分（0~100）
     */
    private Double shapeScore;

    /**
     * 综合复杂度总分（0~100）
     */
    private Double complexityScore;

    /**
     * 复杂度等级
     * 低复杂度 / 中复杂度 / 高复杂度 / 极高复杂度
     */
    private String complexityLevel;

    /**
     * 复杂度说明
     * 用于前端展示或调试解释
     */
    private String summary;

    /**
     * 路段碎片复杂度对总分的贡献值
     */
    private Double fragmentContribution;

    /**
     * 转向操作复杂度对总分的贡献值
     */
    private Double maneuverContribution;

    /**
     * 方向变化复杂度对总分的贡献值
     */
    private Double directionChangeContribution;



    /**
     * 路线形态复杂度对总分的贡献值
     */
    private Double shapeContribution;

    /**
     * 主导复杂度因素
     * 例如：方向变化复杂度 / 路段碎片复杂度 / 转向操作复杂度
     */
    private String dominantFactor;

    /**
     * 更详细的解释说明
     */
    private String explanation;

}
