package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * VehicleRepository
 *
 * 说明：
 * - 继承 JpaRepository<Vehicle, Long> 后可直接使用 CRUD 方法（save, findById, findAll, deleteById 等）。
 * - 在此定义基于方法名的查询（Spring Data JPA 会自动实现）。
 * - 也提供一个示例 JPQL 查询（findSuitableIdleVehicles）。
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /**
     * 根据车牌号查找（精确匹配）。假定车牌号在系统中唯一。
     */
    Vehicle findByLicensePlate(String licensePlate);

    /**
     * 根据车牌号模糊查询（包含关系），例如前端搜索。
     */
    List<Vehicle> findByLicensePlateContainingIgnoreCase(String partialPlate);

    /**
     * 根据车辆状态查询（例如 IDLE, TRANSPORTING 等）。
     */
    List<Vehicle> findByCurrentStatus(Vehicle.VehicleStatus status);

    /**
     * 根据车辆类型查询（例如 平板车/货柜/厢式等）。
     */
    List<Vehicle> findByVehicleType(String vehicleType);

    /**
     * 查找载重能力大于指定值的车辆（比如寻找能承载至少 X 吨的车辆）。
     */
    List<Vehicle> findByMaxLoadCapacityGreaterThan(Double capacity);

    /**
     * 查找当前位置在某 POI 的车辆（通过外键 current_poi_id）。
     * Spring Data 会根据属性名 currentPOI.id 自动解析为 current_poi_id。
     */
    List<Vehicle> findByCurrentPOIId(Long poiId);

    /**
     * 分页查询所有车辆（用于前端分页显示）。
     */
    Page<Vehicle> findAll(Pageable pageable);

    /**
     * 自定义 JPQL 查询示例：查找空闲 (IDLE) 且最大载重 >= requiredCapacity 的车辆，
     * 并按 maxLoadCapacity 升序排序（优先选择载重刚好满足要求的车辆）。
     *
     * 说明：在 JPQL 中使用完整类路径引用枚举值（也可以用参数传入枚举）。
     */
    @Query("SELECT v FROM Vehicle v WHERE v.currentStatus = org.example.roadsimulation.entity.Vehicle.VehicleStatus.IDLE " +
            "AND v.maxLoadCapacity >= :requiredCapacity " +
            "ORDER BY v.maxLoadCapacity ASC")
    List<Vehicle> findSuitableIdleVehicles(@Param("requiredCapacity") Double requiredCapacity);
}
