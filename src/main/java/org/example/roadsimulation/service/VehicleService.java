package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
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

    List<Vehicle> getVehiclesWithActiveAssignments();

    void deleteVehicle(Long id);

    boolean existsByLicensePlate(String licensePlate);

    // ================ 新增的方法 ================

    /**
     * 分配司机到车辆
     * @param vehicleId 车辆ID
     * @param driverName 司机姓名
     * @return 更新后的车辆对象
     */
    Vehicle assignDriverToVehicle(Long vehicleId, String driverName);

    /**
     * 设置车辆位置（移动到指定POI）
     * @param vehicleId 车辆ID
     * @param poiId POI的ID
     * @return 更新后的车辆对象
     */
    Vehicle setVehicleLocation(Long vehicleId, Long poiId);

    /**
     * 根据车辆类型查询车辆
     * @param vehicleType 车辆类型
     * @return 匹配的车辆列表
     */
    List<Vehicle> getVehiclesByType(String vehicleType);

    /**
     * 更新车辆状态
     * @param vehicleId 车辆ID
     * @param status 新的状态
     * @return 更新后的车辆对象
     */
    Vehicle updateVehicleStatus(Long vehicleId, Vehicle.VehicleStatus status);

    /**
     * 更新车辆当前位置坐标
     * @param vehicleId 车辆ID
     * @param longitude 经度
     * @param latitude 纬度
     * @return 更新后的车辆对象
     */
    Vehicle updateVehicleCoordinates(Long vehicleId, BigDecimal longitude, BigDecimal latitude);
}