package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.VehicleMatchResult;
import org.example.roadsimulation.dto.VehicleMatchingCriteria;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.VehicleMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class VehicleMatchingServiceImpl implements VehicleMatchingService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleMatchingServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final POIRepository poiRepository; // 新增

    // 匹配权重配置
    private static final double WEIGHT_CAPACITY_WEIGHT = 0.4;
    private static final double VOLUME_CAPACITY_WEIGHT = 0.3;
    private static final double SPECIAL_REQUIREMENT_WEIGHT = 0.2;
    private static final double DIMENSION_WEIGHT = 0.1;

    // 距离匹配配置
    private static final double DEFAULT_DISTANCE_WEIGHT = 0.3;
    private static final double MAX_DISTANCE_KM = 100.0; // 最大考虑距离

    public VehicleMatchingServiceImpl(VehicleRepository vehicleRepository,
                                      POIRepository poiRepository) { // 修改构造器
        this.vehicleRepository = vehicleRepository;
        this.poiRepository = poiRepository;
    }

    @Override
    public List<VehicleMatchResult> matchVehiclesForGoods(Goods goods, Integer quantity) {
        logger.info("开始为货物 {} (数量: {}) 匹配车辆", goods.getName(), quantity);

        // 计算货物总需求
        Double totalWeight = calculateTotalWeight(goods, quantity);
        Double totalVolume = calculateTotalVolume(goods, quantity);

        // 构建匹配条件
        VehicleMatchingCriteria criteria = new VehicleMatchingCriteria();
        criteria.setMinLoadCapacity(totalWeight);
        criteria.setRequiredVolume(totalVolume);
        criteria.setRequireTempControl(goods.getRequireTemp());
        criteria.setHazmatLevel(goods.getHazmatLevel());

        return matchVehiclesByCriteria(criteria);
    }

    @Override
    public List<VehicleMatchResult> matchVehiclesByCriteria(VehicleMatchingCriteria criteria) {
        List<Vehicle> availableVehicles = getAvailableVehicles();
        logger.info("开始根据条件匹配车辆，可用车辆数量: {}", availableVehicles.size());

        // 如果没有可用车辆，直接返回空列表
        if (availableVehicles.isEmpty()) {
            return Collections.emptyList();
        }

        List<VehicleMatchResult> matchResults = new ArrayList<>();

        for (Vehicle vehicle : availableVehicles) {
            VehicleMatchResult result = evaluateVehicleMatch(vehicle, criteria);
            if (result.getMatchScore() > 0) { // 只返回有匹配度的车辆
                matchResults.add(result);
            }
        }

        // 按匹配度降序排序
        matchResults.sort((r1, r2) -> Double.compare(r2.getMatchScore(), r1.getMatchScore()));

        logger.info("匹配完成，找到 {} 个符合条件的车辆", matchResults.size());
        return matchResults;
    }

    @Override
    public List<VehicleMatchResult> matchVehiclesByProximity(VehicleMatchingCriteria criteria) {
        logger.info("开始就近匹配，出发地POI ID: {}", criteria.getOriginPoiId());

        // 获取出发地POI信息
        POI originPOI = poiRepository.findById(criteria.getOriginPoiId()).orElse(null);
        if (originPOI == null) {
            logger.warn("未找到指定的出发地POI: {}", criteria.getOriginPoiId());
            return Collections.emptyList();
        }

        // 先进行基本匹配
        List<VehicleMatchResult> baseMatches = matchVehiclesByCriteria(criteria);

        // 计算距离并过滤
        List<VehicleMatchResult> proximityMatches = baseMatches.stream()
                .map(result -> {
                    Vehicle vehicle = result.getVehicle();
                    double distance = calculateDistance(vehicle, originPOI);

                    // 设置距离信息
                    result.setDistanceKm(distance);
                    result.setDistanceScore(calculateDistanceScore(distance, criteria.getMaxDistanceKm()));
                    result.setOriginPoiName(originPOI.getName());
                    result.setEstimatedTimeHours(calculateEstimatedTime(distance));

                    return result;
                })
                .filter(result -> {
                    // 根据最大距离过滤
                    if (criteria.getMaxDistanceKm() != null && result.getDistanceKm() > criteria.getMaxDistanceKm()) {
                        return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparingDouble(VehicleMatchResult::getDistanceKm)) // 按距离升序排序
                .collect(Collectors.toList());

        logger.info("就近匹配完成，找到 {} 个符合条件的车辆", proximityMatches.size());
        return proximityMatches;
    }

    @Override
    public List<VehicleMatchResult> smartMatchWithDistance(VehicleMatchingCriteria criteria, Double distanceWeight) {
        logger.info("开始智能匹配，距离权重: {}", distanceWeight);

        // 使用默认权重
        if (distanceWeight == null) {
            distanceWeight = DEFAULT_DISTANCE_WEIGHT;
        }

        // 获取出发地POI
        POI originPOI = criteria.getOriginPoiId() != null ?
                poiRepository.findById(criteria.getOriginPoiId()).orElse(null) : null;

        // 先进行基本匹配
        List<VehicleMatchResult> baseMatches = matchVehiclesByCriteria(criteria);

        // 计算综合评分
        Double finalDistanceWeight = distanceWeight;
        List<VehicleMatchResult> smartMatches = baseMatches.stream()
                .map(result -> {
                    Vehicle vehicle = result.getVehicle();

                    // 计算距离评分
                    double distanceScore = 100.0;
                    double distance = 0.0;

                    if (originPOI != null) {
                        distance = calculateDistance(vehicle, originPOI);
                        distanceScore = calculateDistanceScore(distance, criteria.getMaxDistanceKm());

                        // 设置距离信息
                        result.setDistanceKm(distance);
                        result.setDistanceScore(distanceScore);
                        result.setOriginPoiName(originPOI.getName());
                        result.setEstimatedTimeHours(calculateEstimatedTime(distance));
                    }

                    // 计算综合评分
                    double matchScore = result.getMatchScore(); // 原始匹配分
                    double combinedScore = (matchScore * (1 - finalDistanceWeight)) + (distanceScore * finalDistanceWeight);

                    // 更新匹配分
                    result.setMatchScore(combinedScore);

                    // 根据距离调整匹配描述
                    if (originPOI != null) {
                        String desc = result.getMatchDescription();
                        result.setMatchDescription(desc + String.format("; 距离出发地%.2fkm", distance));
                    }

                    return result;
                })
                .filter(result -> {
                    // 根据最大距离过滤
                    if (criteria.getMaxDistanceKm() != null && result.getDistanceKm() > criteria.getMaxDistanceKm()) {
                        return false;
                    }
                    return true;
                })
                .sorted((r1, r2) -> Double.compare(r2.getMatchScore(), r1.getMatchScore())) // 按综合评分降序
                .collect(Collectors.toList());

        logger.info("智能匹配完成，找到 {} 个车辆", smartMatches.size());
        return smartMatches;
    }

    @Override
    public List<Vehicle> getRecommendedVehicles(Long originPoiId, VehicleMatchingCriteria criteria) {
        POI originPOI = poiRepository.findById(originPoiId).orElse(null);
        if (originPOI == null) {
            return Collections.emptyList();
        }

        // 查询符合条件的车辆
        List<Vehicle> candidates = vehicleRepository.findAvailableVehiclesByCriteria(
                criteria.getBrand(),
                criteria.getVehicleType(),
                criteria.getMinLoadCapacity()
        );

        // 按距离排序
        return candidates.stream()
                .sorted((v1, v2) -> {
                    double d1 = calculateDistance(v1, originPOI);
                    double d2 = calculateDistance(v2, originPOI);
                    return Double.compare(d1, d2);
                })
                .limit(10) // 限制返回数量
                .collect(Collectors.toList());
    }

    @Override
    public List<Vehicle> quickMatchByLoadCapacity(Double requiredLoad) {
        if (requiredLoad == null || requiredLoad <= 0) {
            return Collections.emptyList();
        }

        logger.info("快速匹配：查找载重能力 >= {} 吨的车辆", requiredLoad);
        return vehicleRepository.findAvailableByMinLoadCapacity(requiredLoad);
    }

    @Override
    public List<Vehicle> getAvailableVehicles() {
        return vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE);
    }

    @Override
    public Double calculateTotalVolume(Goods goods, Integer quantity) {
        if (goods.getVolumePerUnit() == null || quantity == null || quantity <= 0) {
            return 0.0;
        }
        return goods.getVolumePerUnit() * quantity;
    }

    @Override
    public Double calculateTotalWeight(Goods goods, Integer quantity) {
        if (goods.getWeightPerUnit() == null || quantity == null || quantity <= 0) {
            return 0.0;
        }
        return goods.getWeightPerUnit() * quantity;
    }

    @Override
    public List<String> getAllBrands() {
        return vehicleRepository.findAllBrands();
    }

    @Override
    public List<String> getAllVehicleTypes() {
        return vehicleRepository.findAllVehicleTypes();
    }

    /**
     * 评估单个车辆与条件的匹配度
     */
    private VehicleMatchResult evaluateVehicleMatch(Vehicle vehicle, VehicleMatchingCriteria criteria) {
        double totalScore = 0.0;
        boolean isFullyMatched = true;
        List<String> matchDescriptions = new ArrayList<>();

        // 1. 载重能力匹配
        double weightScore = evaluateWeightCapacity(vehicle, criteria, matchDescriptions);
        totalScore += weightScore * WEIGHT_CAPACITY_WEIGHT;

        // 2. 容积匹配
        double volumeScore = evaluateVolumeCapacity(vehicle, criteria, matchDescriptions);
        totalScore += volumeScore * VOLUME_CAPACITY_WEIGHT;

        // 3. 特殊要求匹配（温控、危险品等）
        double specialScore = evaluateSpecialRequirements(vehicle, criteria, matchDescriptions);
        totalScore += specialScore * SPECIAL_REQUIREMENT_WEIGHT;

        // 4. 尺寸匹配
        double dimensionScore = evaluateDimensionCompatibility(vehicle, criteria, matchDescriptions);
        totalScore += dimensionScore * DIMENSION_WEIGHT;

        // 计算各项利用率
        Double weightUtilization = calculateWeightUtilization(vehicle, criteria);
        Double volumeUtilization = calculateVolumeUtilization(vehicle, criteria);
        Double capacityUtilization = calculateOverallUtilization(weightUtilization, volumeUtilization);

        VehicleMatchResult result = new VehicleMatchResult();
        result.setVehicle(vehicle);
        result.setMatchScore(totalScore * 100); // 转换为百分比
        result.setFullyMatched(isFullyMatched && totalScore > 0.8); // 匹配度80%以上认为完全匹配
        result.setMatchDescription(String.join("; ", matchDescriptions));
        result.setCapacityUtilization(capacityUtilization);
        result.setWeightUtilization(weightUtilization);
        result.setVolumeUtilization(volumeUtilization);

        return result;
    }

    /**
     * 评估载重能力匹配
     */
    private double evaluateWeightCapacity(Vehicle vehicle, VehicleMatchingCriteria criteria, List<String> descriptions) {
        if (criteria.getMinLoadCapacity() == null) {
            return 1.0; // 无重量要求，得满分
        }

        if (vehicle.getMaxLoadCapacity() == null || vehicle.getMaxLoadCapacity() <= 0) {
            descriptions.add("车辆无载重信息");
            return 0.0;
        }

        double requiredWeight = criteria.getMinLoadCapacity();
        double vehicleCapacity = vehicle.getMaxLoadCapacity();

        if (vehicleCapacity >= requiredWeight) {
            double utilization = requiredWeight / vehicleCapacity;
            double score = calculateUtilizationScore(utilization);
            descriptions.add(String.format("载重匹配(需要%.2f吨, 车辆%.2f吨)", requiredWeight, vehicleCapacity));
            return score;
        } else {
            descriptions.add(String.format("载重不足(需要%.2f吨, 车辆%.2f吨)", requiredWeight, vehicleCapacity));
            return 0.0;
        }
    }

    /**
     * 评估容积匹配
     */
    private double evaluateVolumeCapacity(Vehicle vehicle, VehicleMatchingCriteria criteria, List<String> descriptions) {
        if (criteria.getRequiredVolume() == null) {
            return 1.0; // 无容积要求，得满分
        }

        double requiredVolume = criteria.getRequiredVolume();
        Double vehicleVolume = calculateVehicleVolume(vehicle);

        if (vehicleVolume == null) {
            descriptions.add("无法评估车辆容积");
            return 0.5; // 无法评估时给中等分数
        }

        if (vehicleVolume >= requiredVolume) {
            double utilization = requiredVolume / vehicleVolume;
            double score = calculateUtilizationScore(utilization);
            descriptions.add(String.format("容积匹配(需要%.2fm³, 车辆%.2fm³)", requiredVolume, vehicleVolume));
            return score;
        } else {
            descriptions.add(String.format("容积不足(需要%.2fm³, 车辆%.2fm³)", requiredVolume, vehicleVolume));
            return 0.0;
        }
    }

    /**
     * 评估特殊要求匹配
     */
    private double evaluateSpecialRequirements(Vehicle vehicle, VehicleMatchingCriteria criteria, List<String> descriptions) {
        double score = 1.0;

        // 温控要求匹配
        if (Boolean.TRUE.equals(criteria.getRequireTempControl())) {
            // 这里需要车辆实体有温控字段，目前先标记为不匹配
            descriptions.add("需要温控但车辆不支持");
            score *= 0.0;
        }

        // 危险品要求匹配
        if (criteria.getHazmatLevel() != null && !criteria.getHazmatLevel().isEmpty()) {
            // 这里需要车辆实体有危险品运输资质字段
            descriptions.add("需要危险品运输资质但车辆未配置");
            score *= 0.0;
        }

        // 车型要求匹配
        if (criteria.getVehicleType() != null && !criteria.getVehicleType().isEmpty()) {
            if (criteria.getVehicleType().equals(vehicle.getVehicleType())) {
                descriptions.add("车型匹配: " + vehicle.getVehicleType());
                score *= 1.1; // 车型匹配，稍微加分
            } else {
                descriptions.add("车型不匹配(需要: " + criteria.getVehicleType() + ", 当前: " + vehicle.getVehicleType() + ")");
                score *= 0.7; // 车型不匹配但不致命
            }
        }

        // 品牌要求匹配
        if (criteria.getBrand() != null && !criteria.getBrand().isEmpty()) {
            if (criteria.getBrand().equals(vehicle.getBrand())) {
                descriptions.add("品牌匹配: " + vehicle.getBrand());
            } else {
                descriptions.add("品牌不匹配(需要: " + criteria.getBrand() + ", 当前: " + vehicle.getBrand() + ")");
                score *= 0.9; // 品牌不匹配影响较小
            }
        }

        return Math.min(score, 1.0); // 确保不超过1.0
    }

    /**
     * 评估尺寸兼容性
     */
    private double evaluateDimensionCompatibility(Vehicle vehicle, VehicleMatchingCriteria criteria, List<String> descriptions) {
        // 如果有具体的尺寸要求，检查车辆尺寸是否满足
        boolean dimensionOk = true;
        List<String> dimensionIssues = new ArrayList<>();

        if (criteria.getMinLength() != null && vehicle.getLength() != null) {
            if (vehicle.getLength() < criteria.getMinLength()) {
                dimensionOk = false;
                dimensionIssues.add(String.format("长度不足(需要%.2fm, 车辆%.2fm)", criteria.getMinLength(), vehicle.getLength()));
            }
        }

        if (criteria.getMinWidth() != null && vehicle.getWidth() != null) {
            if (vehicle.getWidth() < criteria.getMinWidth()) {
                dimensionOk = false;
                dimensionIssues.add(String.format("宽度不足(需要%.2fm, 车辆%.2fm)", criteria.getMinWidth(), vehicle.getWidth()));
            }
        }

        if (criteria.getMinHeight() != null && vehicle.getHeight() != null) {
            if (vehicle.getHeight() < criteria.getMinHeight()) {
                dimensionOk = false;
                dimensionIssues.add(String.format("高度不足(需要%.2fm, 车辆%.2fm)", criteria.getMinHeight(), vehicle.getHeight()));
            }
        }

        if (!dimensionOk) {
            descriptions.addAll(dimensionIssues);
            return 0.0;
        } else if (!dimensionIssues.isEmpty()) {
            descriptions.add("尺寸要求完全满足");
        }

        return 1.0;
    }

    /**
     * 计算车辆容积（优先使用明确容积，否则估算）
     */
    private Double calculateVehicleVolume(Vehicle vehicle) {
        // 优先使用明确的容积数据
        if (vehicle.getCargoVolume() != null && vehicle.getCargoVolume() > 0) {
            return vehicle.getCargoVolume();
        }

        // 使用三维尺寸估算容积
        if (vehicle.getLength() != null && vehicle.getWidth() != null && vehicle.getHeight() != null) {
            return vehicle.getLength() * vehicle.getWidth() * vehicle.getHeight();
        }

        return null;
    }

    /**
     * 计算重量利用率
     */
    private Double calculateWeightUtilization(Vehicle vehicle, VehicleMatchingCriteria criteria) {
        if (criteria.getMinLoadCapacity() == null || vehicle.getMaxLoadCapacity() == null || vehicle.getMaxLoadCapacity() <= 0) {
            return null;
        }
        return criteria.getMinLoadCapacity() / vehicle.getMaxLoadCapacity();
    }

    /**
     * 计算容积利用率
     */
    private Double calculateVolumeUtilization(Vehicle vehicle, VehicleMatchingCriteria criteria) {
        if (criteria.getRequiredVolume() == null) {
            return null;
        }

        Double vehicleVolume = calculateVehicleVolume(vehicle);
        if (vehicleVolume == null || vehicleVolume <= 0) {
            return null;
        }

        return criteria.getRequiredVolume() / vehicleVolume;
    }

    /**
     * 计算整体利用率（取各项利用率的最高值）
     */
    private Double calculateOverallUtilization(Double weightUtilization, Double volumeUtilization) {
        if (weightUtilization != null && volumeUtilization != null) {
            return Math.max(weightUtilization, volumeUtilization);
        } else if (weightUtilization != null) {
            return weightUtilization;
        } else if (volumeUtilization != null) {
            return volumeUtilization;
        }
        return null;
    }

    /**
     * 根据利用率计算得分（利用率适中得分高，过高或过低得分低）
     */
    private double calculateUtilizationScore(double utilization) {
        // 理想利用率范围：30%-80%
        if (utilization >= 0.3 && utilization <= 0.8) {
            return 1.0;
        } else if (utilization < 0.3) {
            // 利用率太低，资源浪费
            return 0.7 - (0.3 - utilization) * 1.0;
        } else {
            // 利用率太高，可能超载风险
            return 1.0 - (utilization - 0.8) * 2.0;
        }
    }

    /**
     * 计算车辆与POI之间的距离（公里）
     */
    private double calculateDistance(Vehicle vehicle, POI poi) {
        // 如果车辆有当前位置坐标，优先使用
        if (vehicle.getCurrentLongitude() != null && vehicle.getCurrentLatitude() != null) {
            return calculateHaversineDistance(
                    vehicle.getCurrentLatitude().doubleValue(),
                    vehicle.getCurrentLongitude().doubleValue(),
                    poi.getLatitude().doubleValue(),
                    poi.getLongitude().doubleValue()
            );
        }

        // 如果车辆关联到POI，计算POI之间的距离
        if (vehicle.getCurrentPOI() != null) {
            POI vehiclePOI = vehicle.getCurrentPOI();
            return calculateHaversineDistance(
                    vehiclePOI.getLatitude().doubleValue(),
                    vehiclePOI.getLongitude().doubleValue(),
                    poi.getLatitude().doubleValue(),
                    poi.getLongitude().doubleValue()
            );
        }

        // 如果都没有位置信息，返回一个默认值
        logger.warn("车辆 {} 无位置信息，使用默认距离", vehicle.getLicensePlate());
        return MAX_DISTANCE_KM / 2; // 返回中间值
    }

    /**
     * 使用Haversine公式计算两点间距离（单位：公里）
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 地球半径（公里）

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * 计算距离评分
     */
    private double calculateDistanceScore(double distanceKm, Double maxDistanceKm) {
        if (maxDistanceKm == null) {
            maxDistanceKm = MAX_DISTANCE_KM;
        }

        if (distanceKm <= 0) {
            return 100.0; // 在同一位置
        }

        if (distanceKm >= maxDistanceKm) {
            return 0.0; // 超出最大距离
        }

        // 线性评分：距离越近分数越高
        return 100.0 * (1 - (distanceKm / maxDistanceKm));
    }

    /**
     * 计算预计到达时间（小时）
     */
    private double calculateEstimatedTime(double distanceKm) {
        // 假设平均车速为60km/h
        final double AVERAGE_SPEED_KMH = 60.0;
        return distanceKm / AVERAGE_SPEED_KMH;
    }

    /**
     * 获取区域内的可用车辆（提供给外部调用）
     */
    public List<Vehicle> getAvailableVehiclesInArea(Long centerPoiId, Double radiusKm) {
        POI centerPOI = poiRepository.findById(centerPoiId).orElse(null);
        if (centerPOI == null) {
            logger.warn("未找到指定的中心点POI: {}", centerPoiId);
            return Collections.emptyList();
        }

        logger.info("查询区域内车辆，中心点: {}, 半径: {}km", centerPOI.getName(), radiusKm);

        List<Vehicle> allAvailable = vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE);

        return allAvailable.stream()
                .filter(vehicle -> {
                    double distance = calculateDistance(vehicle, centerPOI);
                    return distance <= radiusKm;
                })
                .sorted((v1, v2) -> {
                    double d1 = calculateDistance(v1, centerPOI);
                    double d2 = calculateDistance(v2, centerPOI);
                    return Double.compare(d1, d2);
                })
                .collect(Collectors.toList());
    }
}