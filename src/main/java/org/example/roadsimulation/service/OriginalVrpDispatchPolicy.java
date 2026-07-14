package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Configurable acceptance rules used by the ORIGINAL greedy packing loop.
 * The orchestration and assignment materialization remain in {@code DataInitializer}.
 */
@Component
public class OriginalVrpDispatchPolicy {

    static final double DEFAULT_MIN_LOAD_FACTOR = 0.80;
    static final double DEFAULT_MAX_ANCHOR_DISTANCE_KM = 400.0;
    static final double DEFAULT_MAX_MARGINAL_COST = 4000.0;
    static final double DEFAULT_MIN_ADDED_TONS_PER_EXTRA_KM = 0.02;
    private static final double DEFAULT_EFFECTIVE_EXTRA_MILEAGE_KM = 5.0;

    private double minLoadFactor = DEFAULT_MIN_LOAD_FACTOR;
    private double maxAnchorDistanceKm = DEFAULT_MAX_ANCHOR_DISTANCE_KM;
    private double maxMarginalCost = DEFAULT_MAX_MARGINAL_COST;
    private double minAddedTonsPerExtraKm = DEFAULT_MIN_ADDED_TONS_PER_EXTRA_KM;

    public OriginalVrpDispatchPolicy() {
    }

    public OriginalVrpDispatchPolicy(
            double minLoadFactor,
            double maxAnchorDistanceKm,
            double maxMarginalCost,
            double minAddedTonsPerExtraKm
    ) {
        this.minLoadFactor = minLoadFactor;
        this.maxAnchorDistanceKm = maxAnchorDistanceKm;
        this.maxMarginalCost = maxMarginalCost;
        this.minAddedTonsPerExtraKm = minAddedTonsPerExtraKm;
    }

    @Value("${app.simulation.original-vrp.min-load-factor:" + DEFAULT_MIN_LOAD_FACTOR + "}")
    public void setMinLoadFactor(double minLoadFactor) {
        this.minLoadFactor = minLoadFactor;
    }

    @Value("${app.simulation.original-vrp.max-anchor-distance-km:" + DEFAULT_MAX_ANCHOR_DISTANCE_KM + "}")
    public void setMaxAnchorDistanceKm(double maxAnchorDistanceKm) {
        this.maxAnchorDistanceKm = maxAnchorDistanceKm;
    }

    @Value("${app.simulation.original-vrp.max-marginal-cost:" + DEFAULT_MAX_MARGINAL_COST + "}")
    public void setMaxMarginalCost(double maxMarginalCost) {
        this.maxMarginalCost = maxMarginalCost;
    }

    @Value("${app.simulation.original-vrp.min-added-tons-per-extra-km:" + DEFAULT_MIN_ADDED_TONS_PER_EXTRA_KM + "}")
    public void setMinAddedTonsPerExtraKm(double minAddedTonsPerExtraKm) {
        this.minAddedTonsPerExtraKm = minAddedTonsPerExtraKm;
    }

    public double calculateLoadFactor(Vehicle vehicle, Collection<ShipmentItem> items) {
        if (vehicle == null || items == null || items.isEmpty()) {
            return 0.0;
        }

        double totalWeight = items.stream().mapToDouble(item -> safe(item == null ? null : item.getWeight())).sum();
        double totalVolume = items.stream().mapToDouble(item -> safe(item == null ? null : item.getVolume())).sum();
        double maxLoad = safe(vehicle.getMaxLoadCapacity());
        double maxVolume = safe(vehicle.getCargoVolume());

        double weightFactor = maxLoad > 0.0 ? totalWeight / maxLoad : 0.0;
        double volumeFactor = maxVolume > 0.0 ? totalVolume / maxVolume : 0.0;
        return Math.max(weightFactor, volumeFactor);
    }

    public boolean meetsMinLoadFactor(Vehicle vehicle, Collection<ShipmentItem> items) {
        return calculateLoadFactor(vehicle, items) >= minLoadFactor;
    }

    public boolean acceptsMarginalCost(Double marginalCost) {
        return marginalCost != null
                && Double.isFinite(marginalCost)
                && marginalCost < maxMarginalCost;
    }

    public boolean isWorthAdding(ShipmentItem item, double extraMileageKm) {
        return calculateAddedTonsPerExtraKm(item, extraMileageKm) >= minAddedTonsPerExtraKm;
    }

    public double calculateAddedTonsPerExtraKm(ShipmentItem item, double extraMileageKm) {
        double effectiveExtraMileageKm = effectiveExtraMileageKm(extraMileageKm);
        if (effectiveExtraMileageKm <= 0.0) {
            return 0.0;
        }
        return safe(item == null ? null : item.getWeight()) / effectiveExtraMileageKm;
    }

    public double effectiveExtraMileageKm(double extraMileageKm) {
        return extraMileageKm > 0.0 && Double.isFinite(extraMileageKm)
                ? extraMileageKm
                : DEFAULT_EFFECTIVE_EXTRA_MILEAGE_KM;
    }

    public double getMinLoadFactor() {
        return minLoadFactor;
    }

    public double getMaxAnchorDistanceKm() {
        return maxAnchorDistanceKm;
    }

    public double getMaxMarginalCost() {
        return maxMarginalCost;
    }

    public double getMinAddedTonsPerExtraKm() {
        return minAddedTonsPerExtraKm;
    }

    private double safe(Double value) {
        return value == null || Double.isNaN(value) || Double.isInfinite(value) ? 0.0 : value;
    }
}
