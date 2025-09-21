package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;

import java.util.List;
import java.util.Optional;

public interface VehicleService {

    Vehicle saveVehicle(Vehicle vehicle);

    void deleteVehicle(Long id);

    Optional<Vehicle> findById(Long id);

    List<Vehicle> findAll();

    Vehicle findByLicensePlate(String licensePlate);

    List<Vehicle> findByCurrentStatus(Vehicle.VehicleStatus status);

    List<Vehicle> findByVehicleType(String vehicleType);

    List<Vehicle> findByCurrentPOIId(Long poiId);

    List<Vehicle> findSuitableIdleVehicles(Double requiredCapacity);
}

