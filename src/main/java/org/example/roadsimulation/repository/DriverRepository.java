package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Driver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DriverRepository
 *
 * 说明：
 * - 继承 JpaRepository<Driver, Long> 可以直接使用 CRUD 方法
 * - 自动解析方法名生成查询语句
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Driver d where d.id = :id")
    java.util.Optional<Driver> findByIdForUpdate(@Param("id") Long id);

    // 根据全名查找
    List<Driver> findByDriverName(String driverName);

    // 根据姓名模糊查找（忽略大小写）
    List<Driver> findByDriverNameContainingIgnoreCase(String partialName);

    // 根据手机号查询（假定唯一）
    Driver findByDriverPhone(String driverPhone);

    // 根据司机状态查找
    List<Driver> findByCurrentStatus(Driver.DriverStatus status);

    // 分页查询所有司机
    Page<Driver> findAll(Pageable pageable);

    // 检查手机号是否存在
    boolean existsByDriverPhone(String driverPhone);

    // 查询某车辆绑定的司机（通过 driver_vehicle 中间表）
    @Query("select d from Driver d join d.vehicles v where v.id = :vehicleId")
    List<Driver> findDriversByVehicleId(@Param("vehicleId") Long vehicleId);

    // 查询处于指定状态集合中的司机（概率行为状态表使用）
    List<Driver> findByCurrentStatusIn(java.util.Collection<Driver.DriverStatus> statuses);
}
