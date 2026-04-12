package org.example.roadsimulation.optimizer;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.service.ShipmentItemService;
import org.example.roadsimulation.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 优化器桥接层
 *
 * 作用：作为 DataInitializer.splitAndCreateShipmentItemsWithSmartMatching() 的直接替代品。
 *
 * 输入：与原方法完全一致
 *   - Shipment shipment
 *   - Goods goods
 *   - Integer totalQuantity
 *   - List<Vehicle> candidateVehicles
 *   - POI startPOI
 *
 * 输出：Map<Vehicle, ShipmentItem>（与原方法完全一致，可直接传入 initalizeAssignment()）
 *
 * 内部流程：
 *   1. 筛选出适配该货物的候选车辆（suitableGoods 匹配）
 *   2. 同时运行 GA 和 SA，选取成本更低的方案
 *   3. 按最优方案创建 ShipmentItem 并组装返回 Map
 *   4. 若优化失败，自动降级到贪心策略（保证系统不中断）
 */
@Component
public class OptimizerBridge {

    private static final Logger log = LoggerFactory.getLogger(OptimizerBridge.class);

    @Autowired
    private QuantityGA gaOptimizer;

    @Autowired
    private QuantitySA saOptimizer;

    @Autowired
    private QuantityEvaluator evaluator;

    @Autowired
    private ShipmentItemService shipmentItemService;

    @Autowired
    private VehicleRepository vehicleRepository;

    /**
     * 优化车辆-货物匹配，返回与原贪心方法完全相同的 Map<Vehicle, ShipmentItem>
     *
     * @param shipment         运单（已保存到数据库）
     * @param goods            货物信息
     * @param totalQuantity    本次需要运输的总件数
     * @param candidateVehicles 候选车辆列表（调用方已按 suitableGoods 和 IDLE 状态筛选）
     * @param startPOI         起点POI（用于日志）
     * @return Map<Vehicle, ShipmentItem>，可直接传入 initalizeAssignment()
     */
    public Map<Vehicle, ShipmentItem> optimizedMatching(
            Shipment shipment,
            Goods goods,
            Integer totalQuantity,
            List<Vehicle> candidateVehicles,
            POI startPOI) {

        log.info("[Optimizer] 开始优化匹配：{}件 {} → {} 辆候选车",
                totalQuantity, goods.getName(), candidateVehicles.size());

        if (candidateVehicles.isEmpty() || totalQuantity == null || totalQuantity <= 0) {
            log.warn("[Optimizer] 候选车辆为空或数量无效，跳过优化");
            return Collections.emptyMap();
        }

        // ── Step1：筛选适配车辆 ──────────────────────────────────────
        List<Vehicle> compatibleVehicles = new ArrayList<>();
        for (Vehicle v : candidateVehicles) {
            if (evaluator.isCompatible(v, goods)) {
                compatibleVehicles.add(v);
            }
        }

        if (compatibleVehicles.isEmpty()) {
            log.warn("[Optimizer] 无适配车辆（suitableGoods不匹配），降级到贪心策略");
            return greedyFallback(shipment, goods, totalQuantity, candidateVehicles);
        }

        log.info("[Optimizer] 适配车辆: {}/{}", compatibleVehicles.size(), candidateVehicles.size());

        // ── Step2：并行运行 GA 和 SA，取更优方案 ─────────────────────
        long seed = System.currentTimeMillis();
        QuantitySolution gaSol, saSol, best;
        try {
            gaSol = gaOptimizer.optimize(compatibleVehicles, goods, totalQuantity, seed);
            saSol = saOptimizer.optimize(compatibleVehicles, goods, totalQuantity, seed);

            // 优先选 feasible 的；都 feasible 则选 cost 更低的
            if (gaSol.isFeasible() && !saSol.isFeasible()) {
                best = gaSol;
                log.info("[Optimizer] 选择 GA 方案（feasible）");
            } else if (!gaSol.isFeasible() && saSol.isFeasible()) {
                best = saSol;
                log.info("[Optimizer] 选择 SA 方案（feasible）");
            } else {
                best = gaSol.getCost() <= saSol.getCost() ? gaSol : saSol;
                log.info("[Optimizer] 选择 {} 方案 cost={:.1f} vs {:.1f}",
                        best == gaSol ? "GA" : "SA", gaSol.getCost(), saSol.getCost());
            }

        } catch (Exception e) {
            log.error("[Optimizer] 算法执行异常，降级到贪心策略: {}", e.getMessage());
            return greedyFallback(shipment, goods, totalQuantity, compatibleVehicles);
        }

        // ── Step3：按最优方案创建 ShipmentItem ────────────────────────
        Map<Vehicle, ShipmentItem> result = buildResultMap(best, compatibleVehicles, shipment, goods, totalQuantity);

        log.info("[Optimizer] 匹配完成：使用{}辆车, 覆盖{}件（共{}件）",
                best.usedVehicleCount(), best.totalAssigned(), totalQuantity);

        return result;
    }

