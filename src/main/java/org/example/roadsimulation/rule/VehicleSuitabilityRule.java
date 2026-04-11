package org.example.roadsimulation.rule;

/**
 * 车辆适配规则枚举
 * 定义货物与车辆匹配的各项规则
 */
public enum VehicleSuitabilityRule {

    /**
     * 载重能力匹配 - 硬性条件
     * 规则：车辆最大载重 >= 货物总重量
     */
    LOAD_CAPACITY("载重能力", RuleType.HARD, 0.25),

    /**
     * 容积能力匹配 - 硬性条件
     * 规则：车辆货厢容积 >= 货物总体积
     */
    VOLUME_CAPACITY("容积能力", RuleType.HARD, 0.20),

    /**
     * 温控需求匹配 - 硬性条件
     * 规则：需要温控的货物必须使用有温控设备的车辆
     */
    TEMP_CONTROL("温控需求", RuleType.HARD, 0.20),

    /**
     * 危险品资质匹配 - 硬性条件
     * 规则：危险品货物必须使用有相应资质级别的车辆
     */
    HAZMAT_QUALIFICATION("危险品资质", RuleType.HARD, 0.15),

    /**
     * 车辆类型匹配 - 软性条件
     * 规则：专用货物推荐使用专用车型
     */
    VEHICLE_TYPE("车辆类型", RuleType.SOFT, 0.10),

    /**
     * 距离优先 - 优化条件
     * 规则：车辆距离装货点越近越优先
     */
    DISTANCE("距离优先", RuleType.OPTIONAL, 0.05),

    /**
     * 载重利用率优化 - 优化条件
     * 规则：车辆载重利用率适中（30%-80%）优先
     */
    LOAD_UTILIZATION("载重利用率", RuleType.OPTIONAL, 0.03),

    /**
     * 容积利用率优化 - 优化条件
     * 规则：车辆容积利用率适中（30%-80%）优先
     */
    VOLUME_UTILIZATION("容积利用率", RuleType.OPTIONAL, 0.02);

    /**
     * 规则类型
     */
    public enum RuleType {
        /**
         * 硬性条件 - 必须满足，否则直接排除
         */
        HARD,
        /**
         * 软性条件 - 不满足会降低评分，但不排除
         */
        SOFT,
        /**
         * 优化条件 - 用于排序优化
         */
        OPTIONAL
    }

    /**
     * 规则中文名称
     */
    private final String displayName;

    /**
     * 规则类型
     */
    private final RuleType ruleType;

    /**
     * 规则权重（用于评分计算）
     */
    private final double weight;

    VehicleSuitabilityRule(String displayName, RuleType ruleType, double weight) {
        this.displayName = displayName;
        this.ruleType = ruleType;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public double getWeight() {
        return weight;
    }

    /**
     * 判断是否为硬性条件
     */
    public boolean isHardRule() {
        return this.ruleType == RuleType.HARD;
    }

    /**
     * 判断是否为软性条件
     */
    public boolean isSoftRule() {
        return this.ruleType == RuleType.SOFT;
    }

    /**
     * 判断是否为优化条件
     */
    public boolean isOptionalRule() {
        return this.ruleType == RuleType.OPTIONAL;
    }
}
