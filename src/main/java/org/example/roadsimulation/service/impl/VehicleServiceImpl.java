package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.POIService;
import org.example.roadsimulation.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
                    vehicle.setCurrentStatus(vehicleDetails.getCurrentStatus());
                    vehicle.setLength(vehicleDetails.getLength());
                    vehicle.setWidth(vehicleDetails.getWidth());
                    vehicle.setHeight(vehicleDetails.getHeight());
                    vehicle.setCurrentLoad(vehicleDetails.getCurrentLoad());
                    vehicle.setCurrentLongitude(vehicleDetails.getCurrentLongitude());
                    vehicle.setCurrentLatitude(vehicleDetails.getCurrentLatitude());
                    vehicle.setVehicleType(vehicleDetails.getVehicleType());
                    vehicle.setDriverName(vehicleDetails.getDriverName());
                    vehicle.setSuitableGoods(vehicleDetails.getSuitableGoods());

                    // 新增指标字段
                    vehicle.setLoadingWaitTime(vehicleDetails.getLoadingWaitTime());
                    vehicle.setEmptyDrivingTime(vehicleDetails.getEmptyDrivingTime());
                    vehicle.setEmptyDrivingDistance(vehicleDetails.getEmptyDrivingDistance());
                    vehicle.setTotalDrivingTime(vehicleDetails.getTotalDrivingTime());
                    vehicle.setTotalDrivingDistance(vehicleDetails.getTotalDrivingDistance());

                    return vehicleRepository.save(vehicle);
                })
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> getVehicleById(Long id) {
        return vehicleRepository.findById(id); // ✅ 返回 Optional<Vehicle>
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

    @Override
    public Vehicle assignDriverToVehicle(Long vehicleId, String driverName) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> {
                    vehicle.setDriverName(driverName);
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

        vehicle.setCurrentPOI(poi);
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
    public Vehicle updateVehicleCoordinates(Long vehicleId, BigDecimal longitude, BigDecimal latitude) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> {
                    vehicle.setCurrentLongitude(longitude);
                    vehicle.setCurrentLatitude(latitude);
                    return vehicleRepository.save(vehicle);
                })
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));
    }

    @Override
    public Vehicle calculateLoadingWaitTime(Long vehicleId, LocalDateTime loadingStartTime) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));

        if (loadingStartTime == null) {
            throw new IllegalArgumentException("装货开始时间不能为空");
        }

        long waitingTime = Duration.between(loadingStartTime, LocalDateTime.now()).getSeconds();
        if (waitingTime < 0) waitingTime = 0;

        vehicle.setLoadingWaitTime(waitingTime);
        return vehicleRepository.save(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getVehiclesWithActiveAssignments() {
        return vehicleRepository.findByAssignmentsIsNotNull();
    }

    // ==================== 新增方法：更新车辆指标 ====================
    public void updateVehicleMetrics(Vehicle vehicle){
        vehicleRepository.save(vehicle); // 直接保存即可
    }
}
