package org.example.roadsimulation.evaluation;

/**
 * Phase 6A：评价指标所属的业务维度。
 *
 * <p>分类只用于组织契约和前端分组，不参与运输状态推进。</p>
 */
public enum EvaluationMetricCategory {
    GLOBAL_OBJECTIVE,
    SERVICE_CONSTRAINT,
    VEHICLE,
    CARGO,
    TASK,
    ENVIRONMENT
}
