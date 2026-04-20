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
import org.example.roadsimulation.service.GetCostService;
import org.example.roadsimulation.service.POIService;
import org.example.roadsimulation.service.VehicleService;
import org.example.roadsimulation.service.impl.VehicleInitializationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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
            Vehicle vehicle = assignment.getAssignedVehicle();
            if (vehicle == null) {
                throw new RuntimeException("No vehicle assigned to assignment: " + request.getAssignmentId());
            }

            // 3. 获取到达点POI
            POI endPOI = poiRepository.findById(request.getEndPOIId())
                    .orElseThrow(() -> new RuntimeException("End POI not found: " + request.getEndPOIId()));

            // ================= 🌟 核心分流逻辑：双轨并行结算 =================
            if (assignment.getNodes() != null && !assignment.getNodes().isEmpty()) {
                // 轨道 B：如果是 VRP 多点任务，走 VRP 专属结算池
                dataInitializer.processVrpVehicleDelivery(assignment, vehicle, endPOI);
                logger.info("🚚 VRP 车辆到达处理完成: 车辆 {}", vehicle.getLicensePlate());
            } else {
                // 轨道 A：如果是传统的 FTL 单线任务，走老结算池
                Route route = assignment.getRoute();
                POI startPOI = route.getStartPOI(); // 现在 route 肯定不为 null 了
                dataInitializer.processVehicleDelivery(startPOI, vehicle, endPOI);
                logger.info("🚗 普通车辆到达处理完成: 车辆 {}", vehicle.getLicensePlate());
            }
            // ===============================================================

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

    @Autowired
    private GetCostService getCostService;

    @GetMapping("/costs")
    public Map<String, Double> getCurrentCosts() {
        Map<String, Double> costs = new HashMap<>();
        costs.put("costA", getCostService.getCostByAllWaitingTimeAndMileageWithoutGoods());
        costs.put("costB", getCostService.getCostByAllEffectiveTimeAndEffectiveMileageWithWorst());
        costs.put("costC", getCostService.getCostByAllEffectiveTransportCapacityWithWorst());
        costs.put("costD", getCostService.getCostByALlOilAndFixedConsumptionWithWorst());
        return costs;
    }

}
