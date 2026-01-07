package org.example.roadsimulation.controller;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {
    @Autowired
    private SimulationMainLoop simulationMainLoop;

    @Autowired
    private DataInitializer dataInitializer;

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
    public ResponseEntity<?> vehicleArrivedAtDestination(@RequestBody VehicleArrivalRequest request) {
        try {
            dataInitializer.vehicleArrivedAtDestination(request.getVehicleId(), request.getEndPOIId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    // 请求体类
    public static class VehicleArrivalRequest {
        private Long vehicleId;
        private Long endPOIId;

        // getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

        public Long getEndPOIId() { return endPOIId; }
        public void setEndPOIId(Long endPOIId) { this.endPOIId = endPOIId; }
    }

}
