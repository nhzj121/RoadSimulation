package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionTransportLoadSplitterTest {

    @Test
    void splitsEvenlyByWeightAndVolumeCapacity() {
        Goods goods = goods("PANEL", 0.1, 0.4, "VAN");
        Vehicle preferred = vehicle("VAN", 10.0, 20.0);
        Vehicle largerButWrongType = vehicle("TRUCK", 30.0, 100.0);
        ProductionTransportLoadSplitter splitter = splitter(preferred, largerButWrongType);

        List<ProductionTransportLoadSplitter.LoadPart> parts = splitter.split(goods, 12.0);

        assertThat(parts).hasSize(3);
        assertThat(parts).allSatisfy(part -> {
            assertThat(part.weight()).isCloseTo(4.0, within(1.0e-9));
            assertThat(part.volume()).isCloseTo(16.0, within(1.0e-9));
            assertThat(part.quantity()).isEqualTo(40);
        });
        assertThat(parts.stream().mapToDouble(ProductionTransportLoadSplitter.LoadPart::weight).sum())
                .isCloseTo(12.0, within(1.0e-9));
        assertThat(parts.stream().mapToDouble(ProductionTransportLoadSplitter.LoadPart::volume).sum())
                .isCloseTo(48.0, within(1.0e-9));
    }

    @Test
    void keepsSmallFlowAsSingleLoadPart() {
        ProductionTransportLoadSplitter splitter = splitter(vehicle("TRUCK", 15.0, 30.0));

        List<ProductionTransportLoadSplitter.LoadPart> parts =
                splitter.split(goods("TIRE", 0.09, 0.04, null), 2.7);

        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).weight()).isCloseTo(2.7, within(1.0e-9));
        assertThat(parts.get(0).volume()).isCloseTo(1.2, within(1.0e-9));
        assertThat(parts.get(0).quantity()).isEqualTo(30);
    }

    @Test
    void fallsBackToWholeFleetWhenPreferredTypeCannotCarryCargo() {
        Goods goods = goods("STEEL", 0.1, 0.2, "VAN");
        Vehicle unusablePreferred = vehicle("VAN", 10.0, 0.0);
        Vehicle fallback = vehicle("TRUCK", 5.0, 20.0);

        List<ProductionTransportLoadSplitter.LoadPart> parts =
                splitter(unusablePreferred, fallback).split(goods, 8.0);

        assertThat(parts).hasSize(2);
        assertThat(parts).allSatisfy(part -> assertThat(part.weight()).isEqualTo(4.0));
    }

    @Test
    void rejectsCargoWhenNoVehicleHasPhysicalCapacity() {
        ProductionTransportLoadSplitter splitter = splitter(vehicle("TRUCK", 10.0, 0.0));

        assertThatThrownBy(() -> splitter.split(goods("STEEL", 0.1, 0.2, null), 1.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有车辆能够承载");
    }

    private ProductionTransportLoadSplitter splitter(Vehicle... vehicles) {
        VehicleRepository repository = mock(VehicleRepository.class);
        when(repository.findAll()).thenReturn(List.of(vehicles));
        return new ProductionTransportLoadSplitter(repository);
    }

    private Goods goods(String sku, double unitWeight, double unitVolume, String vehicleFit) {
        Goods goods = new Goods("Goods " + sku, sku);
        goods.setWeightPerUnit(unitWeight);
        goods.setVolumePerUnit(unitVolume);
        goods.setVehicleFit(vehicleFit);
        return goods;
    }

    private Vehicle vehicle(String type, double maxWeight, double maxVolume) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleType(type);
        vehicle.setMaxLoadCapacityTonnes(maxWeight);
        vehicle.setCargoVolume(maxVolume);
        return vehicle;
    }

    private Vehicle vehicle(double maxWeight, double maxVolume) {
        return vehicle("TRUCK", maxWeight, maxVolume);
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
