package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.Driver.DriverStatus;
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
 * 注意：
 * - 使用 @Service 表示这是一个 Spring 管理的服务 Bean。
 * - 通过构造器注入 DriverRepository（比字段注入更利于测试）。
 * - save 和 delete 操作使用 @Transactional 保证事务一致性（必要时可设置回滚策略）。
 */
@Service
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;

    @Autowired
    public DriverServiceImpl(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    /**
     * 新增或更新司机
     * - 直接调用 repository.save()，Spring Data JPA 会根据 id 是否为 null 来判断 insert 或 update
     */
    @Override
    @Transactional
    public Driver save(Driver driver) {
        // 1) 简单校验：司机姓名不能为空（也可使用验证框架在 Controller 层校验）
        if (driver == null) {
            throw new IllegalArgumentException("driver must not be null");
        }
        // 2) 保存（新增或更新）
        return driverRepository.save(driver);
    }

    /**
     * 根据 ID 查询
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Driver> findById(Long id) {
        if (id == null) return Optional.empty();
        return driverRepository.findById(id);
    }

    /**
     * 查询所有司机（不分页）
     */
    @Override
    @Transactional(readOnly = true)
    public List<Driver> findAll() {
        return driverRepository.findAll();
    }

    /**
     * 分页查询（推荐用于列表展示）
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Driver> findAll(Pageable pageable) {
        return driverRepository.findAll(pageable);
    }

    /**
     * 根据手机号查找（可能返回 null）
     */
    @Override
    @Transactional(readOnly = true)
    public Driver findByPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return driverRepository.findByDriverPhone(phone);
    }

    /**
     * 根据姓名模糊查询（忽略大小写）
     */
    @Override
    @Transactional(readOnly = true)
    public List<Driver> searchByName(String partialName) {
        if (partialName == null) return List.of();
        return driverRepository.findByDriverNameContainingIgnoreCase(partialName);
    }

    /**
     * 根据状态查询
     */
    @Override
    @Transactional(readOnly = true)
    public List<Driver> findByStatus(DriverStatus status) {
        if (status == null) return List.of();
        return driverRepository.findByCurrentStatus(status);
    }

    /**
     * 删除司机（id 不存在时此操作为幂等）
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        if (id == null) return;
        // 可选：在删除之前可以做检查或业务校验，例如是否有未完成的任务等
        driverRepository.deleteById(id);
    }

    /**
     * 判断司机是否存在
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        if (id == null) return false;
        return driverRepository.existsById(id);
    }
}
