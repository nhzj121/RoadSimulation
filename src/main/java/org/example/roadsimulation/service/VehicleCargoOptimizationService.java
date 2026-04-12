package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.OptimizationResultDTO;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.GeneticAlgorithmOptimizer;
import org.example.roadsimulation.optimizer.MatchingSolution;
import org.example.roadsimulation.optimizer.SimulatedAnnealingOptimizer;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 车辆-货物匹配全局优化 Service
 *
 * 调用方式：
 *   GET  /api/optimization/run?algorithm=BOTH&seed=-1
 *   GET  /api/optimization/compare
 *
 * algorithm 取值：
 *   "GA"   — 仅运行遗传算法
 *   "SA"   — 仅运行模拟退火
 *   "BOTH" — 两种都跑，返回对比结果（默认）
 */
@Service
public class VehicleCargoOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(VehicleCargoOptimizationService.class);

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    @Autowired
    private GeneticAlgorithmOptimizer gaOptimizer;

    @Autowired
    private SimulatedAnnealingOptimizer saOptimizer;

    /**
     * 执行优化主入口
     *
     * @param algorithm "GA" | "SA" | "BOTH"
     * @param seed      随机种子（-1 表示使用当前时间戳）
     * @return 各算法的优化结果列表（BOTH模式返回2条）
     */
    public List<OptimizationResultDTO> runOptimization(String algorithm, long seed) {

        // ── 1. 加载数据 ──────────────────────────────────────────────
        // 使用 ShipmentItemStatus.NOT_ASSIGNED.name() 对齐 findByStatus(String) 签名
        List<ShipmentItem> items = shipmentItemRepository.findByStatus(
                ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
        List<Vehicle>      vehicles = vehicleRepository.findByCurrentStatus(
                Vehicle.VehicleStatus.IDLE);

        if (vehicles.isEmpty()) {
            log.warn("[Optimizer] 无IDLE状态车辆，无法优化");
            return List.of();
        }
        if (items.isEmpty()) {
            log.warn("[Optimizer] 无NOT_ASSIGNED状态货物明细，无法优化");
            return List.of();
        }

        log.info("[Optimizer] 数据加载完成：{}辆IDLE车辆, {}条待分配货物明细", vehicles.size(), items.size());

        long actualSeed = seed < 0 ? System.currentTimeMillis() : seed;

        // ── 2. 按算法分支执行 ─────────────────────────────────────────
        List<OptimizationResultDTO> results = new ArrayList<>();

        switch (algorithm.toUpperCase()) {
            case "GA" -> {
                long t0 = System.currentTimeMillis();
                MatchingSolution gaSol = gaOptimizer.optimize(vehicles, items, actualSeed);
                results.add(OptimizationResultDTO.from(gaSol, "GeneticAlgorithm",
                        System.currentTimeMillis() - t0, vehicles, items));
            }
            case "SA" -> {
                long t0 = System.currentTimeMillis();
                MatchingSolution saSol = saOptimizer.optimize(vehicles, items, actualSeed);
                results.add(OptimizationResultDTO.from(saSol, "SimulatedAnnealing",
                        System.currentTimeMillis() - t0, vehicles, items));
            }
            default -> { // BOTH
                long t0 = System.currentTimeMillis();
                MatchingSolution gaSol = gaOptimizer.optimize(vehicles, items, actualSeed);
                long gaMs = System.currentTimeMillis() - t0;

                t0 = System.currentTimeMillis();
                MatchingSolution saSol = saOptimizer.optimize(vehicles, items, actualSeed);
                long saMs = System.currentTimeMillis() - t0;

                results.add(OptimizationResultDTO.from(gaSol, "GeneticAlgorithm",  gaMs, vehicles, items));
                results.add(OptimizationResultDTO.from(saSol, "SimulatedAnnealing", saMs, vehicles, items));

                // 对比日志
                logComparison(results.get(0), results.get(1));
            }
        }

        return results;
    }

    /**
     * 返回更优的单一结果（供业务逻辑自动选用最优方案时调用）
     */
    public OptimizationResultDTO getBestResult(long seed) {
        List<OptimizationResultDTO> results = runOptimization("BOTH", seed);
        if (results.isEmpty()) return null;
        if (results.size() == 1) return results.get(0);
        // 先选 feasible 的；都 feasible 或都不 feasible 时选 cost 更低的
        OptimizationResultDTO ga = results.get(0);
        OptimizationResultDTO sa = results.get(1);
        if (ga.isFeasible() && !sa.isFeasible()) return ga;
        if (!ga.isFeasible() && sa.isFeasible()) return sa;
        return ga.getTotalCost() <= sa.getTotalCost() ? ga : sa;
    }

    private void logComparison(OptimizationResultDTO ga, OptimizationResultDTO sa) {
        String winner = (ga.isFeasible() && !sa.isFeasible()) ? "GA"
                : (!ga.isFeasible() && sa.isFeasible()) ? "SA"
                : ga.getTotalCost() <= sa.getTotalCost() ? "GA" : "SA";

        log.info("[Optimizer] 对比 ── GA: cost={:.0f} feasible={} 用{}辆 {}ms | " +
                        "SA: cost={:.0f} feasible={} 用{}辆 {}ms | 胜出: {}",
                ga.getTotalCost(), ga.isFeasible(), ga.getVehiclesUsed(), ga.getElapsedMs(),
                sa.getTotalCost(), sa.isFeasible(), sa.getVehiclesUsed(), sa.getElapsedMs(),
                winner);
    }
}
