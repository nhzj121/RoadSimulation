package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.Shipment;

public interface TransportDemandService {
    Shipment createTransport(
            ProcessingExecutionFlow flow,
            POI sourcePOI,
            String actor
    );
}
