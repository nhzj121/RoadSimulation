package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.service.VehicleInitializationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicle-initialization")
public class VehicleInitializationController {

    @Autowired
    private VehicleInitializationService vehicleInitializationService;

    /**
     * 初始化所有车辆状态
     */
    @PostMapping("/initialize-all")
    public ResponseEntity<Map<String, Object>> initializeAllVehicles() {
        try {
            vehicleInitializationService.initializeAllVehicleStatus();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "所有车辆初始化完成",
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "初始化失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 重置单个车辆到指定POI
     */
    @PostMapping("/reset/{vehicleId}")
    public ResponseEntity<Map<String, Object>> resetVehicle(
            @PathVariable Long vehicleId,
            @RequestParam Long poiId) {
        try {
            Vehicle vehicle = vehicleInitializationService.resetVehicleToPOI(vehicleId, poiId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "车辆重置成功",
                    "vehicleId", vehicleId,
                    "poiId", poiId,
                    "vehicle", vehicle
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "重置失败: " + e.getMessage(),
                    "vehicleId", vehicleId
            ));
        }
    }

    /**
     * 批量重置车辆
     */
    @PostMapping("/batch-reset")
    public ResponseEntity<Map<String, Object>> batchResetVehicles(
            @RequestParam List<Long> vehicleIds,
            @RequestParam Long poiId) {
        try {
            int successCount = vehicleInitializationService.batchResetVehiclesToPOI(vehicleIds, poiId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "批量重置完成",
                    "successCount", successCount,
                    "totalCount", vehicleIds.size(),
                    "poiId", poiId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "批量重置失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 清空车辆载重
     */
    @PostMapping("/clear-load/{vehicleId}")
    public ResponseEntity<Map<String, Object>> clearVehicleLoad(
            @PathVariable Long vehicleId) {
        try {
            Vehicle vehicle = vehicleInitializationService.clearVehicleLoad(vehicleId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "载重清空成功",
                    "vehicleId", vehicleId,
                    "currentLoad", vehicle.getCurrentLoad()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "清空载重失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 设置车辆为空闲状态
     */
    @PostMapping("/set-idle/{vehicleId}")
    public ResponseEntity<Map<String, Object>> setVehicleToIdle(
            @PathVariable Long vehicleId) {
        try {
            Vehicle vehicle = vehicleInitializationService.setVehicleToIdle(vehicleId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "车辆已设置为空闲状态",
                    "vehicleId", vehicleId,
                    "status", vehicle.getCurrentStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "设置空闲状态失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取初始化统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getInitializationStats() {
        try {
            Map<String, Object> stats = vehicleInitializationService.getInitializationStats();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "获取统计信息成功",
                    "data", stats
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取统计信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取可用的默认POI
     */
    @GetMapping("/available-pois")
    public ResponseEntity<Map<String, Object>> getAvailablePOIs() {
        try {
            List<Long> poiIds = vehicleInitializationService.getAvailableDefaultPOIs();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "获取可用POI成功",
                    "poiIds", poiIds,
                    "count", poiIds.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取可用POI失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 设置默认POI
     */
    @PostMapping("/set-default-poi")
    public ResponseEntity<Map<String, Object>> setDefaultPOI(
            @RequestParam Long poiId) {
        try {
            vehicleInitializationService.setDefaultPOI(poiId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "设置默认POI成功",
                    "poiId", poiId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "设置默认POI失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 检查车辆是否可以初始化
     */
    @GetMapping("/can-initialize/{vehicleId}")
    public ResponseEntity<Map<String, Object>> canInitializeVehicle(
            @PathVariable Long vehicleId) {
        try {
            boolean canInitialize = vehicleInitializationService.canInitializeVehicle(vehicleId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "检查完成",
                    "vehicleId", vehicleId,
                    "canInitialize", canInitialize
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "检查失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取车辆状态信息
     */
    @GetMapping("/status/{vehicleId}")
    public ResponseEntity<Map<String, Object>> getVehicleStatus(
            @PathVariable Long vehicleId) {
        try {
            Map<String, Object> statusInfo = vehicleInitializationService.getVehicleStatusInfo(vehicleId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "获取车辆状态成功",
                    "vehicleId", vehicleId,
                    "data", statusInfo
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "获取车辆状态失败: " + e.getMessage()
            ));
        }
    }
}