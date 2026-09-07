package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.Shipment;

public interface TransportDemandService {
    Shipment createInboundTransport(
            ProcessingStageExecution execution,
            POI sourcePOI,
            String actor
    );

    Shipment createOutboundTransport(
            ProcessingStageExecution currentExecution,
            ProcessingStageExecution nextExecution,
            String actor
    );
}
