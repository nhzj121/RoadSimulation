package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OriginalVrpDispatchPolicyTest {

    private final OriginalVrpDispatchPolicy policy = new OriginalVrpDispatchPolicy(
            0.80,
            400.0,
            4000.0,
            0.02
    );

    @Test
    void rejectsLoadFactorBelowEightyPercent() {
        Vehicle vehicle = vehicle(2.0, 17.2);
        ShipmentItem item = item(0.3, 0.2);

        double loadFactor = policy.calculateLoadFactor(vehicle, List.of(item));

        assertEquals(0.15, loadFactor, 1e-9);
        assertFalse(policy.meetsMinLoadFactor(vehicle, List.of(item)));
    }

    @Test
    void acceptsLoadFactorAboveEightyPercent() {
        Vehicle vehicle = vehicle(2.0, 17.2);
        ShipmentItem item = item(1.7, 0.2);

        double loadFactor = policy.calculateLoadFactor(vehicle, List.of(item));

        assertEquals(0.85, loadFactor, 1e-9);
        assertTrue(policy.meetsMinLoadFactor(vehicle, List.of(item)));
    }

    @Test
    void rejectsAddedOrderBelowTonsPerExtraKmThreshold() {
        ShipmentItem item = item(0.1, 0.2);

        assertEquals(0.01, policy.calculateAddedTonsPerExtraKm(item, 10.0), 1e-9);
        assertFalse(policy.isWorthAdding(item, 10.0));
    }

    @Test
    void acceptsAddedOrderAboveTonsPerExtraKmThreshold() {
        ShipmentItem item = item(0.3, 0.2);

        assertEquals(0.03, policy.calculateAddedTonsPerExtraKm(item, 10.0), 1e-9);
        assertTrue(policy.isWorthAdding(item, 10.0));
    }

    @Test
    void usesHigherOfWeightAndVolumeLoadFactors() {
        Vehicle vehicle = vehicle(10.0, 1.0);
        ShipmentItem item = item(0.5, 0.9);

        double loadFactor = policy.calculateLoadFactor(vehicle, List.of(item));

        assertEquals(0.9, loadFactor, 1e-9);
        assertTrue(policy.meetsMinLoadFactor(vehicle, List.of(item)));
    }

    private Vehicle vehicle(double maxLoad, double maxVolume) {
        Vehicle vehicle = new Vehicle();
        vehicle.setMaxLoadCapacity(maxLoad);
        vehicle.setCargoVolume(maxVolume);
        return vehicle;
    }

    private ShipmentItem item(double weight, double volume) {
        ShipmentItem item = new ShipmentItem();
        item.setWeight(weight);
        item.setVolume(volume);
        return item;
    }
}
