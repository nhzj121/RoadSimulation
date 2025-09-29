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
 * 功能说明：
 * 1. 实现 VehicleService 接口
 * 2. 提供增删改查、分页、模糊搜索、状态查询、唯一性校验
 * 3. 双向关系维护：
 *    - Driver-Vehicle
 *    - Vehicle-POI
 */
@Service
@Transactional
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Autowired
    public VehicleServiceImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public Vehicle createVehicle(Vehicle vehicle) {
        if (vehicleRepository.existsByLicensePlate(vehicle.getLicensePlate())) {
            throw new IllegalArgumentException("车牌号已存在: " + vehicle.getLicensePlate());
        }
        return vehicleRepository.save(vehicle);
    }

    @Override
    public Vehicle updateVehicle(Long id, Vehicle vehicleDetails) {
        return vehicleRepository.findById(id)
                .map(vehicle -> {
                    if (!vehicle.getLicensePlate().equals(vehicleDetails.getLicensePlate()) &&
                            vehicleRepository.existsByLicensePlate(vehicleDetails.getLicensePlate())) {
                        throw new IllegalArgumentException("车牌号已存在: " + vehicleDetails.getLicensePlate());
                    }
                    vehicle.setLicensePlate(vehicleDetails.getLicensePlate());
                    vehicle.setBrand(vehicleDetails.getBrand());
                    vehicle.setModelType(vehicleDetails.getModelType());
                    vehicle.setMaxLoadCapacity(vehicleDetails.getMaxLoadCapacity());
                    vehicle.setCargoVolume(vehicleDetails.getCargoVolume());
                    vehicle.setCurrentStatus(vehicleDetails.getCurrentStatus());
                    vehicle.setLength(vehicleDetails.getLength());
                    vehicle.setWidth(vehicleDetails.getWidth());
                    vehicle.setHeight(vehicleDetails.getHeight());
                    vehicle.setCurrentLoad(vehicleDetails.getCurrentLoad());
                    vehicle.setCurrentLongitude(vehicleDetails.getCurrentLongitude());
                    vehicle.setCurrentLatitude(vehicleDetails.getCurrentLatitude());
                    return vehicleRepository.save(vehicle);
                })
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> getVehicleById(Long id) {
        return vehicleRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Vehicle> getAllVehicles(Pageable pageable) {
        return vehicleRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> searchVehiclesByLicense(String partialLicense) {
        return vehicleRepository.findByLicensePlateContainingIgnoreCase(partialLicense);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getVehiclesByStatus(Vehicle.VehicleStatus status) {
        return vehicleRepository.findByCurrentStatus(status);
    }

    @Override
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + id));
        vehicleRepository.delete(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByLicensePlate(String licensePlate) {
        return vehicleRepository.existsByLicensePlate(licensePlate);
    }
}
