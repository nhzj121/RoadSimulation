package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Vehicle;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * VehicleRepository
 *
 * 功能说明：
 * 1. 继承 JpaRepository 提供基础 CRUD 功能
 * 2. 提供额外查询方法：
 *    - 按车牌号精确/模糊查询
 *    - 按品牌或车型查询
 *    - 按状态查询
 *    - 分页查询
 *    - 唯一性校验车牌号
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    // 根据车牌号查找
    Optional<Vehicle> findByLicensePlate(String licensePlate);

    // 模糊查询车牌号（忽略大小写）
    List<Vehicle> findByLicensePlateContainingIgnoreCase(String partialLicense);

    // 根据品牌查找
    List<Vehicle> findByBrand(String brand);

    // 根据车型查找
    List<Vehicle> findByModelType(String modelType);

    // 根据车辆类型查找（使用已有的 vehicleType 字段）
    List<Vehicle> findByVehicleType(String vehicleType);

    // 根据车辆状态查找
    List<Vehicle> findByCurrentStatus(Vehicle.VehicleStatus status);

    // 分页查询
    @NotNull Page<Vehicle> findAll(@NotNull Pageable pageable);

    // 检查车牌号是否存在（唯一性校验）
    boolean existsByLicensePlate(String licensePlate);

    // 新增Modbus相关查询方法
    List<Vehicle> findByModbusSlaveIdIsNotNull();

    List<Vehicle> findByIsOnline(Boolean isOnline);

    Optional<Vehicle> findByModbusSlaveId(Integer modbusSlaveId);

    @Query("SELECT v FROM Vehicle v WHERE v.lastPositionUpdate IS NOT NULL ORDER BY v.lastPositionUpdate DESC")
    List<Vehicle> findVehiclesWithRecentPosition();
}