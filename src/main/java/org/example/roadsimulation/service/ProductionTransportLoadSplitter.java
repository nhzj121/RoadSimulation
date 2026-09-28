package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Splits one production material flow into vehicle-feasible transport load units.
 * Weight is expressed in tonnes and volume in cubic metres throughout this class.
 */
@Service
public class ProductionTransportLoadSplitter {

    private static final double EPSILON = 1.0e-9;

    private final VehicleRepository vehicleRepository;

    public ProductionTransportLoadSplitter(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public List<LoadPart> split(Goods goods, double totalWeight) {
        validateGoods(goods);
        if (totalWeight <= 0 || !Double.isFinite(totalWeight)) {
            throw new IllegalArgumentException("生产运输总重量必须大于 0");
        }

        double volumePerTonne = goods.getVolumePerUnit() / goods.getWeightPerUnit();
        double totalVolume = totalWeight * volumePerTonne;
        List<Vehicle> fleet = vehicleRepository.findAll();
        double maxPartWeight = maximumFeasibleWeight(preferredFleet(goods, fleet), volumePerTonne);
        if (maxPartWeight <= EPSILON) {
            maxPartWeight = maximumFeasibleWeight(fleet, volumePerTonne);
        }
        if (maxPartWeight <= EPSILON) {
            throw new IllegalStateException("没有车辆能够承载生产货物: " + goods.getSku());
        }

        int partCount = Math.max(1, (int) Math.ceil((totalWeight - EPSILON) / maxPartWeight));
        int totalQuantity = Math.max(partCount,
                (int) Math.max(1L, Math.round(totalWeight / goods.getWeightPerUnit())));

        double baseWeight = totalWeight / partCount;
        double baseVolume = totalVolume / partCount;
        int baseQuantity = totalQuantity / partCount;
        int quantityRemainder = totalQuantity % partCount;

        java.util.ArrayList<LoadPart> parts = new java.util.ArrayList<>(partCount);
        double assignedWeight = 0.0;
        double assignedVolume = 0.0;
        for (int index = 0; index < partCount; index++) {
            boolean last = index == partCount - 1;
            double partWeight = last ? totalWeight - assignedWeight : baseWeight;
            double partVolume = last ? totalVolume - assignedVolume : baseVolume;
            int quantity = baseQuantity + (index < quantityRemainder ? 1 : 0);
            parts.add(new LoadPart(partWeight, partVolume, quantity));
            assignedWeight += partWeight;
            assignedVolume += partVolume;
        }
        return List.copyOf(parts);
    }

    private List<Vehicle> preferredFleet(Goods goods, List<Vehicle> fleet) {
        String vehicleFit = goods.getVehicleFit();
        if (vehicleFit == null || vehicleFit.isBlank() || fleet == null) {
            return List.of();
        }
        return fleet.stream()
                .filter(vehicle -> vehicle != null && vehicleFit.equals(vehicle.getVehicleType()))
                .toList();
    }

    private double maximumFeasibleWeight(List<Vehicle> vehicles, double volumePerTonne) {
        if (vehicles == null || vehicles.isEmpty()) {
            return 0.0;
        }
        return vehicles.stream()
                .mapToDouble(vehicle -> feasibleWeight(vehicle, volumePerTonne))
                .max()
                .orElse(0.0);
    }

    private double feasibleWeight(Vehicle vehicle, double volumePerTonne) {
        if (vehicle == null || vehicle.getMaxLoadCapacityTonnes() == null
                || vehicle.getMaxLoadCapacityTonnes() <= 0
                || !Double.isFinite(vehicle.getMaxLoadCapacityTonnes())) {
            return 0.0;
        }
        if (volumePerTonne <= EPSILON) {
            return vehicle.getMaxLoadCapacityTonnes();
        }
        Double cargoVolume = vehicle.getCargoVolume();
        if (cargoVolume == null || cargoVolume <= 0 || !Double.isFinite(cargoVolume)) {
            return 0.0;
        }
        return Math.min(vehicle.getMaxLoadCapacityTonnes(), cargoVolume / volumePerTonne);
    }

    private void validateGoods(Goods goods) {
        if (goods == null || goods.getSku() == null || goods.getSku().isBlank()) {
            throw new IllegalArgumentException("生产运输货物缺少 Goods 主数据");
        }
        if (goods.getWeightPerUnit() == null || goods.getWeightPerUnit() <= 0
                || !Double.isFinite(goods.getWeightPerUnit())) {
            throw new IllegalStateException("生产运输货物单位重量无效: " + goods.getSku());
        }
        if (goods.getVolumePerUnit() == null || goods.getVolumePerUnit() < 0
                || !Double.isFinite(goods.getVolumePerUnit())) {
            throw new IllegalStateException("生产运输货物单位体积无效: " + goods.getSku());
        }
    }

    public record LoadPart(double weight, double volume, int quantity) { }
}
