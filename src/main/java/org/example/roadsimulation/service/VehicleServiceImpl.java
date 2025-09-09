package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class VehicleServiceImpl implements VehicleService{
    private final VehicleRepository vehicleRepository;
    @Autowired
    public VehicleServiceImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }
    // 查询所有的已有车辆
    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getAllVehicles(){
        return vehicleRepository.findAll();
    }
    // 查找某个车辆
    @Override
    @Transactional(readOnly = true)
    public Vehicle getVehicleById(Long id){
        return vehicleRepository.findById(id).orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + id));
    }
    // 新建一个车辆
    @Override
    public Vehicle createVehicle(Vehicle vehicle){
        // 检查车牌号是否已经存在
        if(vehicleRepository.findByLicensePlate(vehicle.getLicensePlate()) != null){
            throw new RuntimeException("车牌号已经存在：" + vehicle.getLicensePlate());
        }
        // 初始化
        if(vehicle.getCurrentStatus() == null){
            vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
        }

        return vehicleRepository.save(vehicle);
    }

    @Override
    public Vehicle updateVehicle(Long id, Vehicle vehicleDetails) {
        Vehicle vehicle = getVehicleById(id);

        // 更新字段（避免更新敏感字段如id）
        if (vehicleDetails.getLicensePlate() != null) {
            // 检查新车牌号是否与其他车辆冲突
            Vehicle existingVehicle = vehicleRepository.findByLicensePlate(vehicleDetails.getLicensePlate());
            if (existingVehicle != null && !existingVehicle.getId().equals(id)) {
                throw new RuntimeException("车牌号已被其他车辆使用: " + vehicleDetails.getLicensePlate());
            }
            vehicle.setLicensePlate(vehicleDetails.getLicensePlate());
        }

        if (vehicleDetails.getBrand() != null) {
            vehicle.setBrand(vehicleDetails.getBrand());
        }

        if (vehicleDetails.getMaxLoadCapacity() != null) {
            vehicle.setMaxLoadCapacity(vehicleDetails.getMaxLoadCapacity());
        }

        // 更新其他字段...
        // ToDo

        return vehicleRepository.save(vehicle);
    }

    @Override
    public void deleteVehicle(Long id) {
        Vehicle vehicle = getVehicleById(id);

        // 业务规则：只有在空闲状态的车辆才能被删除
        if (vehicle.getCurrentStatus() != Vehicle.VehicleStatus.IDLE) {
            throw new RuntimeException("只能删除空闲状态的车辆");
        }

        vehicleRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> getVehiclesByStatus(Vehicle.VehicleStatus status) {
        return vehicleRepository.findByCurrentStatus(status);
    }

    @Override
    public Vehicle updateVehicleStatus(Long vehicleId, Vehicle.VehicleStatus newStatus) {
//        Vehicle vehicle = getVehicleById(vehicleId);
//
//        // 这里可以添加状态转换的验证逻辑
//        // validateStatusTransition(vehicle.getCurrentStatus(), newStatus);
//        // ToDo
//
//        vehicle.setCurrentStatus(newStatus);
//        return vehicleRepository.save(vehicle);
        return null;
    }

    @Override
    public Vehicle assignToPOI(Long vehicleId, Long poiId) {
//        Vehicle vehicle = getVehicleById(vehicleId);
//
//        // 业务规则：只有在空闲或完成状态的车辆才能被分配
//        if (vehicle.getCurrentStatus() != Vehicle.VehicleStatus.IDLE &&
//                vehicle.getCurrentStatus() != Vehicle.VehicleStatus.RESTING) {
//            throw new RuntimeException("车辆当前状态不适合分配任务");
//        }
//
//        // 这里需要POI服务来获取POI实体，暂时用null代替
//        // POI poi = poiService.getPOIById(poiId);
//        // vehicle.setCurrentPOI(poi);
//        // ToDo
//
//        vehicle.setCurrentStatus(Vehicle.VehicleStatus.TRANSPORTING);
//        return vehicleRepository.save(vehicle);
        return null;
    }

    @Override
    public List<Vehicle> findAvailableVehiclesWithMinCapacity(Double minCapacity){
        return null;
    }

}
