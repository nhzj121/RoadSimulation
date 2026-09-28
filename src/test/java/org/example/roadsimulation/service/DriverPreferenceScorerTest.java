package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverPreferenceScorerTest {

    private final DriverPreferenceScorer scorer = new DriverPreferenceScorer();

    @Test
    void normalizeCargoMapsChineseAndEnglishKeywords() {
        assertEquals("水泥", scorer.normalizeCargo("水泥"));
        assertEquals("水泥", scorer.normalizeCargo("CEMENT-42.5"));
        assertEquals("木材", scorer.normalizeCargo("原木"));
        assertEquals("钢铁", scorer.normalizeCargo("SEMIF_STEEL"));
        assertEquals("钢铁", scorer.normalizeCargo("铁矿石"));
        assertEquals("家具", scorer.normalizeCargo("FURNITURE"));
        assertNull(scorer.normalizeCargo("未知货物"));
        assertNull(scorer.normalizeCargo(null));
        assertNull(scorer.normalizeCargo("  "));
    }

    @Test
    void validatesCargoPreferenceAgainstWhitelist() {
        assertTrue(scorer.isValidCargoPreference("水泥"));
        assertTrue(scorer.isValidCargoPreference("钢铁"));
        assertFalse(scorer.isValidCargoPreference("水泥石"));
        assertFalse(scorer.isValidCargoPreference(null));
    }

    @Test
    void scoreIsOneWhenActualWithinLimitsAndCargoMatches() {
        // 距离 100km ≤ 150km、重量 2t ≤ 3t、货类命中 → 满分
        ShipmentItem item = item("水泥", 2.0, 100_000.0);
        Driver matching = driver("水泥", 150.0, 3.0);
        assertEquals(1.0, scorer.scoreFor(matching, item), 1e-9);
    }

    @Test
    void limitScoreDecaysProportionallyBeyondLimit() {
        // 货类命中(0.4) + 距离 200km/上限100km → 超1倍 → 0.0(0.3)
        //   + 重量 2t/上限1t → 超1倍 → 0.0(0.3) → 总分 0.4
        ShipmentItem item = item("水泥", 2.0, 200_000.0);
        Driver driver = driver("水泥", 100.0, 1.0);
        assertEquals(0.4, scorer.scoreFor(driver, item), 1e-9);

        // 距离 150km/上限100km → 超出 50% → 0.5(0.3*0.5=0.15)
        // 重量 1.5t/上限1t → 超出 50% → 0.5(0.3*0.5=0.15) → 总分 0.4+0.15+0.15=0.7
        ShipmentItem halfOver = item("水泥", 1.5, 150_000.0);
        assertEquals(0.7, scorer.scoreFor(driver, halfOver), 1e-9);
    }

    @Test
    void unknownDimensionsScoreHalfAndNullDriverIsNeutral() {
        // 无 shipment(距离未知 0.5) + 货类命中 + 重量命中 → 0.4 + 0.15 + 0.3 = 0.85
        ShipmentItem itemWithoutShipment = item("水泥", 2.0, null);
        Driver driver = driver("水泥", 150.0, 3.0);
        assertEquals(0.85, scorer.scoreFor(driver, itemWithoutShipment), 1e-9);

        // 无偏好司机：三维全未知 → 0.5
        Driver noPrefs = new Driver();
        assertEquals(0.5, scorer.scoreFor(noPrefs, itemWithoutShipment), 1e-9);

        // 无司机 → 中性 0.5
        assertEquals(0.5, scorer.scoreFor(null, itemWithoutShipment), 1e-9);
    }

    @Test
    void vehicleItemAffinityTakesMaxAcrossBoundIdleDrivers() {
        ShipmentItem item = item("水泥", 2.0, 100_000.0);
        Vehicle vehicle = new Vehicle();

        Driver matching = driver("水泥", 150.0, 3.0);
        matching.setCurrentStatus(Driver.DriverStatus.IDLE);
        matching.addVehicle(vehicle);
        Driver mismatching = driver("木材", 50.0, 1.0);
        mismatching.setCurrentStatus(Driver.DriverStatus.IDLE);
        mismatching.addVehicle(vehicle);

        assertEquals(1.0, scorer.vehicleItemAffinity(vehicle, item), 1e-9);

        Vehicle noDriverVehicle = new Vehicle();
        assertEquals(0.0, scorer.vehicleItemAffinity(noDriverVehicle, item), 1e-9);
    }

    @Test
    void vehicleItemAffinityIgnoresMaintenanceAndRejectingDrivers() {
        // 保养/拒单司机即使满分也不计入（行为状态表联动）
        ShipmentItem item = item("水泥", 2.0, 100_000.0);
        Vehicle vehicle = new Vehicle();

        Driver maintenance = driver("水泥", 150.0, 3.0);
        maintenance.setCurrentStatus(Driver.DriverStatus.MAINTENANCE);
        maintenance.addVehicle(vehicle);
        Driver rejecting = driver("水泥", 150.0, 3.0);
        rejecting.setCurrentStatus(Driver.DriverStatus.REJECTING);
        rejecting.addVehicle(vehicle);

        assertEquals(0.0, scorer.vehicleItemAffinity(vehicle, item), 1e-9);
    }

    @Test
    void vehicleItemAffinityUsesIdleDriverWhenMixedWithNonIdle() {
        // 保养司机满分 + 空闲司机低分 → 结果应取空闲司机而非保养司机
        ShipmentItem item = item("水泥", 2.0, 100_000.0);
        Vehicle vehicle = new Vehicle();

        Driver maintenance = driver("水泥", 150.0, 3.0);
        maintenance.setCurrentStatus(Driver.DriverStatus.MAINTENANCE);
        maintenance.addVehicle(vehicle);
        Driver idle = driver("木材", 50.0, 1.0);
        idle.setCurrentStatus(Driver.DriverStatus.IDLE);
        idle.addVehicle(vehicle);

        assertEquals(0.0, scorer.vehicleItemAffinity(vehicle, item), 1e-9);
    }

    @Test
    void preferenceTextRendersNumericLimits() {
        Driver full = driver("水泥", 150.0, 3.0);
        assertEquals("偏好: 水泥 / ≤150km / ≤3t", scorer.preferenceText(full));

        Driver fractional = driver("钢铁", 80.5, 2.5);
        assertEquals("偏好: 钢铁 / ≤80.5km / ≤2.5t", scorer.preferenceText(fractional));

        Driver partial = driver("钢铁", null, null);
        assertEquals("偏好: 钢铁", scorer.preferenceText(partial));

        assertNull(scorer.preferenceText(new Driver()));
        assertNull(scorer.preferenceText(null));
    }

    private Driver driver(String cargo, Double maxDistanceKm, Double maxWeightTons) {
        Driver driver = new Driver();
        driver.setPreferredCargoType(cargo);
        driver.setPreferredMaxDistanceKm(maxDistanceKm);
        driver.setPreferredMaxWeightTons(maxWeightTons);
        return driver;
    }

    private ShipmentItem item(String goodsName, double weightTons, Double distanceMeters) {
        ShipmentItem item = new ShipmentItem();
        item.setGoods(new Goods(goodsName, "TEST_SKU"));
        item.setWeight(weightTons);
        if (distanceMeters != null) {
            Shipment shipment = new Shipment();
            shipment.setTotalDrivingDistance(distanceMeters);
            item.setShipment(shipment);
        }
        return item;
    }
}
