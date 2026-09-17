package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.ProductionBatchResponse;
import org.example.roadsimulation.event.ShipmentDeliveredEvent;

import java.time.LocalDateTime;

public interface ProductionExecutionService {
    void onShipmentDelivered(ShipmentDeliveredEvent event);

    void updateProgress(LocalDateTime simNow, int minutesPerLoop);

    ProductionBatchResponse getBatch(Long batchId);
}
