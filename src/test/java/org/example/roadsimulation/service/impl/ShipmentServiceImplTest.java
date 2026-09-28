package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.repository.EnrollmentRepository;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.GaodeMapService;
import org.example.roadsimulation.service.TransportLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceImplTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private GaodeMapService gaodeMapService;
    @Mock
    private POIRepository poiRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private GoodsRepository goodsRepository;
    @Mock
    private DataInitializer dataInitializer;
    @Mock
    private TransportLifecycleService transportLifecycleService;

    private ShipmentServiceImpl shipmentService;

    @BeforeEach
    void setUp() {
        shipmentService = new ShipmentServiceImpl(
                shipmentRepository,
                vehicleRepository,
                gaodeMapService,
                poiRepository,
                enrollmentRepository,
                goodsRepository,
                dataInitializer,
                transportLifecycleService
        );
    }

    @Test
    void updateStatusCancelledUsesLifecycleCancellation() {
        Shipment shipment = new Shipment();
        shipment.setId(1L);
        shipment.setStatus(Shipment.ShipmentStatus.PLANNED);
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));

        Shipment result = shipmentService.updateStatus(1L, Shipment.ShipmentStatus.CANCELLED);

        assertEquals(shipment, result);
        verify(transportLifecycleService).cancelShipment(
                eq(shipment),
                eq("Shipment status update"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq("ShipmentService")
        );
        verify(shipmentRepository, never()).save(shipment);
    }

    @Test
    void updateStatusNonCancelledKeepsDirectShipmentUpdateBehavior() {
        Shipment shipment = new Shipment();
        shipment.setId(2L);
        shipment.setStatus(Shipment.ShipmentStatus.CREATED);
        when(shipmentRepository.findById(2L)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(shipment)).thenReturn(shipment);

        Shipment result = shipmentService.updateStatus(2L, Shipment.ShipmentStatus.PLANNED);

        assertEquals(Shipment.ShipmentStatus.PLANNED, result.getStatus());
        verify(shipmentRepository).save(shipment);
        verify(transportLifecycleService, never()).cancelShipment(
                eq(shipment),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
