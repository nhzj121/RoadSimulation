package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository // 标记为Repository，Spring会自动管理它（可省略，因为JpaRepository本身已被Spring识别）
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    // 基本命名模式： 查询主题(如findBy/readBy/queryBy) + 属性名 + 条件(如GreaterThan/Like/NotNull)
    // 1. 根据车牌号查找车辆（假设车牌号唯一）
    Vehicle findByLicensePlate(String licensePlate);

    // 2. 根据车辆状态查找
    List<Vehicle> findByCurrentStatus(Vehicle.VehicleStatus status);

    // 3. 根据车辆类型查找
    List<Vehicle> findByVehicleType(String vehicleType);

    // 4. 查找载重能力大于指定值的车辆
    List<Vehicle> findByMaxLoadCapacityGreaterThan(Double capacity);

    // 5. 查找载重能力在某个范围内的车辆
    List<Vehicle> findByMaxLoadCapacityBetween(Double minCapacity, Double maxCapacity);

    // 6. 根据品牌查找车辆
    List<Vehicle> findByBrand(String brand);

    // 7. 查找当前位置在某POI的车辆
    List<Vehicle> findByCurrentPOIId(Long poiId);

    // 8. 根据车牌号模糊查询
    List<Vehicle> findByLicensePlateContaining(String partialLicensePlate);

    // 9. 查找空闲状态的车辆，并按载重能力降序排列
    List<Vehicle> findByCurrentStatusOrderByMaxLoadCapacityDesc(Vehicle.VehicleStatus status);

    /*
    * @Query("JPQL或SQL查询语句")
    * 返回类型 方法名(参数列表);
    * */
    /*目的：找到载重能力大于等于指定值且处于特定状态的车辆
    解析：
    SELECT v FROM Vehicle v：从 Vehicle 实体中选择
    WHERE v.maxLoadCapacity >= :minCapacity：载重能力条件
    AND v.currentStatus = :status：状态条件
    两个条件通过 AND 连接
    * */
    @Query("SELECT v FROM Vehicle v WHERE v.maxLoadCapacity >= :minCapacity AND v.currentStatus = :status")
    List<Vehicle> findAvailableVehiclesWithMinCapacity(
            @Param("minCapacity") Double minCapacity,
            @Param("status") Vehicle.VehicleStatus status);

    /*目的：统计处于特定状态的车辆数量
    解析：
    SELECT COUNT(v)：计算符合条件的记录数
    FROM Vehicle v：从 Vehicle 实体中统计
    WHERE v.currentStatus = :status：按状态筛选
    * */
    @Query("SELECT COUNT(v) FROM Vehicle v WHERE v.currentStatus = :status")
    Long countByStatus(@Param("status") Vehicle.VehicleStatus status);

    /*目的：找到最适合运输指定重量货物的空闲车辆
    解析：
    v.currentStatus = org.example.roadsimulation.entity.Vehicle.VehicleStatus.IDLE：只选择空闲状态的车辆
    v.maxLoadCapacity >= :requiredCapacity：载重能力满足要求
    ORDER BY v.maxLoadCapacity ASC：按载重能力升序排列，这样返回的第一个车辆就是刚好能满足要求的最小载重车辆（最经济的选择）
    注意：枚举值需要完整路径（除非使用静态导入）
    * */
    @Query("SELECT v FROM Vehicle v WHERE v.currentStatus = org.example.roadsimulation.entity.Vehicle.VehicleStatus.IDLE " +
            "AND v.maxLoadCapacity >= :requiredCapacity " +
            "ORDER BY v.maxLoadCapacity ASC")
    List<Vehicle> findSuitableIdleVehicles(@Param("requiredCapacity") Double requiredCapacity);

}