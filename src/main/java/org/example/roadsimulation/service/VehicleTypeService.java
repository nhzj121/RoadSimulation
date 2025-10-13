package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.VehicleType;

import java.util.List;

public interface VehicleTypeService {

    List<VehicleType> getAllVehicleTypes();

    VehicleType getVehicleTypeById(Long id);

    VehicleType createVehicleType(VehicleType vehicleType);

    VehicleType updateVehicleType(Long id, VehicleType vehicleType);

    void deleteVehicleType(Long id);

    List<VehicleType> getVehicleTypesByCategory(String category);

    List<VehicleType> getVehicleTypesByLoadCapacity(Double minWeight, Double minVolume);
}