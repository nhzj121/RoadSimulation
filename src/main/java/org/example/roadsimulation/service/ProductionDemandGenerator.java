package org.example.roadsimulation.service;

import org.example.roadsimulation.service.production.ProductionRules;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creates and releases the configured production batches for every active chain.
 */
@Service
public class ProductionDemandGenerator {

    static final int MIN_FINAL_QUANTITY = ProductionRules.MIN_FINAL_QUANTITY;
    static final int MAX_FINAL_QUANTITY = ProductionRules.MAX_FINAL_QUANTITY;
    private static final String GENERATOR_ACTOR = "production-demand-generator";
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
            var demand = sandboxRunRuntimeContext != null && sandboxRunRuntimeContext.isV2()
                    ? sandboxRunRuntimeContext.revisionV2().specification().demand().forChain(chain.getId()) : null;
            int batches = demand == null ? 1 : demand.batchesPerGeneration().intValueExact();
            BigDecimal multiplier = demand == null ? BigDecimal.ONE : demand.quantityMultiplier();
            for (int batch = 0; batch < batches; batch++) {
                try {
                    generateForChain(simulationRunId, tick.loopIndex(), chain, batch, multiplier);
                } catch (RuntimeException failure) {
                    failures.add(chain.getChainCode() + " batch " + batch + ": " + failure.getMessage());
                    log.error("Automatic production demand failed: runId={}, round={}, chain={}, batch={}",
                            simulationRunId, tick.loopIndex(), chain.getChainCode(), batch, failure);
                }
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("部分加工链自动需求生成失败: " + String.join("; ", failures));
        }
    }

    private void generateForChain(String runId, int round, ProcessingChain chain, int batch, BigDecimal multiplier) {
        ProductionPlan existing = findBatch(runId, round, chain.getId(), batch).orElse(null);
        if (existing != null) {
            releaseIfCalculated(existing);
            return;
        }

        Goods finalGoods = catalogService.resolveFinalTransportedGoods(chain.getId());
        long planSeed;
        double finalWeight;
        if (sandboxRunRuntimeContext != null) {
            var key = ProductionRules.batchRandomKey(round, chain.getId(), batch);
            planSeed = sandboxRunRuntimeContext.deriveSeed(
                    SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY, key);
            finalWeight = ProductionRules.automaticDemand(
                    sandboxRunRuntimeContext.random(SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY, key)::nextInt,
                    multiplier, finalGoods.getWeightPerUnit()).weight();
        } else {
            planSeed = derivedSeed(runtimeConfig.getDemandRandomSeed(), round, chain.getId());
            finalWeight = roundWeight(ProductionRules.finalQuantity(new Random(planSeed)::nextInt) * finalGoods.getWeightPerUnit());
        }
        CreateProductionPlanRequest request = new CreateProductionPlanRequest(
                chain.getId(),
                finalWeight,
                finalWeight,
                null,
                planSeed,
                null,
                GENERATOR_ACTOR
        );

        ProductionPlanResponse plan;
        try {
            plan = batch == 0 ? planningService.createAutomaticPlan(request, runId, round)
                    : planningService.createAutomaticPlan(request, runId, round, batch);
        } catch (DataIntegrityViolationException duplicate) {
            releaseIfCalculated(findBatch(runId, round, chain.getId(), batch).orElseThrow(() -> duplicate));
            return;
        }
        releaseAutomaticPlan(plan.id());
    }

    private Optional<ProductionPlan> findBatch(String runId, int round, Long chainId, int batch) {
        return batch == 0 ? planRepository.findBySimulationRunIdAndGenerationRoundAndChainId(runId, round, chainId)
                : planRepository.findBySimulationRunIdAndGenerationRoundAndChainIdAndGenerationBatchIndex(runId, round, chainId, batch);
    }

    private void releaseIfCalculated(ProductionPlan plan) {
        if (plan.getStatus() == ProductionPlan.PlanStatus.CALCULATED) {
            releaseAutomaticPlan(plan.getId());
        } else if (plan.getStatus() != ProductionPlan.PlanStatus.RELEASED) {
            throw new IllegalStateException(
                    "自动计划处于不可发布状态: planId=" + plan.getId()
                            + ", status=" + plan.getStatus());
        }
    }

    private void releaseAutomaticPlan(Long planId) {
        try {
            planningService.releasePlan(planId, GENERATOR_ACTOR);
        } catch (IllegalStateException failure) {
            // The conditional status update may have lost to another generator.
            boolean released = planRepository.findById(planId)
                    .filter(plan -> plan.getStatus() == ProductionPlan.PlanStatus.RELEASED).isPresent();
            if (!released) throw failure;
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
        return ProductionRules.round(value);
    }
}
