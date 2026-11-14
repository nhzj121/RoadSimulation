package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimulationDispatchRouter {

    private static final Logger log = LoggerFactory.getLogger(SimulationDispatchRouter.class);

    private final SimulationRuntimeConfig runtimeConfig;
    private final OriginalSimulationDispatchService originalDispatchService;
    private final HeuristicSimulationDispatchService heuristicDispatchService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.example.roadsimulation.sandbox.optimization.PlanExecutionCoordinator planExecution;

    public SimulationDispatchRouter(
            SimulationRuntimeConfig runtimeConfig,
            OriginalSimulationDispatchService originalDispatchService,
            HeuristicSimulationDispatchService heuristicDispatchService
    ) {
        this.runtimeConfig = runtimeConfig;
        this.originalDispatchService = originalDispatchService;
        this.heuristicDispatchService = heuristicDispatchService;
    }

    public void dispatch() {
        if (planExecution != null && planExecution.active()) { planExecution.dispatch(); return; }
        DispatchStrategy strategy = runtimeConfig.getDispatchStrategy();
        if (strategy == DispatchStrategy.GLOBAL_OPTIMIZATION)
            throw new IllegalStateException("Global optimization requires an installed plan");
        log.info("[Dispatch] Current strategy: {}", strategy);

        if (DispatchStrategy.HEURISTIC.equals(strategy)) {
            heuristicDispatchService.dispatch();
            return;
        }

        originalDispatchService.dispatch();
    }
}
