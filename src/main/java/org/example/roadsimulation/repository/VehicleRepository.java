package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    // 根据车牌号查找
    Vehicle findByLicensePlate(String licensePlate);

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
    Page<Vehicle> findAll(Pageable pageable);

    // 检查车牌号是否存在（唯一性校验）
    boolean existsByLicensePlate(String licensePlate);

    // === 新增方法：用于车辆匹配服务 ===

    // 根据最小载重能力查询可用车辆
    @Query("SELECT v FROM Vehicle v WHERE v.maxLoadCapacity >= :minLoadCapacity AND v.currentStatus = 'IDLE'")
    List<Vehicle> findAvailableByMinLoadCapacity(@Param("minLoadCapacity") Double minLoadCapacity);

    // 根据载重范围查询可用车辆
    @Query("SELECT v FROM Vehicle v WHERE v.maxLoadCapacity BETWEEN :minLoad AND :maxLoad AND v.currentStatus = 'IDLE'")
    List<Vehicle> findAvailableByLoadRange(@Param("minLoad") Double minLoad, @Param("maxLoad") Double maxLoad);

    // 根据品牌和车辆类型查询可用车辆
    List<Vehicle> findByBrandAndVehicleTypeAndCurrentStatus(String brand, String vehicleType, Vehicle.VehicleStatus status);

    // 根据车辆类型和最小载重查询
    @Query("SELECT v FROM Vehicle v WHERE v.vehicleType = :vehicleType AND v.maxLoadCapacity >= :minLoadCapacity AND v.currentStatus = 'IDLE'")
    List<Vehicle> findByVehicleTypeAndMinLoadCapacity(@Param("vehicleType") String vehicleType,
                                                      @Param("minLoadCapacity") Double minLoadCapacity);

    // 综合查询：品牌、类型、最小载重
    @Query("SELECT v FROM Vehicle v WHERE " +
            "(:brand IS NULL OR v.brand = :brand) AND " +
            "(:vehicleType IS NULL OR v.vehicleType = :vehicleType) AND " +
            "(:minLoadCapacity IS NULL OR v.maxLoadCapacity >= :minLoadCapacity) AND " +
            "v.currentStatus = 'IDLE'")
    List<Vehicle> findAvailableVehiclesByCriteria(@Param("brand") String brand,
                                                  @Param("vehicleType") String vehicleType,
                                                  @Param("minLoadCapacity") Double minLoadCapacity);

    // 分页查询可用车辆
    Page<Vehicle> findByCurrentStatus(Vehicle.VehicleStatus status, Pageable pageable);

    // 根据载重能力降序排列可用车辆
    @Query("SELECT v FROM Vehicle v WHERE v.currentStatus = 'IDLE' ORDER BY v.maxLoadCapacity DESC")
    List<Vehicle> findAvailableVehiclesOrderByLoadCapacityDesc();

    // 根据品牌分组统计车辆数量
    @Query("SELECT v.brand, COUNT(v) FROM Vehicle v GROUP BY v.brand")
    List<Object[]> countVehiclesByBrand();

    // 查询所有品牌（去重）
    @Query("SELECT DISTINCT v.brand FROM Vehicle v WHERE v.brand IS NOT NULL")
    List<String> findAllBrands();

    // 查询所有车辆类型（去重）
    @Query("SELECT DISTINCT v.vehicleType FROM Vehicle v WHERE v.vehicleType IS NOT NULL")
    List<String> findAllVehicleTypes();
    @Query("SELECT v FROM Vehicle v WHERE " +
            "v.currentStatus = 'IDLE' AND " +
            "v.currentPOI.id IN (" +
            "  SELECT p.id FROM POI p WHERE " +
            "  (6371 * acos(cos(radians(:latitude)) * cos(radians(p.latitude)) * " +
            "  cos(radians(p.longitude) - radians(:longitude)) + " +
            "  sin(radians(:latitude)) * sin(radians(p.latitude)))) <= :radiusKm)")
    List<Vehicle> findVehiclesNearLocation(@Param("latitude") Double latitude,
                                           @Param("longitude") Double longitude,
                                           @Param("radiusKm") Double radiusKm);
}