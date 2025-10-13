package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DataSource dataSource;

    @GetMapping("/database-status")
    public Map<String, Object> getDatabaseStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // 检查数据库连接
            try (Connection conn = dataSource.getConnection()) {
                status.put("databaseConnection", "SUCCESS");

                // 检查vehicle表结构
                ResultSet rs = conn.getMetaData().getColumns(null, null, "vehicle", "modbus_slave_id");
                status.put("hasModbusSlaveIdColumn", rs.next());

                rs = conn.getMetaData().getColumns(null, null, "vehicle", "is_online");
                status.put("hasIsOnlineColumn", rs.next());
            }

            // 检查车辆数据
            List<Vehicle> vehicles = vehicleRepository.findAll();
            status.put("totalVehicles", vehicles.size());

            long configuredVehicles = vehicles.stream()
                    .filter(v -> v.getModbusSlaveId() != null)
                    .count();
            status.put("configuredModbusVehicles", configuredVehicles);

            long onlineVehicles = vehicles.stream()
                    .filter(v -> Boolean.TRUE.equals(v.getIsOnline()))
                    .count();
            status.put("onlineVehicles", onlineVehicles);

            // 检查是否有重复的modbus_slave_id
            boolean hasDuplicateSlaveIds = vehicles.stream()
                    .filter(v -> v.getModbusSlaveId() != null)
                    .map(Vehicle::getModbusSlaveId)
                    .distinct()
                    .count() != configuredVehicles;
            status.put("hasDuplicateSlaveIds", hasDuplicateSlaveIds);

            status.put("status", "SUCCESS");

        } catch (Exception e) {
            status.put("status", "ERROR");
            status.put("error", e.getMessage());
        }

        return status;
    }

    @GetMapping("/vehicle-details")
    public List<Vehicle> getVehicleDetails() {
        return vehicleRepository.findAll();
    }

    @GetMapping("/fix-modbus-config")
    public String fixModbusConfig() {
        try {
            List<Vehicle> vehicles = vehicleRepository.findAll();
            int slaveId = 1;
            int fixedCount = 0;

            for (Vehicle vehicle : vehicles) {
                // 跳过已经配置的车辆
                if (vehicle.getModbusSlaveId() != null) {
                    continue;
                }

                // 分配新的slave id
                vehicle.setModbusSlaveId(slaveId++);

                // 确保在线状态
                if (vehicle.getIsOnline() == null) {
                    vehicle.setIsOnline(true);
                }

                // 确保有位置信息
                if (vehicle.getCurrentLongitude() == null) {
                    vehicle.setCurrentLongitude(116.4074);
                }
                if (vehicle.getCurrentLatitude() == null) {
                    vehicle.setCurrentLatitude(39.9042);
                }

                vehicleRepository.save(vehicle);
                fixedCount++;
            }

            return "成功修复 " + fixedCount + " 辆车的Modbus配置";

        } catch (Exception e) {
            return "修复失败: " + e.getMessage();
        }
    }
}