    /**
     * 按方案创建 ShipmentItem 并组装 Map
     */
    private Map<Vehicle, ShipmentItem> buildResultMap(
            QuantitySolution solution,
            List<Vehicle> vehicles,
            Shipment shipment,
            Goods goods,
            int totalQuantity) {

        Map<Vehicle, ShipmentItem> map = new LinkedHashMap<>();
        int[] quantities = solution.getQuantities();
        int assignedTotal = 0;

        for (int j = 0; j < vehicles.size(); j++) {
            int qty = quantities[j];
            if (qty <= 0) continue;

            Vehicle vehicle = vehicles.get(j);
            try {
                ShipmentItem item = shipmentItemService.initalizeShipmentItem(shipment, goods, qty);
                map.put(vehicle, item);

                // 更新车辆当前载重
                if (goods.getWeightPerUnit() != null) {
                    BigDecimal weight = BigDecimal.valueOf(goods.getWeightPerUnit())
                            .multiply(BigDecimal.valueOf(qty))
                            .setScale(2, RoundingMode.HALF_UP);
                    vehicle.setCurrentLoad(weight.doubleValue());
                    vehicleRepository.save(vehicle);
                }

                assignedTotal += qty;
                log.debug("[Optimizer] 车辆 {} 分配 {} 件", vehicle.getLicensePlate(), qty);

            } catch (Exception e) {
                log.error("[Optimizer] 创建 ShipmentItem 失败（车辆 {}）: {}",
                        vehicle.getLicensePlate(), e.getMessage());
            }
        }

        // 如果有未分配的货物，创建一条 null→ShipmentItem 的记录（与原贪心行为一致）
        int remaining = totalQuantity - assignedTotal;
        if (remaining > 0) {
            log.warn("[Optimizer] 仍有 {} 件货物未分配，创建未分配条目", remaining);
            try {
                ShipmentItem remainItem = shipmentItemService.initalizeShipmentItem(shipment, goods, remaining);
                map.put(null, remainItem);
            } catch (Exception e) {
                log.error("[Optimizer] 创建未分配 ShipmentItem 失败: {}", e.getMessage());
            }
        }

        return map;
    }

    /**
     * 降级策略：贪心分配（当优化器失败时使用，保证系统不中断）
     * 逻辑与原 DataInitializer 中的贪心一致
     */
    private Map<Vehicle, ShipmentItem> greedyFallback(
            Shipment shipment, Goods goods, int totalQuantity, List<Vehicle> vehicles) {

        log.info("[Optimizer] 执行贪心降级策略");
        Map<Vehicle, ShipmentItem> map = new LinkedHashMap<>();
        int remaining = totalQuantity;

        if (goods.getWeightPerUnit() == null || goods.getWeightPerUnit() <= 0) {
            log.warn("[Optimizer] 货物单位重量为空，无法贪心分配");
            return map;
        }

        for (Vehicle vehicle : vehicles) {
            if (remaining <= 0) break;
            Double maxLoad = vehicle.getMaxLoadCapacity();
            if (maxLoad == null || maxLoad <= 0) continue;
            int cap = (int) Math.floor(maxLoad / goods.getWeightPerUnit()) - 2;
            if (cap <= 0) continue;
            int assign = Math.min(cap, remaining);
            try {
                ShipmentItem item = shipmentItemService.initalizeShipmentItem(shipment, goods, assign);
                map.put(vehicle, item);
                remaining -= assign;
            } catch (Exception e) {
                log.error("[Optimizer] 贪心创建 ShipmentItem 失败: {}", e.getMessage());
            }
        }

        if (remaining > 0) {
            try {
                ShipmentItem remainItem = shipmentItemService.initalizeShipmentItem(shipment, goods, remaining);
                map.put(null, remainItem);
            } catch (Exception e) {
                log.error("[Optimizer] 贪心未分配条目创建失败: {}", e.getMessage());
            }
        }

        return map;
    }
}
