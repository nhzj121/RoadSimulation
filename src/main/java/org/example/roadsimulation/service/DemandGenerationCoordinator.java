package org.example.roadsimulation.service;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.config.DemandGenerationMode;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.core.SimulationContext;
import org.springframework.stereotype.Service;

/** 统一需求生成入口，主循环不再直接选择需求源。 */
@Service
public class DemandGenerationCoordinator {

    static final int LEGACY_INTERVAL_LOOPS = 2;
    static final int PRODUCTION_INTERVAL_LOOPS = 6;

    private final DataInitializer dataInitializer;
    private final ProductionDemandGenerator productionDemandGenerator;
    private final SimulationRuntimeConfig runtimeConfig;
    private final SimulationContext simulationContext;

    public DemandGenerationCoordinator(
            DataInitializer dataInitializer,
            ProductionDemandGenerator productionDemandGenerator,
            SimulationRuntimeConfig runtimeConfig,
            SimulationContext simulationContext
    ) {
        this.dataInitializer = dataInitializer;
        this.productionDemandGenerator = productionDemandGenerator;
        this.runtimeConfig = runtimeConfig;
        this.simulationContext = simulationContext;
    }

    public void generateForTick(SimulationTick tick) {
        if (tick == null) {
            throw new IllegalArgumentException("simulation tick is required");
        }
        DemandGenerationMode mode = runtimeConfig.getDemandGenerationMode();
        if ((mode == DemandGenerationMode.LEGACY || mode == DemandGenerationMode.MIXED)
                && tick.loopIndex() % LEGACY_INTERVAL_LOOPS == 0) {
            dataInitializer.generateGoods(tick.loopIndex());
        }
        if ((mode == DemandGenerationMode.PRODUCTION || mode == DemandGenerationMode.MIXED)
                && tick.loopIndex() % PRODUCTION_INTERVAL_LOOPS == 0) {
            String runId = simulationContext.getSimulationRunId()
                    .orElseThrow(() -> new IllegalStateException(
                            "production demand generation requires an active simulation run id"));
            productionDemandGenerator.generate(runId, tick);
        }
    }
}
