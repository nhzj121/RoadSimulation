package org.example.roadsimulation.service;

import org.example.roadsimulation.service.production.ProductionRules;
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

    private final VehicleRepository vehicleRepository;

    public ProductionTransportLoadSplitter(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public List<LoadPart> split(Goods goods, double totalWeight) {
        validateGoods(goods);
        if (totalWeight <= 0 || !Double.isFinite(totalWeight)) {
            throw new IllegalArgumentException("生产运输总重量必须大于 0");
        }
        return ProductionRules.split(goods, totalWeight, vehicleRepository.findAll());
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
