package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VehicleService {

    Vehicle createVehicle(Vehicle vehicle);

    Vehicle updateVehicle(Long id, Vehicle vehicle);

    Optional<Vehicle> getVehicleById(Long id);

    List<Vehicle> getAllVehicles();

    Page<Vehicle> getAllVehicles(Pageable pageable);

    List<Vehicle> searchVehiclesByLicense(String partialLicense);

    List<Vehicle> getVehiclesByStatus(Vehicle.VehicleStatus status);

    List<Vehicle> getVehiclesWithActiveAssignments();

    void deleteVehicle(Long id);

    boolean existsByLicensePlate(String licensePlate);

    Vehicle assignDriverToVehicle(Long vehicleId, String driverName);

    Vehicle setVehicleLocation(Long vehicleId, Long poiId);

    List<Vehicle> getVehiclesByType(String vehicleType);

    Vehicle updateVehicleStatus(Long vehicleId, Vehicle.VehicleStatus status);

    Vehicle updateVehicleCoordinates(Long vehicleId, BigDecimal longitude, BigDecimal latitude);

    Vehicle calculateLoadingWaitTime(Long vehicleId, LocalDateTime loadingStartTime);

    /**
     * 新增方法：更新车辆指标信息（空驶距离、等待时间、总行驶距离等）
     */
    void updateVehicleMetrics(Vehicle vehicle);
}
