package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    // 根据车牌号查找车辆
    Vehicle findByLicensePlate(String licensePlate);

    // 根据车辆状态查找
    List<Vehicle> findByCurrentStatus(Vehicle.VehicleStatus status);

    // 根据车辆类型查找
    List<Vehicle> findByVehicleType(String vehicleType);

    // 查找载重能力大于指定值的车辆
    List<Vehicle> findByMaxLoadCapacityGreaterThan(Double capacity);

    // 查找当前位置在某POI的车辆
    List<Vehicle> findByCurrentPOIId(Long poiId);

    // 使用 JPQL 自定义查询示例：查找空闲状态且载重满足要求的车辆
    @Query("SELECT v FROM Vehicle v WHERE v.currentStatus = org.example.roadsimulation.entity.Vehicle.VehicleStatus.IDLE " +
            "AND v.maxLoadCapacity >= :requiredCapacity " +
            "ORDER BY v.maxLoadCapacity ASC")
    List<Vehicle> findSuitableIdleVehicles(@Param("requiredCapacity") Double requiredCapacity);
}
