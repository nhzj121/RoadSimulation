package org.example.roadsimulation.controller;

import jakarta.validation.Valid;
import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.service.VehicleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * VehicleController - 车辆管理REST API控制器
 */
@Validated
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private static final Logger logger = LoggerFactory.getLogger(VehicleController.class);

    private final VehicleService vehicleService;

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    /**
     * 创建新车辆
     */
    @PostMapping
    public ResponseEntity<Vehicle> createVehicle(@Valid @RequestBody Vehicle vehicle) {
        logger.info("接收到创建车辆请求，车牌号: {}", vehicle.getLicensePlate());

        try {
            Vehicle createdVehicle = vehicleService.createVehicle(vehicle);

            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(createdVehicle.getId())
                    .toUri();

            logger.info("车辆创建成功，ID: {}, 车牌号: {}", createdVehicle.getId(), createdVehicle.getLicensePlate());
            return ResponseEntity.created(location).body(createdVehicle);
        } catch (IllegalArgumentException e) {
            logger.error("车辆创建失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 根据ID查询车辆详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> getVehicleById(@PathVariable Long id) {
        logger.debug("查询车辆详情，ID: {}", id);

        Optional<Vehicle> vehicle = vehicleService.getVehicleById(id);
        if (vehicle.isPresent()) {
            return ResponseEntity.ok(vehicle.get());
        } else {
            logger.warn("车辆不存在，ID: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取所有车辆列表
     */
    @GetMapping
    public ResponseEntity<List<Vehicle>> getAllVehicles() {
        logger.debug("查询所有车辆列表");

        List<Vehicle> vehicles = vehicleService.getAllVehicles();
        logger.debug("查询到 {} 辆车辆", vehicles.size());
        return ResponseEntity.ok(vehicles);
    }

    /**
     * 分页查询车辆列表
     */
    @GetMapping("/page")
    public ResponseEntity<Page<Vehicle>> getAllVehiclesPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        logger.debug("分页查询车辆，页码: {}, 每页大小: {}, 排序字段: {}, 排序方向: {}",
                page, size, sortBy, direction);

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<Vehicle> vehiclePage = vehicleService.getAllVehicles(pageable);
        logger.debug("分页查询完成，总记录数: {}, 总页数: {}",
                vehiclePage.getTotalElements(), vehiclePage.getTotalPages());

        return ResponseEntity.ok(vehiclePage);
    }

    /**
     * 根据车牌号模糊搜索车辆
     */
    @GetMapping("/search")
    public ResponseEntity<List<Vehicle>> searchVehicles(@RequestParam String license) {
        logger.debug("根据车牌号搜索车辆，关键字: {}", license);

        List<Vehicle> vehicles = vehicleService.searchVehiclesByLicense(license);
        logger.debug("搜索完成，找到 {} 辆匹配的车辆", vehicles.size());
        return ResponseEntity.ok(vehicles);
    }

    /**
     * 根据车辆状态查询车辆列表
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Vehicle>> getVehiclesByStatus(@PathVariable Vehicle.VehicleStatus status) {
        logger.debug("根据状态查询车辆，状态: {}", status);

        List<Vehicle> vehicles = vehicleService.getVehiclesByStatus(status);
        logger.debug("状态查询完成，找到 {} 辆状态为 {} 的车辆", vehicles.size(), status);
        return ResponseEntity.ok(vehicles);
    }

    /**
     * 全量更新车辆信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<Vehicle> updateVehicle(
            @PathVariable Long id,
            @Valid @RequestBody Vehicle vehicleDetails) {

        logger.info("全量更新车辆信息，ID: {}", id);

        try {
            Vehicle updatedVehicle = vehicleService.updateVehicle(id, vehicleDetails);
            logger.info("车辆更新成功，ID: {}", id);
            return ResponseEntity.ok(updatedVehicle);
        } catch (IllegalArgumentException e) {
            logger.error("车辆更新失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 删除指定车辆
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        logger.info("删除车辆，ID: {}", id);

        try {
            vehicleService.deleteVehicle(id);
            logger.info("车辆删除成功，ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            logger.error("车辆删除失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 分配司机给车辆
     */
    @PatchMapping("/{vehicleId}/assign-driver")
    public ResponseEntity<Vehicle> assignDriver(
            @PathVariable Long vehicleId,
            @RequestParam String driverName) {

        logger.info("分配司机给车辆，车辆ID: {}, 司机姓名: {}", vehicleId, driverName);

        try {
            Vehicle vehicle = vehicleService.assignDriverToVehicle(vehicleId, driverName);
            logger.info("司机分配成功，车辆ID: {}, 司机姓名: {}", vehicleId, driverName);
            return ResponseEntity.ok(vehicle);
        } catch (RuntimeException e) {
            logger.error("司机分配失败，车辆ID: {}, 司机姓名: {}, 错误: {}", vehicleId, driverName, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 设置车辆当前位置（POI）
     */
    @PatchMapping("/{vehicleId}/set-location/{poiId}")
    public ResponseEntity<Vehicle> setVehicleLocation(
            @PathVariable Long vehicleId,
            @PathVariable Long poiId) {

        logger.info("设置车辆位置，车辆ID: {}, POI ID: {}", vehicleId, poiId);

        try {
            Vehicle vehicle = vehicleService.setVehicleLocation(vehicleId, poiId);
            logger.info("车辆位置设置成功，车辆ID: {}, POI ID: {}", vehicleId, poiId);
            return ResponseEntity.ok(vehicle);
        } catch (IllegalArgumentException e) {
            logger.error("车辆位置设置失败，车辆ID: {}, POI ID: {}, 错误: {}", vehicleId, poiId, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 校验车牌号是否存在
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> existsByLicensePlate(@RequestParam String licensePlate) {
        logger.debug("校验车牌号是否存在: {}", licensePlate);
        boolean exists = vehicleService.existsByLicensePlate(licensePlate);
        logger.debug("车牌号 {} 存在性检查结果: {}", licensePlate, exists);
        return ResponseEntity.ok(exists);
    }

    @GetMapping("/current-positions")
    public ResponseEntity<Map<Long, double[]>> getVehicleCurrentPositions() {
        Map<Long, double[]> positions = dataInitializer.getVehicleCurrentPositions();
        return ResponseEntity.ok(positions);
    }

    @GetMapping("/positions")
    public ResponseEntity<Map<String, double[]>> getVehiclePositions() {
        try {
            Map<String, double[]> positions = new HashMap<>();

            // 获取所有有任务的车辆
            List<Vehicle> vehiclesWithAssignments = vehicleService.getVehiclesWithActiveAssignments();

            for (Vehicle vehicle : vehiclesWithAssignments) {
                if (vehicle.getCurrentLongitude() != null && vehicle.getCurrentLatitude() != null) {
                    double[] position = new double[] {
                            vehicle.getCurrentLongitude().doubleValue(),
                            vehicle.getCurrentLatitude().doubleValue()
                    };
                    positions.put(vehicle.getId().toString(), position);
                }
            }

            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            logger.error("获取车辆位置失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}