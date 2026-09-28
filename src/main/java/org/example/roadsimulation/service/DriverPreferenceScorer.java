package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 司机接单偏好打分器。
 * 三维偏好：货类（白名单命中 1.0/不中 0.0）、距离与重量为上限型数值
 * （实际值 ≤ 偏好上限 1.0，超出按比例衰减，未知 0.5），
 * 加权合成 0.4*cargo + 0.3*distance + 0.3*weight，只用于候选顺序，
 * 不得修改成本、约束、适应度或可行性判断。
 */
@Component
public class DriverPreferenceScorer {

    public static final double CARGO_WEIGHT = 0.4;
    public static final double DISTANCE_WEIGHT = 0.3;
    public static final double WEIGHT_WEIGHT = 0.3;

    /**
     * 货类归一化关键字表：命中即映射到规范货类名（顺序即优先级）。
     */
    private static final Map<String, String> CARGO_KEYWORDS = new LinkedHashMap<>();
    static {
        CARGO_KEYWORDS.put("水泥", "水泥");
        CARGO_KEYWORDS.put("CEMENT", "水泥");
        CARGO_KEYWORDS.put("家具", "家具");
        CARGO_KEYWORDS.put("FURNITURE", "家具");
        CARGO_KEYWORDS.put("轮胎", "轮胎");
        CARGO_KEYWORDS.put("TIRE", "轮胎");
        CARGO_KEYWORDS.put("橡胶", "橡胶");
        CARGO_KEYWORDS.put("RUBBER", "橡胶");
        CARGO_KEYWORDS.put("汽车", "汽车");
        CARGO_KEYWORDS.put("AUTO", "汽车");
        CARGO_KEYWORDS.put("钢", "钢铁");
        CARGO_KEYWORDS.put("铁", "钢铁");
        CARGO_KEYWORDS.put("STEEL", "钢铁");
        CARGO_KEYWORDS.put("IRON", "钢铁");
        CARGO_KEYWORDS.put("木", "木材");
        CARGO_KEYWORDS.put("WOOD", "木材");
        CARGO_KEYWORDS.put("矿", "矿石");
        CARGO_KEYWORDS.put("ORE", "矿石");
        CARGO_KEYWORDS.put("石", "石料");
        CARGO_KEYWORDS.put("STONE", "石料");
    }

    public static final List<String> ALLOWED_CARGO_CATEGORIES = List.of(
            "水泥", "家具", "轮胎", "橡胶", "汽车", "钢铁", "木材", "矿石", "石料");

    /** 把货物名/类别/SKU 归一为规范货类，匹配不上返回 null。 */
    public String normalizeCargo(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : CARGO_KEYWORDS.entrySet()) {
            String keyword = entry.getKey().toUpperCase(Locale.ROOT);
            if (upper.contains(keyword)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 货类偏好值是否在白名单内。 */
    public boolean isValidCargoPreference(String cargo) {
        return cargo != null && ALLOWED_CARGO_CATEGORIES.contains(cargo);
    }

    /** 从运单项解析货类：goods 名称/类别/SKU 优先，shipment.cargoType 兜底。 */
    public String resolveCargo(ShipmentItem item) {
        if (item == null) {
            return null;
        }
        Goods goods = item.getGoods();
        if (goods != null) {
            String normalized = normalizeCargo(goods.getName());
            if (normalized == null) normalized = normalizeCargo(goods.getCategory());
            if (normalized == null) normalized = normalizeCargo(goods.getSku());
            if (normalized != null) return normalized;
        }
        Shipment shipment = item.getShipment();
        if (shipment != null) {
            return normalizeCargo(shipment.getCargoType());
        }
        return null;
    }

    /**
     * 上限型单维得分：实际值不超过偏好上限 → 1.0；
     * 超出按比例衰减（超到 2 倍上限 → 0.0）；偏好或实际值缺失 → 0.5。
     */
    private double limitScore(Double actual, Double limit) {
        if (limit == null || !Double.isFinite(limit) || limit <= 0.0) {
            return 0.5;
        }
        if (actual == null || !Double.isFinite(actual)) {
            return 0.5;
        }
        if (actual <= limit) {
            return 1.0;
        }
        return Math.max(0.0, 1.0 - (actual - limit) / limit);
    }

    /** 司机对单个运单项的综合偏好得分 ∈ [0,1]。司机无偏好时为 0.5（中性）。 */
    public double scoreFor(Driver driver, ShipmentItem item) {
        if (driver == null) {
            return 0.5;
        }
        String actualCargo = resolveCargo(item);
        Double actualDistanceKm = null;
        if (item != null && item.getShipment() != null
                && item.getShipment().getTotalDrivingDistance() != null) {
            actualDistanceKm = item.getShipment().getTotalDrivingDistance() / 1000.0;
        }
        Double actualWeightTons = item != null ? item.getWeight() : null;

        double cargoScore = driver.getPreferredCargoType() == null || actualCargo == null
                ? 0.5
                : (driver.getPreferredCargoType().equals(actualCargo) ? 1.0 : 0.0);
        double distanceScore = limitScore(actualDistanceKm, driver.getPreferredMaxDistanceKm());
        double weightScore = limitScore(actualWeightTons, driver.getPreferredMaxWeightTons());
        return CARGO_WEIGHT * cargoScore + DISTANCE_WEIGHT * distanceScore + WEIGHT_WEIGHT * weightScore;
    }

    /** 车辆对单个运单项的亲和度：取其绑定空闲司机中的最大得分（无司机为 0）。 */
    public double vehicleItemAffinity(Vehicle vehicle, ShipmentItem item) {
        if (vehicle == null || vehicle.getDrivers() == null || vehicle.getDrivers().isEmpty()) {
            return 0.0;
        }
        return vehicle.getDrivers().stream()
                .filter(d -> d.getCurrentStatus() == Driver.DriverStatus.IDLE)
                .mapToDouble(driver -> scoreFor(driver, item))
                .max()
                .orElse(0.0);
    }

    /** 车辆对整批待接单的亲和度（仅用于稳定排序）：对每单取车组最大得分的平均值。 */
    public double vehicleAffinity(Vehicle vehicle, List<ShipmentItem> items) {
        if (vehicle == null || items == null || items.isEmpty()) {
            return 0.0;
        }
        return items.stream()
                .mapToDouble(item -> vehicleItemAffinity(vehicle, item))
                .average()
                .orElse(0.0);
    }

    /** 司机偏好展示文本，如 "偏好: 水泥 / ≤150km / ≤3t"；无任何偏好返回 null。 */
    public String preferenceText(Driver driver) {
        if (driver == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (driver.getPreferredCargoType() != null) {
            parts.add(driver.getPreferredCargoType());
        }
        if (driver.getPreferredMaxDistanceKm() != null) {
            parts.add("≤" + formatNumber(driver.getPreferredMaxDistanceKm()) + "km");
        }
        if (driver.getPreferredMaxWeightTons() != null) {
            parts.add("≤" + formatNumber(driver.getPreferredMaxWeightTons()) + "t");
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "偏好: " + String.join(" / ", parts);
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
