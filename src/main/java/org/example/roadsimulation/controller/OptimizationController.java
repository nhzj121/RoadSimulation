package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.ApiResponse;
import org.example.roadsimulation.dto.OptimizationResultDTO;
import org.example.roadsimulation.service.VehicleCargoOptimizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 车辆-货物全局匹配优化接口
 *
 * GET /api/optimization/run?algorithm=BOTH&seed=-1
 *   返回：ApiResponse<List<OptimizationResultDTO>>
 *   algorithm: "GA" | "SA" | "BOTH"（默认BOTH）
 *   seed: 随机种子，-1表示随机（默认-1）
 *
 * GET /api/optimization/best?seed=-1
 *   返回：ApiResponse<OptimizationResultDTO>（自动选两种算法中更优的）
 *
 * GET /api/optimization/compare?seed=-1
 *   返回：两种算法的摘要对比（轻量接口，适合前端卡片展示）
 */
@RestController
@RequestMapping("/api/optimization")
public class OptimizationController {

    @Autowired
    private VehicleCargoOptimizationService optimizationService;

    /**
     * 运行优化，返回完整分配方案
     */
    @GetMapping("/run")
    public ApiResponse<List<OptimizationResultDTO>> run(
            @RequestParam(defaultValue = "BOTH") String algorithm,
            @RequestParam(defaultValue = "-1")   long   seed) {

        List<OptimizationResultDTO> results = optimizationService.runOptimization(algorithm, seed);
        if (results.isEmpty()) {
            return ApiResponse.error("无可用车辆或待分配货物明细，优化未执行");
        }
        return ApiResponse.success(results);
    }

    /**
     * 返回两种算法中更优的单一方案
     */
    @GetMapping("/best")
    public ApiResponse<OptimizationResultDTO> best(
            @RequestParam(defaultValue = "-1") long seed) {

        OptimizationResultDTO best = optimizationService.getBestResult(seed);
        if (best == null) {
            return ApiResponse.error("无可用数据，优化未执行");
        }
        return ApiResponse.success(best);
    }

    /**
     * 轻量对比摘要（前端展示对比卡片用）
     */
    @GetMapping("/compare")
    public ApiResponse<Map<String, Object>> compare(
            @RequestParam(defaultValue = "-1") long seed) {

        List<OptimizationResultDTO> results = optimizationService.runOptimization("BOTH", seed);
        if (results.size() < 2) {
            return ApiResponse.error("数据不足，无法对比");
        }

        OptimizationResultDTO ga = results.get(0);
        OptimizationResultDTO sa = results.get(1);
        String winner = (ga.isFeasible() && !sa.isFeasible()) ? "GA"
                : (!ga.isFeasible() && sa.isFeasible()) ? "SA"
                : ga.getTotalCost() <= sa.getTotalCost() ? "GA" : "SA";

        Map<String, Object> body = Map.of(
                "GA", Map.of(
                        "cost",         ga.getTotalCost(),
                        "feasible",     ga.isFeasible(),
                        "vehiclesUsed", ga.getVehiclesUsed(),
                        "itemsAssigned",ga.getItemsAssigned(),
                        "elapsedMs",    ga.getElapsedMs()
                ),
                "SA", Map.of(
                        "cost",         sa.getTotalCost(),
                        "feasible",     sa.isFeasible(),
                        "vehiclesUsed", sa.getVehiclesUsed(),
                        "itemsAssigned",sa.getItemsAssigned(),
                        "elapsedMs",    sa.getElapsedMs()
                ),
                "winner", winner
        );

        return ApiResponse.success(body);
    }
}
