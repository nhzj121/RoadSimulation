package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.VehicleService;
import org.example.roadsimulation.service.POIService;
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
    private final POIService poiService;

    @Autowired
    public VehicleServiceImpl(VehicleRepository vehicleRepository, POIService poiService) {
        this.vehicleRepository = vehicleRepository;
        this.poiService = poiService;
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
                    vehicle.setVehicleType(vehicleDetails.getVehicleType()); // 更新车辆类型
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

    // ================ 新增的方法实现 ================

    @Override
    public Vehicle assignDriverToVehicle(Long vehicleId, String driverName) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> {
                    // 这里只是简单设置司机姓名，如果需要完整的司机实体关联可以扩展
                    // vehicle.setDriverName(driverName);
                    return vehicleRepository.save(vehicle);
                })
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));
    }

    @Override
    public Vehicle setVehicleLocation(Long vehicleId, Long poiId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));

        POI poi = poiService.getById(poiId)
                .orElseThrow(() -> new RuntimeException("POI 不存在，ID: " + poiId));

        // 使用 Vehicle 实体的方法来维护双向关系
        vehicle.setCurrentPOI(poi);

        // 同时更新车辆的坐标到POI的坐标
        vehicle.setCurrentLongitude(poi.getLongitude());
        vehicle.setCurrentLatitude(poi.getLatitude());

        return vehicleRepository.save(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getVehiclesByType(String vehicleType) {
        return vehicleRepository.findByVehicleType(vehicleType);
    }

    @Override
    public Vehicle updateVehicleStatus(Long vehicleId, Vehicle.VehicleStatus status) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> {
                    vehicle.setCurrentStatus(status);
                    return vehicleRepository.save(vehicle);
                })
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));
    }

    @Override
    public Vehicle updateVehicleCoordinates(Long vehicleId, Double longitude, Double latitude) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> {
                    vehicle.setCurrentLongitude(longitude);
                    vehicle.setCurrentLatitude(latitude);
                    return vehicleRepository.save(vehicle);
                })
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));
    }
}