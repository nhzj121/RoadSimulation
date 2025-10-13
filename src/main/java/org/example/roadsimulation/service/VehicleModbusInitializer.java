package org.example.roadsimulation.service;

import lombok.extern.slf4j.Slf4j;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Component
public class VehicleModbusInitializer {

    @Autowired
    private VehicleRepository vehicleRepository;

    /**
     * 初始化车辆Modbus配置
     */
    @PostConstruct
    public void initModbusConfig() {
        try {
            // 获取所有车辆
            List<Vehicle> vehicles = vehicleRepository.findAll();
            int slaveId = 1;
            int configuredCount = 0;

            for (Vehicle vehicle : vehicles) {
                // 只为未配置的车辆分配Modbus Slave ID
                if (vehicle.getModbusSlaveId() == null) {
                    vehicle.setModbusSlaveId(slaveId++);
                    vehicle.setIsOnline(true);

                    // 如果车辆没有初始位置，设置默认位置（北京）
                    if (vehicle.getCurrentLongitude() == null || vehicle.getCurrentLatitude() == null) {
                        vehicle.setCurrentLongitude(116.4074); // 北京经度
                        vehicle.setCurrentLatitude(39.9042);   // 北京纬度
                    }

                    vehicleRepository.save(vehicle);
                    configuredCount++;

                    log.info("为车辆 {} 分配Modbus Slave ID: {}",
                            vehicle.getLicensePlate(), vehicle.getModbusSlaveId());
                }
            }

            log.info("Modbus配置初始化完成，共配置 {} 辆车", configuredCount);

            // 生成Modbus Slave配置指南
            generateModbusConfigGuide();

        } catch (Exception e) {
            log.error("初始化Modbus配置失败: {}", e.getMessage());
        }
    }

    /**
     * 生成Modbus Slave配置指南
     */
    private void generateModbusConfigGuide() {
        List<Vehicle> vehicles = vehicleRepository.findByModbusSlaveIdIsNotNull();

        log.info("=== Modbus Slave 配置指南 ===");
        log.info("请按以下配置在Modbus Slave软件中设置：");
        log.info("1. 连接类型: TCP");
        log.info("2. 服务器地址: 127.0.0.1");
        log.info("3. 端口: 502");
        log.info("4. 寄存器配置:");

        for (Vehicle vehicle : vehicles) {
            log.info("  从站 {} (车辆: {})",
                    vehicle.getModbusSlaveId(), vehicle.getLicensePlate());
            log.info("    地址 0: 车辆ID - 设置为: {}", vehicle.getId());
            log.info("    地址 1-2: 经度 (32位浮点数)");
            log.info("    地址 3-4: 纬度 (32位浮点数)");
            log.info("    地址 5: 状态 (0-6)");
            log.info("    地址 6: 速度 (0-120)");
            log.info("    地址 7: 油量 (0-100)");
        }
        log.info("============================");
    }

    /**
     * 重置车辆的Modbus配置
     */
    public void resetModbusConfig(Long vehicleId) {
        vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
            vehicle.setModbusSlaveId(null);
            vehicle.setIsOnline(false);
            vehicleRepository.save(vehicle);
            log.info("已重置车辆 {} 的Modbus配置", vehicle.getLicensePlate());
        });
    }
}