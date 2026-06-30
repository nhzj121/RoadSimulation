package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.cost.CostNormalizationConfig;
import org.example.roadsimulation.optimizer.multi.ga.MultiOrderGA;
import org.example.roadsimulation.optimizer.multi.ga.MultiOrderGAConfig;
import org.example.roadsimulation.optimizer.multi.ga.MutationConfig;
import org.example.roadsimulation.optimizer.multi.init.InitialPopulationConfig;
import org.example.roadsimulation.optimizer.multi.persist.MultiOrderAssignmentMaterializer;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.SimulationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HeuristicSimulationDispatchService implements SimulationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(HeuristicSimulationDispatchService.class);

    private final ShipmentItemRepository shipmentItemRepository;
    private final VehicleRepository vehicleRepository;
    private final MultiOrderGA multiOrderGA;
    private final MultiOrderAssignmentMaterializer assignmentMaterializer;
    private final DataInitializer dataInitializer;

    public HeuristicSimulationDispatchService(
            ShipmentItemRepository shipmentItemRepository,
            VehicleRepository vehicleRepository,
            MultiOrderGA multiOrderGA,
            MultiOrderAssignmentMaterializer assignmentMaterializer,
            DataInitializer dataInitializer
    ) {
        this.shipmentItemRepository = shipmentItemRepository;
        this.vehicleRepository = vehicleRepository;
        this.multiOrderGA = multiOrderGA;
        this.assignmentMaterializer = assignmentMaterializer;
        this.dataInitializer = dataInitializer;
    }

    @Override
    @Transactional
    public void dispatch() {
        long dispatchStart = System.currentTimeMillis();

        List<ShipmentItem> pendingItems = shipmentItemRepository.findByStatus(
                ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED
        );
        List<Vehicle> idleVehicles = vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE);

        log.info(
                "[Dispatch][HEURISTIC] Start. pendingItems={}, idleVehicles={}",
                pendingItems.size(),
                idleVehicles.size()
        );

        if (pendingItems.isEmpty()) {
            log.info("[Dispatch][HEURISTIC] No pending shipment items.");
            dataInitializer.dispatchOverdueTailItems("TAIL_FALLBACK_HEURISTIC");
            return;
        }

        if (idleVehicles.isEmpty()) {
            log.info("[Dispatch][HEURISTIC] No idle vehicles.");
            dataInitializer.dispatchOverdueTailItems("TAIL_FALLBACK_HEURISTIC");
            return;
        }

        long seed = System.currentTimeMillis();
        long optimizeStart = System.currentTimeMillis();
        MultiOrderSolution solution = multiOrderGA.optimize(
                pendingItems,
                idleVehicles,
                new MultiOrderGAConfig(),
                new InitialPopulationConfig(),
                new CostNormalizationConfig(),
                new MutationConfig(),
                seed
        );
        long optimizeElapsed = System.currentTimeMillis() - optimizeStart;

        log.info(
                "[Dispatch][HEURISTIC] GA finished. elapsedMs={}, feasible={}, cost={}, unassignedItems={}",
                optimizeElapsed,
                solution.isFeasible(),
                solution.getCost(),
                solution.getUnassignedShipmentItemIds() == null ? 0 : solution.getUnassignedShipmentItemIds().size()
        );

        long materializeStart = System.currentTimeMillis();
        List<Assignment> createdAssignments = assignmentMaterializer
                .materialize(solution, pendingItems, idleVehicles);
        long materializeElapsed = System.currentTimeMillis() - materializeStart;

        long frontendRegisterStart = System.currentTimeMillis();
        for (Assignment assignment : createdAssignments) {
            dataInitializer.registerAssignmentForFrontend(assignment);
        }
        long frontendRegisterElapsed = System.currentTimeMillis() - frontendRegisterStart;

        int createdCount = createdAssignments.size();

        int unassignedCount = solution.getUnassignedShipmentItemIds() == null
                ? 0
                : solution.getUnassignedShipmentItemIds().size();

        log.info(
                "[Dispatch][HEURISTIC] Done. createdAssignments={}, unassignedItems={}, optimizeMs={}, materializeMs={}, frontendRegisterMs={}, totalMs={}",
                createdCount,
                unassignedCount,
                optimizeElapsed,
                materializeElapsed,
                frontendRegisterElapsed,
                System.currentTimeMillis() - dispatchStart
        );
        dataInitializer.dispatchOverdueTailItems("TAIL_FALLBACK_HEURISTIC");
    }
}
