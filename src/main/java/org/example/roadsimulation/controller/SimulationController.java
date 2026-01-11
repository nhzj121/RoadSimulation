package org.example.roadsimulation.controller;

import lombok.Getter;
import lombok.Setter;
import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.POIService;
import org.example.roadsimulation.service.VehicleService;
import org.example.roadsimulation.service.impl.VehicleInitializationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {
    @Autowired
    private SimulationMainLoop simulationMainLoop;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private POIRepository poiRepository;

    @Autowired
    private DataInitializer dataInitializer;

    private static final Logger logger = LoggerFactory.getLogger(VehicleInitializationServiceImpl.class);

    /*
    启动仿真
     */
    @PostMapping("/start")
    public ApiResponse<String> startSimulation(){
        simulationMainLoop.start();
        return ApiResponse.success("仿真循环已开始");
    }

    /*
    暂停仿真
     */
    @PostMapping("/stop")
    public ApiResponse<String> stopSimulation(){
        simulationMainLoop.stop();
        return ApiResponse.success("仿真循环已暂停");
    }

    /*
    重置仿真循环
     */
    @PostMapping("/reset")
    public ApiResponse<String> resetSimulation(){
        simulationMainLoop.reset();
        return ApiResponse.success("仿真已重置");
    }

    /**
     * 前端通知车辆到达终点
     */
    @PostMapping("/vehicle-arrived")
    public ResponseEntity<Void> handleVehicleArrived(@RequestBody VehicleArrivedRequest request) {
        try {
            // 1. 获取Assignment
            Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                    .orElseThrow(() -> new RuntimeException("Assignment not found: " + request.getAssignmentId()));

            // 2. 获取车辆
            Vehicle vehicle = vehicleRepository.findById(request.vehicleId)
                    .orElseThrow(() -> new RuntimeException("Vehicle not found: " + request.vehicleId));

            if (vehicle == null) {
                vehicle = assignment.getAssignedVehicle();
                if(vehicle == null){
                    throw new RuntimeException("No vehicle assigned to assignment: " + request.getAssignmentId());
                }
            }

            // 3. 获取卸货点POI
            POI endPOI = poiRepository.findById(request.getEndPOIId())
                    .orElseThrow(() -> new RuntimeException("End POI not found: " + request.getEndPOIId()));

            Route route = assignment.getRoute();
            POI startPOI = route.getStartPOI();

            // 4. 更新车辆状态
            vehicle.setPreviousStatus(vehicle.getCurrentStatus());
            vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
            vehicle.setStatusStartTime(LocalDateTime.now());
            vehicle.setStatusDurationSeconds(0L);

            // 5. 更新车辆位置到卸货点（重要！）
            vehicle.setCurrentPOI(endPOI);
            vehicle.setCurrentLongitude(endPOI.getLongitude());
            vehicle.setCurrentLatitude(endPOI.getLatitude());

            // 6. 载货量清零
            vehicle.setCurrentLoad(0.0);
            vehicle.setCurrentVolumn(0.0);

            // 7. 保存车辆
            vehicleRepository.save(vehicle);

            // 8. 标记Assignment为已完成（如果尚未完成）
            if (assignment.getStatus() != Assignment.AssignmentStatus.COMPLETED) {
                assignment.setStatus(Assignment.AssignmentStatus.COMPLETED);
                assignment.setEndTime(LocalDateTime.now());
                assignmentRepository.save(assignment);
            }

            dataInitializer.shipOutGoodsWhenVehicleArrives(startPOI, endPOI, vehicle);

            logger.info("车辆到达处理完成: 车辆 {} 到达 POI {}, 状态更新为 IDLE",
                    vehicle.getLicensePlate(), endPOI.getName());

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            logger.error("车辆到达处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 请求体类
    public static class VehicleArrivedRequest {
        // getters and setters
        @Setter
        @Getter
        private Long assignmentId;
        @Setter
        @Getter
        private Long vehicleId;
        @Getter
        @Setter
        private Long endPOIId;

    }

}
