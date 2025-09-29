package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Driver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * DriverService
 *
 * 说明：
 * - 定义 Driver 相关业务接口
 * - 包括增删改查、分页、模糊查询、状态过滤
 */
public interface DriverService {

    // 创建司机
    Driver createDriver(Driver driver);

    // 更新司机信息
    Driver updateDriver(Long id, Driver driverDetails);

    // 查询所有司机
    List<Driver> getAllDrivers();

    // 分页查询
    Page<Driver> getAllDrivers(Pageable pageable);

    // 根据ID查询
    Optional<Driver> getDriverById(Long id);

    // 根据手机号查询
    Optional<Driver> getDriverByPhone(String phone);

    // 根据姓名模糊查询
    List<Driver> searchDriversByName(String name);

    // 根据状态查询
    List<Driver> getDriversByStatus(Driver.DriverStatus status);

    // 删除司机
    void deleteDriver(Long id);

    // 检查手机号是否存在
    boolean existsByPhone(String phone);
}
