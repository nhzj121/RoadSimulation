package org.example.roadsimulation.controller;

import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {
    @Autowired
    private SimulationMainLoop simulationMainLoop;

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

}
