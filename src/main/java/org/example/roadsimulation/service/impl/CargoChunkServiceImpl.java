package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.CargoChunk;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.CargoChunkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 货物块拆分实现 —— 按车辆类型容量将大订单预拆分为标准化运输块。
 *
 * <p>核心思路：不直接绑定具体车辆，而是根据所有车辆类型的载重能力，
 * 将总数量拆分为一系列"刚好装满某类车"的标准块。VRP分配时，
 * 每块天然匹配某类车，装载率自然高达80-100%。
 */
@Service
public class CargoChunkServiceImpl implements CargoChunkService {

    private static final Logger logger = LoggerFactory.getLogger(CargoChunkServiceImpl.class);

    /** 车辆载重聚合的精度阈值（吨），容量差在此范围内的视为同类型 */
    private static final double CAPACITY_MERGE_THRESHOLD = 0.5;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Override
    public List<CargoChunk> chunkCargo(Goods goods, int totalQty) {
        if (totalQty <= 0) {
            return Collections.emptyList();
        }

        double weightPerUnit = goods.getWeightPerUnit() != null ? goods.getWeightPerUnit() : 0.0;
        double volumePerUnit = goods.getVolumePerUnit() != null ? goods.getVolumePerUnit() : 0.0;

        // 1. 获取所有车辆类型（按载重聚合去重，从大到小排序）
        List<VehicleCapacity> vehicleTypes = loadAndGroupVehicleTypes();
        if (vehicleTypes.isEmpty()) {
            logger.warn("系统中没有车辆数据，无法拆分");
            return Collections.singletonList(
                    new CargoChunk(totalQty, null, null, null, 0.0, true));
        }

        logger.info("开始货物块拆分: 总数量={}, 货物={} ({}吨/件, {}m³/件), 车辆类型={}种",
                totalQty, goods.getSku(), weightPerUnit, volumePerUnit, vehicleTypes.size());

        // 2. 计算每类车的理想装载量
        for (VehicleCapacity vc : vehicleTypes) {
            int idealByWeight = (weightPerUnit > 0 && vc.maxLoad > 0)
                    ? (int) Math.floor(vc.maxLoad / weightPerUnit) : Integer.MAX_VALUE;
            int idealByVolume = (volumePerUnit > 0 && vc.maxVolume > 0)
                    ? (int) Math.floor(vc.maxVolume / volumePerUnit) : Integer.MAX_VALUE;
            vc.idealQty = Math.min(idealByWeight, idealByVolume);

            if (vc.idealQty > 0) {
                double loadByWeight = (vc.idealQty * weightPerUnit) / vc.maxLoad;
                double loadByVolume = (vc.idealQty * volumePerUnit) / vc.maxVolume;
                vc.expectedLoadFactor = Math.max(loadByWeight, loadByVolume);
            }
        }

        // 3. 检查是否所有车型都无法装下1件
        int maxIdealQty = vehicleTypes.stream().mapToInt(vc -> vc.idealQty).max().orElse(0);
        if (maxIdealQty == 0) {
            logger.error("货物 {} 单件重量/体积超过所有车型容量！最大车型载重={}吨, 单件重={}吨",
                    goods.getSku(),
                    vehicleTypes.stream().mapToDouble(vc -> vc.maxLoad).max().orElse(0),
                    weightPerUnit);
            return Collections.singletonList(
                    new CargoChunk(totalQty, null, null, null, 0.0, true));
        }

        // 4. 贪心拆分：大车类型优先
        List<CargoChunk> chunks = new ArrayList<>();
        int remaining = totalQty;

        for (VehicleCapacity vc : vehicleTypes) {
            if (vc.idealQty <= 0) continue;
            while (remaining >= vc.idealQty) {
                chunks.add(new CargoChunk(vc.idealQty, vc.vehicleTypeName,
                        vc.maxLoad, vc.maxVolume, vc.expectedLoadFactor, false));
                remaining -= vc.idealQty;
            }
        }

        // 5. 处理余数
        if (remaining > 0) {
            // 找能装下remaining的最小车型（保证最高装载率）
            VehicleCapacity bestFit = null;
            int finalRemaining = remaining;
            bestFit = vehicleTypes.stream()
                    .filter(vc -> vc.idealQty >= finalRemaining)
                    .min(Comparator.comparingDouble(vc -> vc.maxLoad))
                    .orElse(null);

            if (bestFit != null && bestFit.idealQty > 0) {
                double loadByWeight = (remaining * weightPerUnit) / bestFit.maxLoad;
                double loadByVolume = (remaining * volumePerUnit) / bestFit.maxVolume;
                double loadFactor = Math.max(loadByWeight, loadByVolume);
                chunks.add(new CargoChunk(remaining, bestFit.vehicleTypeName,
                        bestFit.maxLoad, bestFit.maxVolume, loadFactor, false));
                remaining = 0;
            } else {
                // 没有车型能装下这么多，找能装最多单位的车型，多次拆分
                if (maxIdealQty > 0) {
                    while (remaining > 0) {
                        int chunkQty = Math.min(maxIdealQty, remaining);
                        VehicleCapacity vc = vehicleTypes.stream()
                                .filter(v -> v.idealQty == maxIdealQty).findFirst().orElse(null);
                        if (vc == null || chunkQty <= 0) break;
                        double lf = (chunkQty * weightPerUnit) / vc.maxLoad;
                        chunks.add(new CargoChunk(chunkQty, vc.vehicleTypeName,
                                vc.maxLoad, vc.maxVolume, lf, false));
                        remaining -= chunkQty;
                    }
                }
            }
        }

        if (remaining > 0) {
            logger.warn("货物块拆分完成但仍有 {} 单位无法分配（总数量={}）", remaining, totalQty);
        }

        double minLoad = chunks.stream().mapToDouble(CargoChunk::getExpectedLoadFactor).min().orElse(0) * 100;
        double maxLoad = chunks.stream().mapToDouble(CargoChunk::getExpectedLoadFactor).max().orElse(0) * 100;
        logger.info("货物块拆分完成: {} 个块, 装载率范围 {}%-{}%",
                chunks.size(), String.format("%.0f", minLoad), String.format("%.0f", maxLoad));

        return chunks;
    }

