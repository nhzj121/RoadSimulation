package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.repository.DriverRepository;
import org.example.roadsimulation.service.DriverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * DriverServiceImpl
 *
 * 说明：
 * - 实现 DriverService 接口
 * - 提供增删查改、分页、模糊查询、状态筛选、唯一性检查功能
 * - 遵循事务管理，读取操作设置 readOnly=true
 */
@Service
@Transactional
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;

    @Autowired
    public DriverServiceImpl(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    /**
     * 创建新的司机
     * @param driver 待创建司机
     * @return 创建后的司机实体（带ID）
     */
    @Override
    public Driver createDriver(Driver driver) {
        // 检查手机号唯一性
        if (driverRepository.existsByDriverPhone(driver.getDriverPhone())) {
            throw new IllegalArgumentException("手机号已存在: " + driver.getDriverPhone());
        }
        return driverRepository.save(driver);
    }

    /**
     * 更新司机信息
     */
    @Override
    public Driver updateDriver(Long id, Driver driverDetails) {
        return driverRepository.findById(id)
                .map(driver -> {
                    // 检查手机号是否冲突
                    if (!driver.getDriverPhone().equals(driverDetails.getDriverPhone()) &&
                            driverRepository.existsByDriverPhone(driverDetails.getDriverPhone())) {
                        throw new IllegalArgumentException("手机号已存在: " + driverDetails.getDriverPhone());
                    }

                    driver.setDriverName(driverDetails.getDriverName());
                    driver.setDriverPhone(driverDetails.getDriverPhone());
                    driver.setCurrentStatus(driverDetails.getCurrentStatus());
                    return driverRepository.save(driver);
                })
                .orElseThrow(() -> new RuntimeException("司机不存在，ID: " + id));
    }

    /**
     * 获取所有司机列表（不分页）
     */
    @Override
    @Transactional(readOnly = true)
    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    /**
     * 分页获取司机列表
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Driver> getAllDrivers(Pageable pageable) {
        return driverRepository.findAll(pageable);
    }

    /**
     * 根据ID查询司机
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Driver> getDriverById(Long id) {
        return driverRepository.findById(id);
    }

    /**
     * 根据姓名模糊查询司机
     */
    @Override
    @Transactional(readOnly = true)
    public List<Driver> searchDriversByName(String name) {
        return driverRepository.findByDriverNameContainingIgnoreCase(name);
    }

    /**
     * 根据手机号查询司机
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Driver> getDriverByPhone(String phone) {
        return Optional.ofNullable(driverRepository.findByDriverPhone(phone));
    }

    /**
     * 根据状态查询司机
     */
    @Override
    @Transactional(readOnly = true)
    public List<Driver> getDriversByStatus(Driver.DriverStatus status) {
        return driverRepository.findByCurrentStatus(status);
    }

    /**
     * 删除司机
     * 删除前检查是否有关联的任务（Assignments）
     */
    @Override
    public void deleteDriver(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("司机不存在，ID: " + id));

        if (!driver.getAssignments().isEmpty()) {
            throw new IllegalStateException("无法删除司机，存在关联任务");
        }

        driverRepository.delete(driver);
    }

    /**
     * 检查手机号是否存在
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByPhone(String phone) {
        return driverRepository.existsByDriverPhone(phone);
    }
}
