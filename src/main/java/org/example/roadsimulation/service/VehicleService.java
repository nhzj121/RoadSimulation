
// VehicleService.java (接口)
package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import java.util.List;

public interface VehicleService {
    List<Vehicle> getAllVehicles();
    Vehicle getVehicleById(Long id);
    Vehicle createVehicle(Vehicle vehicle);
    Vehicle updateVehicle(Long id, Vehicle vehicleDetails);
    void deleteVehicle(Long id);
    List<Vehicle> getVehiclesByStatus(Vehicle.VehicleStatus status);
    Vehicle updateVehicleStatus(Long vehicleId, Vehicle.VehicleStatus newStatus);
    Vehicle assignToPOI(Long vehicleId, Long poiId);
    List<Vehicle> findAvailableVehiclesWithMinCapacity(Double minCapacity);
}