package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.VehicleMatchingRequest;
import org.example.roadsimulation.dto.VehicleMatchingResult;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.VehicleType;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.VehicleMatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class VehicleMatchingServiceImpl implements VehicleMatchingService {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private GoodsRepository goodsRepository;

    @Override
    public VehicleMatchingResult matchVehicleForGoods(VehicleMatchingRequest request) {
        Goods goods = goodsRepository.findById(request.getGoodsId())
                .orElseThrow(() -> new IllegalArgumentException("货物不存在: " + request.getGoodsId()));

        double totalWeight = calculateTotalWeight(goods, request);
        double totalVolume = calculateTotalVolume(goods, request);

        List<Vehicle> availableVehicles = vehicleRepository.findAvailableVehicles();

        List<VehicleMatchingResult.VehicleScore> scoredVehicles = availableVehicles.stream()
                .map(vehicle -> {
                    double score = calculateVehicleScore(vehicle, goods, totalWeight, totalVolume, request);
                    return new VehicleMatchingResult.VehicleScore(vehicle, score);
                })
                .filter(score -> score.getScore() > 0)
                .sorted(Comparator.comparing(VehicleMatchingResult.VehicleScore::getScore).reversed())
                .collect(Collectors.toList());

        return new VehicleMatchingResult(goods, request, scoredVehicles);
    }

    @Override
    public List<VehicleMatchingResult> batchMatchVehicles(List<VehicleMatchingRequest> requests) {
        return requests.stream()
                .map(this::matchVehicleForGoods)
                .collect(Collectors.toList());
    }

    @Override
    public VehicleMatchingResult quickMatch(Long goodsId, Integer quantity) {
        VehicleMatchingRequest request = new VehicleMatchingRequest(goodsId, quantity);
        return matchVehicleForGoods(request);
    }

    private double calculateTotalWeight(Goods goods, VehicleMatchingRequest request) {
        if (request.getTotalWeight() != null) {
            return request.getTotalWeight();
        }
        return goods.getWeightPerUnit() != null ? goods.getWeightPerUnit() * request.getQuantity() : 0;
    }

    private double calculateTotalVolume(Goods goods, VehicleMatchingRequest request) {
        if (request.getTotalVolume() != null) {
            return request.getTotalVolume();
        }
        return goods.getVolumePerUnit() != null ? goods.getVolumePerUnit() * request.getQuantity() : 0;
    }

    private double calculateVehicleScore(Vehicle vehicle, Goods goods,
                                         double totalWeight, double totalVolume,
                                         VehicleMatchingRequest request) {
        double score = 0;

        // 1. 实时载重能力匹配 (35%)
        double capacityScore = calculateCapacityScore(vehicle, totalWeight);
        if (capacityScore == 0) return 0;
        score += capacityScore * 0.35;

        // 2. 实时容积匹配 (25%)
        double volumeScore = calculateVolumeScore(vehicle, totalVolume);
        if (volumeScore == 0) return 0;
        score += volumeScore * 0.25;

        // 3. 车型规格匹配 (15%)
        double typeScore = calculateVehicleTypeScore(vehicle.getVehicleTypeEntity(), goods, totalWeight, totalVolume, request);
        score += typeScore * 0.15;

        // 4. 位置匹配 (10%)
        double locationScore = calculateLocationScore(vehicle, request);
        score += locationScore * 0.1;

        // 5. 温控要求匹配 (8%)
        double tempScore = calculateTempControlScore(vehicle, goods, request);
        score += tempScore * 0.08;

        // 6. 危险品匹配 (5%)
        double hazmatScore = calculateHazmatScore(vehicle, goods, request);
        score += hazmatScore * 0.05;

        // 7. 特殊要求匹配 (2%)
        double specialReqScore = calculateSpecialRequirementsScore(vehicle, request);
        score += specialReqScore * 0.02;

        return score;
    }

    private double calculateCapacityScore(Vehicle vehicle, double totalWeight) {
        double availableCapacity = vehicle.getAvailableCapacity();

        if (totalWeight > availableCapacity) {
            return 0;
        }

        double utilization = totalWeight / availableCapacity;

        if (utilization >= 0.7 && utilization <= 0.85) {
            return 1.0;
        } else if (utilization >= 0.5 && utilization < 0.7) {
            return 0.8;
        } else if (utilization > 0.85 && utilization <= 1.0) {
            return 0.7;
        } else {
            return 0.5;
        }
    }

    private double calculateVolumeScore(Vehicle vehicle, double totalVolume) {
        double availableVolume = vehicle.getAvailableVolume();

        if (totalVolume > availableVolume) {
            return 0;
        }

        double utilization = totalVolume / availableVolume;

        if (utilization >= 0.7 && utilization <= 0.85) {
            return 1.0;
        } else if (utilization >= 0.5 && utilization < 0.7) {
            return 0.8;
        } else if (utilization > 0.85 && utilization <= 1.0) {
            return 0.7;
        } else {
            return 0.5;
        }
    }

    private double calculateVehicleTypeScore(VehicleType vehicleType, Goods goods,
                                             double totalWeight, double totalVolume,
                                             VehicleMatchingRequest request) {
        if (vehicleType == null) {
            return 0.5;
        }

        if (totalWeight > vehicleType.getMaxLoadWeight() ||
                totalVolume > vehicleType.getMaxLoadVolume()) {
            return 0;
        }

        if (totalWeight < vehicleType.getMinLoadWeight()) {
            return 0.3;
        }

        return 1.0;
    }

    private double calculateLocationScore(Vehicle vehicle, VehicleMatchingRequest request) {
        if (request.getStartLongitude() == null || request.getStartLatitude() == null ||
                vehicle.getCurrentLongitude() == null || vehicle.getCurrentLatitude() == null) {
            return 0.5;
        }

        double distance = calculateDistance(
                vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude(),
                request.getStartLatitude(), request.getStartLongitude()
        );

        if (distance <= 10) {
            return 1.0;
        } else if (distance <= 50) {
            return 0.8;
        } else if (distance <= 100) {
            return 0.6;
        } else {
            return 0.3;
        }
    }

    private double calculateTempControlScore(Vehicle vehicle, Goods goods, VehicleMatchingRequest request) {
        Boolean requireTemp = request.getRequireTempControl() != null ?
                request.getRequireTempControl() : goods.getRequireTemp();

        if (requireTemp == null || !requireTemp) {
            return 1.0;
        }

        VehicleType vehicleType = vehicle.getVehicleTypeEntity();
        if (vehicleType != null && Boolean.TRUE.equals(vehicleType.getHasTempControl())) {
            return 1.0;
        }

        return 0;
    }

    private double calculateHazmatScore(Vehicle vehicle, Goods goods, VehicleMatchingRequest request) {
        String hazmatLevel = request.getHazmatLevel() != null ?
                request.getHazmatLevel() : goods.getHazmatLevel();

        if (hazmatLevel == null) {
            return 1.0;
        }

        VehicleType vehicleType = vehicle.getVehicleTypeEntity();
        if (vehicleType != null && vehicleType.supportsHazmatLevel(hazmatLevel)) {
            return 1.0;
        }

        return 0;
    }

    private double calculateSpecialRequirementsScore(Vehicle vehicle, VehicleMatchingRequest request) {
        if (request.getSpecialRequirements() == null || request.getSpecialRequirements().isEmpty()) {
            return 1.0;
        }

        int matchedCount = 0;
        VehicleType vehicleType = vehicle.getVehicleTypeEntity();

        if (vehicleType != null) {
            for (String requirement : request.getSpecialRequirements()) {
                if (vehicleType.hasSpecialFeature(requirement)) {
                    matchedCount++;
                }
            }
        }

        return (double) matchedCount / request.getSpecialRequirements().size();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}