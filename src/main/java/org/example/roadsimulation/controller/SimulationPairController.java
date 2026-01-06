// SimulationPairController.java
package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.POIPairDTO;
import org.example.roadsimulation.service.SimulationPairService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulation/pairs")
public class SimulationPairController {

    @Autowired
    private SimulationPairService simulationPairService;

    /**
     * 获取当前活跃的POI配对
     */
    @GetMapping("/current")
    public List<POIPairDTO> getCurrentPairs() {
        return simulationPairService.getCurrentPOIPairs();
    }

    /**
     * 获取新增的POI配对（尚未被前端绘制过的）
     */
    @GetMapping("/new")
    public List<POIPairDTO> getNewPairs() {
        return simulationPairService.getNewPOIPairs();
    }

    /**
     * 标记配对为已绘制
     */
    @PostMapping("/mark-drawn/{pairId}")
    public ResponseEntity<Void> markPairAsDrawn(@PathVariable String pairId) {
        simulationPairService.markPairAsDrawn(pairId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取需要清理的配对（已完成运输的）
     */
    @GetMapping("/to-cleanup")
    public List<String> getPairsToCleanup() {
        return simulationPairService.getCompletedPairIds();
    }
}