    /**
     * 从数据库加载所有车辆，按载重聚合为车型列表（去重），从大到小排序。
     */
    private List<VehicleCapacity> loadAndGroupVehicleTypes() {
        List<Vehicle> allVehicles = vehicleRepository.findAll();
        if (allVehicles.isEmpty()) {
            return Collections.emptyList();
        }

        // 按载重聚合：载重差在阈值内的视为同类型
        Map<Double, VehicleCapacity> grouped = new TreeMap<>(Comparator.reverseOrder());

        for (Vehicle v : allVehicles) {
            Double load = v.getMaxLoadCapacity();
            Double volume = v.getCargoVolume();
            if (load == null || load <= 0) continue;

            // 找到最近的聚合桶
            Double bucketKey = grouped.keySet().stream()
                    .filter(k -> Math.abs(k - load) <= CAPACITY_MERGE_THRESHOLD)
                    .findFirst()
                    .orElse(load);

            if (!grouped.containsKey(bucketKey) || bucketKey.equals(load)) {
                String typeName = (v.getVehicleType() != null ? v.getVehicleType() + " " : "")
                        + v.getLicensePlate();
                grouped.put(bucketKey, new VehicleCapacity(
                        bucketKey, volume != null ? volume : 0.0, typeName));
            }
        }

        return new ArrayList<>(grouped.values());
    }

    /**
     * 车辆容量信息（内部类）
     */
    private static class VehicleCapacity {
        final double maxLoad;
        final double maxVolume;
        final String vehicleTypeName;
        int idealQty;
        double expectedLoadFactor;

        VehicleCapacity(double maxLoad, double maxVolume, String vehicleTypeName) {
            this.maxLoad = maxLoad;
            this.maxVolume = maxVolume;
            this.vehicleTypeName = vehicleTypeName;
        }
    }
}
