package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.service.SimulationDispatchService;
import org.springframework.stereotype.Service;

/** Executes the ORIGINAL packing cycle followed by its shared overdue-tail fallback. */
@Service
public class OriginalSimulationDispatchService implements SimulationDispatchService {

    private static final String TAIL_FALLBACK_SOURCE = "TAIL_FALLBACK_ORIGINAL";

    private final DataInitializer dataInitializer;

    public OriginalSimulationDispatchService(DataInitializer dataInitializer) {
        this.dataInitializer = dataInitializer;
    }

    @Override
    public void dispatch() {
        dataInitializer.vrpDispatchingCycle();
        dataInitializer.dispatchOverdueTailItems(TAIL_FALLBACK_SOURCE);
    }
}
