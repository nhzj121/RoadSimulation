package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.service.SimulationDispatchService;
import org.springframework.stereotype.Service;

@Service
public class OriginalSimulationDispatchService implements SimulationDispatchService {

    private final DataInitializer dataInitializer;

    public OriginalSimulationDispatchService(DataInitializer dataInitializer) {
        this.dataInitializer = dataInitializer;
    }

    @Override
    public void dispatch() {
        dataInitializer.vrpDispatchingCycle();
    }
}
