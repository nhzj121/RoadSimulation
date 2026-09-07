package org.example.roadsimulation.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Emitted after transport delivery has been committed to shipment state.
 */
public record ShipmentDeliveredEvent(
        List<Long> shipmentIds,
        LocalDateTime deliveredAt
) {
    public ShipmentDeliveredEvent {
        shipmentIds = shipmentIds == null ? List.of() : List.copyOf(shipmentIds);
    }
}
