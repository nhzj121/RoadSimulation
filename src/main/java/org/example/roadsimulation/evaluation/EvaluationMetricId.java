package org.example.roadsimulation.evaluation;

/**
 * Phase 6A：评价文档中全部指标的稳定标识。
 *
 * <p>Java 常量名用于后端编译期引用，{@link #metricId} 是后续 HTTP 和前端使用的稳定键。</p>
 */
public enum EvaluationMetricId {
    // Phase 6A：四个全局优化目标和一个服务约束。
    GLOBAL_EMPTY_MILEAGE_RATIO("emptyMileageRatio", EvaluationMetricCategory.GLOBAL_OBJECTIVE),
    GLOBAL_CAPACITY_WASTE_RATIO("capacityWasteRatio", EvaluationMetricCategory.GLOBAL_OBJECTIVE),
    GLOBAL_UNMET_DEMAND_RATIO("unmetDemandRatio", EvaluationMetricCategory.GLOBAL_OBJECTIVE),
    GLOBAL_CARBON_INTENSITY("carbonIntensity", EvaluationMetricCategory.GLOBAL_OBJECTIVE),
    GLOBAL_WAITING_SERVICE_COMPLIANT("waitingServiceCompliant", EvaluationMetricCategory.SERVICE_CONSTRAINT),

    // Phase 6A：车辆对象指标。
    VEHICLE_TOTAL_COUNT("vehicleTotalCount", EvaluationMetricCategory.VEHICLE),
    VEHICLE_AVAILABLE_COUNT("vehicleAvailableCount", EvaluationMetricCategory.VEHICLE),
    VEHICLE_IN_TRANSPORT_COUNT("vehicleInTransportCount", EvaluationMetricCategory.VEHICLE),
    VEHICLE_EMPTY_DRIVING_COUNT("vehicleEmptyDrivingCount", EvaluationMetricCategory.VEHICLE),
    VEHICLE_LOADED_DRIVING_COUNT("vehicleLoadedDrivingCount", EvaluationMetricCategory.VEHICLE),
    VEHICLE_TOTAL_EXECUTED_DISTANCE_KM("vehicleTotalExecutedDistanceKm", EvaluationMetricCategory.VEHICLE),
    VEHICLE_EMPTY_EXECUTED_DISTANCE_KM("vehicleEmptyExecutedDistanceKm", EvaluationMetricCategory.VEHICLE),
    VEHICLE_EMPTY_MILEAGE_RATIO("vehicleEmptyMileageRatio", EvaluationMetricCategory.VEHICLE),
    VEHICLE_CURRENT_LOAD_TONNES("vehicleCurrentLoadTonnes", EvaluationMetricCategory.VEHICLE),
    VEHICLE_RATED_CAPACITY_TONNES("vehicleRatedCapacityTonnes", EvaluationMetricCategory.VEHICLE),
    VEHICLE_DISTANCE_WEIGHTED_LOAD_RATIO("vehicleDistanceWeightedLoadRatio", EvaluationMetricCategory.VEHICLE),
    VEHICLE_REMAINING_CAPACITY_TONNES("vehicleRemainingCapacityTonnes", EvaluationMetricCategory.VEHICLE),
    VEHICLE_LOW_LOAD_RATIO("vehicleLowLoadRatio", EvaluationMetricCategory.VEHICLE),
    VEHICLE_FULL_LOAD_RATIO("vehicleFullLoadRatio", EvaluationMetricCategory.VEHICLE),
    VEHICLE_CUMULATIVE_WAIT_SECONDS("vehicleCumulativeWaitSeconds", EvaluationMetricCategory.VEHICLE),
    VEHICLE_P95_WAIT_SECONDS("vehicleP95WaitSeconds", EvaluationMetricCategory.VEHICLE),
    VEHICLE_TOTAL_ENERGY("vehicleTotalEnergy", EvaluationMetricCategory.VEHICLE),
    VEHICLE_TOTAL_EMISSION_KG("vehicleTotalEmissionKg", EvaluationMetricCategory.VEHICLE),
    VEHICLE_EMISSION_INTENSITY("vehicleEmissionIntensity", EvaluationMetricCategory.VEHICLE),

    // Phase 6A：货物对象指标。
    CARGO_REQUIRED_TONNES("cargoRequiredTonnes", EvaluationMetricCategory.CARGO),
    CARGO_UNASSIGNED_TONNES("cargoUnassignedTonnes", EvaluationMetricCategory.CARGO),
    CARGO_ASSIGNED_NOT_LOADED_TONNES("cargoAssignedNotLoadedTonnes", EvaluationMetricCategory.CARGO),
    CARGO_IN_TRANSIT_TONNES("cargoInTransitTonnes", EvaluationMetricCategory.CARGO),
    CARGO_DELIVERED_TONNES("cargoDeliveredTonnes", EvaluationMetricCategory.CARGO),
    CARGO_DELIVERY_ACHIEVEMENT_RATIO("cargoDeliveryAchievementRatio", EvaluationMetricCategory.CARGO),
    CARGO_UNMET_TONNES("cargoUnmetTonnes", EvaluationMetricCategory.CARGO),
    CARGO_OVERDUE_UNTRANSPORTED_TONNES("cargoOverdueUntransportedTonnes", EvaluationMetricCategory.CARGO),
    CARGO_AVERAGE_WAIT_SECONDS("cargoAverageWaitSeconds", EvaluationMetricCategory.CARGO),
    CARGO_P95_WAIT_SECONDS("cargoP95WaitSeconds", EvaluationMetricCategory.CARGO),
    CARGO_HIGH_PRIORITY_COMPLETION_RATIO("cargoHighPriorityCompletionRatio", EvaluationMetricCategory.CARGO),
    CARGO_PRIORITY_WEIGHTED_COMPLETION_RATIO("cargoPriorityWeightedCompletionRatio", EvaluationMetricCategory.CARGO),
    CARGO_EXECUTED_TONNE_KM("cargoExecutedTonneKm", EvaluationMetricCategory.CARGO),

