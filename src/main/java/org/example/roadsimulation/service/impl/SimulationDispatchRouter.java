package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Routes one periodic dispatch cycle to the strategy selected in the runtime configuration.
 * Ordinary batch direct matching occurs earlier during goods generation and is not routed here.
 */
@Service
public class SimulationDispatchRouter {

    private static final Logger log = LoggerFactory.getLogger(SimulationDispatchRouter.class);

    private final SimulationRuntimeConfig runtimeConfig;
    private final OriginalSimulationDispatchService originalDispatchService;
    private final HeuristicSimulationDispatchService heuristicDispatchService;

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
        DispatchStrategy strategy = runtimeConfig.getDispatchStrategy();
        log.info("[Dispatch] Current strategy: {}", strategy);

        if (DispatchStrategy.HEURISTIC.equals(strategy)) {
            heuristicDispatchService.dispatch();
            return;
        }

        originalDispatchService.dispatch();
    }
}
