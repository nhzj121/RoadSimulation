package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * VehicleServiceImpl
 *
 * 说明：
 * - 使用 @Service 将该类注册为 Spring 管理的 Service Bean。
 * - 通过构造器注入 repository（便于单元测试）。
 * - 对写操作使用 @Transactional，以保证事务一致性。
 */
@Service
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Autowired
    public VehicleServiceImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    /**
     * 保存或更新车辆（新增/更新）。
     * 校验：简单校验 null，复杂校验建议在 Controller 层或使用 @Valid 注解。
     */
    @Override
    @Transactional
    public Vehicle save(Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("vehicle must not be null");
        }
        // 这里可以加入更多业务校验，例如：车牌不为空、载重不为负等（实体已使用校验注解）
        return vehicleRepository.save(vehicle);
    }

    /**
     * 根据 ID 查找（只读事务）
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> findById(Long id) {
        if (id == null) return Optional.empty();
        return vehicleRepository.findById(id);
    }

    /**
     * 查询全部（只读）
     */
    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }

    /**
     * 分页查询（只读）
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Vehicle> findAll(Pageable pageable) {
        return vehicleRepository.findAll(pageable);
    }

    /**
     * 根据车牌查找（精确）
     */
    @Override
    @Transactional(readOnly = true)
    public Vehicle findByLicensePlate(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) return null;
        return vehicleRepository.findByLicensePlate(licensePlate);
    }

    /**
     * 车牌模糊搜索
     */
    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> searchByLicensePlate(String partialPlate) {
        if (partialPlate == null) return List.of();
        return vehicleRepository.findByLicensePlateContainingIgnoreCase(partialPlate);
    }

    /**
     * 根据状态查找
     */
    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findByStatus(Vehicle.VehicleStatus status) {
        if (status == null) return List.of();
        return vehicleRepository.findByCurrentStatus(status);
    }

    /**
     * 根据类型查找
     */
    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findByVehicleType(String vehicleType) {
        if (vehicleType == null) return List.of();
        return vehicleRepository.findByVehicleType(vehicleType);
    }

    /**
     * 查询某 POI 下的车辆
     */
    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findByCurrentPOIId(Long poiId) {
        if (poiId == null) return List.of();
        return vehicleRepository.findByCurrentPOIId(poiId);
    }

    /**
     * 查找空闲且能满足最低载重的车辆（按升序返回，列表第一个为最合适）
     */
    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findSuitableIdleVehicles(Double requiredCapacity) {
        if (requiredCapacity == null) return List.of();
        return vehicleRepository.findSuitableIdleVehicles(requiredCapacity);
    }

    /**
     * 删除车辆（幂等操作）
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        if (id == null) return;
        // 可在此添加删除前检查（例如是否存在正在进行的 Assignment）
        vehicleRepository.deleteById(id);
    }

    /**
     * 判断是否存在
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        if (id == null) return false;
        return vehicleRepository.existsById(id);
    }
}
