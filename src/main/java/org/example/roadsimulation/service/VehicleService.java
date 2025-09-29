package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * VehicleService
 *
 * 功能说明：
 * 1. 定义 Vehicle 的业务接口
 * 2. 支持增删改查、分页、模糊搜索、状态查询、唯一性校验
 * 3. 对 Driver/POI 的双向关系需通过实体内部方法维护
 */
public interface VehicleService {

    Vehicle createVehicle(Vehicle vehicle);

    Vehicle updateVehicle(Long id, Vehicle vehicle);

    Optional<Vehicle> getVehicleById(Long id);

    List<Vehicle> getAllVehicles();

    Page<Vehicle> getAllVehicles(Pageable pageable);

    List<Vehicle> searchVehiclesByLicense(String partialLicense);

    List<Vehicle> getVehiclesByStatus(Vehicle.VehicleStatus status);

    void deleteVehicle(Long id);

    boolean existsByLicensePlate(String licensePlate);
}