    // Phase 6A：运输任务对象指标；未分配任务保留原文标识，但契约明确为不适用。
    TASK_TOTAL_COUNT("taskTotalCount", EvaluationMetricCategory.TASK),
    TASK_UNASSIGNED_COUNT("taskUnassignedCount", EvaluationMetricCategory.TASK),
    TASK_ASSIGNED_PENDING_COUNT("taskAssignedPendingCount", EvaluationMetricCategory.TASK),
    TASK_IN_PROGRESS_COUNT("taskInProgressCount", EvaluationMetricCategory.TASK),
    TASK_COMPLETED_COUNT("taskCompletedCount", EvaluationMetricCategory.TASK),
    TASK_COMPLETION_RATIO("taskCompletionRatio", EvaluationMetricCategory.TASK),
    TASK_AVERAGE_RESPONSE_SECONDS("taskAverageResponseSeconds", EvaluationMetricCategory.TASK),
    TASK_AVERAGE_START_WAIT_SECONDS("taskAverageStartWaitSeconds", EvaluationMetricCategory.TASK),
    TASK_OVERDUE_COUNT("taskOverdueCount", EvaluationMetricCategory.TASK),
    TASK_ON_TIME_COMPLETION_RATIO("taskOnTimeCompletionRatio", EvaluationMetricCategory.TASK),
    TASK_AVERAGE_LOAD_RATIO("taskAverageLoadRatio", EvaluationMetricCategory.TASK),
    TASK_LOW_LOAD_COUNT("taskLowLoadCount", EvaluationMetricCategory.TASK),
    TASK_EMPTY_PICKUP_DISTANCE_KM("taskEmptyPickupDistanceKm", EvaluationMetricCategory.TASK),
    TASK_AVERAGE_EMPTY_PICKUP_DISTANCE_KM("taskAverageEmptyPickupDistanceKm", EvaluationMetricCategory.TASK),
    TASK_ROUTE_EFFICIENCY_RATIO("taskRouteEfficiencyRatio", EvaluationMetricCategory.TASK),
    TASK_TONNE_KM("taskTonneKm", EvaluationMetricCategory.TASK),
    TASK_EMISSION_KG("taskEmissionKg", EvaluationMetricCategory.TASK),
    TASK_EMISSION_INTENSITY("taskEmissionIntensity", EvaluationMetricCategory.TASK),
    TASK_REASSIGNMENT_COUNT("taskReassignmentCount", EvaluationMetricCategory.TASK),

    // Phase 6A：外部环境指标，统一等待 Phase 7 的确定性场景事实。
    ENV_NETWORK_AVERAGE_SPEED_KPH("environmentNetworkAverageSpeedKph", EvaluationMetricCategory.ENVIRONMENT),
    ENV_ROAD_REALTIME_SPEED_KPH("environmentRoadRealtimeSpeedKph", EvaluationMetricCategory.ENVIRONMENT),
    ENV_CONGESTION_INDEX("environmentCongestionIndex", EvaluationMetricCategory.ENVIRONMENT),
    ENV_ROAD_PASSABILITY_RATIO("environmentRoadPassabilityRatio", EvaluationMetricCategory.ENVIRONMENT),
    ENV_CLOSED_ROAD_COUNT("environmentClosedRoadCount", EvaluationMetricCategory.ENVIRONMENT),
    ENV_ABNORMAL_EVENT_COUNT("environmentAbnormalEventCount", EvaluationMetricCategory.ENVIRONMENT),
    ENV_WEATHER_RISK_LEVEL("environmentWeatherRiskLevel", EvaluationMetricCategory.ENVIRONMENT),
    ENV_TRAVEL_TIME_FACTOR("environmentTravelTimeFactor", EvaluationMetricCategory.ENVIRONMENT),
    ENV_DISTANCE_FACTOR("environmentDistanceFactor", EvaluationMetricCategory.ENVIRONMENT),
    ENV_ENERGY_FACTOR("environmentEnergyFactor", EvaluationMetricCategory.ENVIRONMENT),
    ENV_NODE_QUEUE_LENGTH("environmentNodeQueueLength", EvaluationMetricCategory.ENVIRONMENT),
    ENV_NODE_AVERAGE_SERVICE_SECONDS("environmentNodeAverageServiceSeconds", EvaluationMetricCategory.ENVIRONMENT),
    ENV_NODE_THROUGHPUT_TONNES("environmentNodeThroughputTonnes", EvaluationMetricCategory.ENVIRONMENT);

    private final String metricId;
    private final EvaluationMetricCategory category;

    EvaluationMetricId(String metricId, EvaluationMetricCategory category) {
        this.metricId = metricId;
        this.category = category;
    }

    public String getMetricId() {
        return metricId;
    }

    public EvaluationMetricCategory getCategory() {
        return category;
    }
}
