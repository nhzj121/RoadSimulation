package org.example.roadsimulation.service;

import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.dto.ProductionPlanResponse;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProductionPlan;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.sandbox.random.SandboxRandomDomain;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creates and releases one idempotent production plan for every active chain.
 */
@Service
public class ProductionDemandGenerator {

    static final int MIN_FINAL_QUANTITY = 10;
    static final int MAX_FINAL_QUANTITY = 34;
    private static final Logger log = LoggerFactory.getLogger(ProductionDemandGenerator.class);

    private final ProcessingChainRepository chainRepository;
    private final ProductionPlanRepository planRepository;
    private final ProductionPlanningService planningService;
    private final ProductionDemandCatalogService catalogService;
    private final SimulationRuntimeConfig runtimeConfig;
    private SandboxRunRuntimeContext sandboxRunRuntimeContext;

    public ProductionDemandGenerator(
            ProcessingChainRepository chainRepository,
            ProductionPlanRepository planRepository,
            ProductionPlanningService planningService,
            ProductionDemandCatalogService catalogService,
            SimulationRuntimeConfig runtimeConfig
    ) {
        this.chainRepository = chainRepository;
        this.planRepository = planRepository;
        this.planningService = planningService;
        this.catalogService = catalogService;
        this.runtimeConfig = runtimeConfig;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSandboxRunRuntimeContext(SandboxRunRuntimeContext sandboxRunRuntimeContext) {
        this.sandboxRunRuntimeContext = sandboxRunRuntimeContext;
    }

    public void generate(String simulationRunId, SimulationTick tick) {
        if (simulationRunId == null || simulationRunId.isBlank()) {
            throw new IllegalArgumentException("simulationRunId is required");
        }
        if (tick == null) {
            throw new IllegalArgumentException("simulation tick is required");
        }

        List<ProcessingChain> activeChains = chainRepository
                .findByStatus(ProcessingChain.ChainStatus.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(ProcessingChain::getId))
                .toList();
        List<String> failures = new ArrayList<>();
        for (ProcessingChain chain : activeChains) {
            try {
                generateForChain(simulationRunId, tick.loopIndex(), chain);
            } catch (RuntimeException failure) {
                failures.add(chain.getChainCode() + ": " + failure.getMessage());
                log.error(
                        "Automatic production demand failed: runId={}, round={}, chain={}",
                        simulationRunId, tick.loopIndex(), chain.getChainCode(), failure);
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("部分加工链自动需求生成失败: " + String.join("; ", failures));
        }
    }

    private void generateForChain(String runId, int round, ProcessingChain chain) {
        ProductionPlan existing = planRepository
                .findBySimulationRunIdAndGenerationRoundAndChainId(runId, round, chain.getId())
                .orElse(null);
        if (existing != null) {
            releaseIfCalculated(existing);
            return;
        }

        Goods finalGoods = catalogService.resolveFinalTransportedGoods(chain.getId());
        long planSeed;
        int quantity;
        if (sandboxRunRuntimeContext != null) {
            Map<String, Object> key = Map.of(
                    "loopIndex", round,
                    "processingChainId", chain.getId());
            planSeed = sandboxRunRuntimeContext.deriveSeed(
                    SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY, key);
            quantity = sandboxRunRuntimeContext.random(
                    SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY, key).nextInt(
                    MAX_FINAL_QUANTITY - MIN_FINAL_QUANTITY + 1) + MIN_FINAL_QUANTITY;
        } else {
            planSeed = derivedSeed(runtimeConfig.getDemandRandomSeed(), round, chain.getId());
            quantity = new Random(planSeed).nextInt(
                    MAX_FINAL_QUANTITY - MIN_FINAL_QUANTITY + 1) + MIN_FINAL_QUANTITY;
        }
        double finalWeight = roundWeight(quantity * finalGoods.getWeightPerUnit());
        CreateProductionPlanRequest request = new CreateProductionPlanRequest(
                chain.getId(),
                finalWeight,
                finalWeight,
                null,
                planSeed,
                null,
                "production-demand-generator"
        );

        try {
            ProductionPlanResponse plan = planningService.createAutomaticPlan(request, runId, round);
            planningService.releasePlan(plan.id(), "production-demand-generator");
        } catch (DataIntegrityViolationException duplicate) {
            ProductionPlan concurrent = planRepository
                    .findBySimulationRunIdAndGenerationRoundAndChainId(
                            runId, round, chain.getId())
                    .orElse(null);
            if (concurrent == null) {
                throw duplicate;
            }
            releaseIfCalculated(concurrent);
        }
    }

    private void releaseIfCalculated(ProductionPlan plan) {
        if (plan.getStatus() == ProductionPlan.PlanStatus.CALCULATED) {
            planningService.releasePlan(plan.getId(), "production-demand-generator");
        } else if (plan.getStatus() != ProductionPlan.PlanStatus.RELEASED) {
            throw new IllegalStateException(
                    "自动计划处于不可发布状态: planId=" + plan.getId()
                            + ", status=" + plan.getStatus());
        }
    }

    private long derivedSeed(Long baseSeed, int round, Long chainId) {
        if (baseSeed == null) {
            return ThreadLocalRandom.current().nextLong();
        }
        long value = baseSeed;
        value ^= 0x9E3779B97F4A7C15L * (round + 1L);
        value ^= 0xC2B2AE3D27D4EB4FL * (chainId + 1L);
        return value;
    }

    private double roundWeight(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
