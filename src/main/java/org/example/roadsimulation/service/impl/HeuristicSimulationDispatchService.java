package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.optimizer.multi.MultiOrderSolution;
import org.example.roadsimulation.optimizer.multi.ga.MultiOrderGA;
import org.example.roadsimulation.optimizer.multi.persist.MultiOrderAssignmentMaterializer;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.sandbox.random.SandboxRandomDomain;
import org.example.roadsimulation.sandbox.run.SandboxAlgorithmRuntimeConfiguration;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.example.roadsimulation.service.DriverBehaviorService;
import org.example.roadsimulation.service.SimulationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class HeuristicSimulationDispatchService implements SimulationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(HeuristicSimulationDispatchService.class);

    private final ShipmentItemRepository shipmentItemRepository;
    private final VehicleRepository vehicleRepository;
    private final MultiOrderGA multiOrderGA;
    private final MultiOrderAssignmentMaterializer assignmentMaterializer;
    private final DataInitializer dataInitializer;
    private SandboxRunRuntimeContext sandboxRunRuntimeContext;
    private SimulationContext simulationContext;
    private final DriverBehaviorService driverBehaviorService;

    public HeuristicSimulationDispatchService(
            ShipmentItemRepository shipmentItemRepository,
            VehicleRepository vehicleRepository,
            MultiOrderGA multiOrderGA,
            MultiOrderAssignmentMaterializer assignmentMaterializer,
            DataInitializer dataInitializer
    ) {
        this(shipmentItemRepository, vehicleRepository, multiOrderGA, assignmentMaterializer,
                dataInitializer, null);
    }

    @Autowired
    public HeuristicSimulationDispatchService(
            ShipmentItemRepository shipmentItemRepository,
            VehicleRepository vehicleRepository,
            MultiOrderGA multiOrderGA,
            MultiOrderAssignmentMaterializer assignmentMaterializer,
            DataInitializer dataInitializer,
            DriverBehaviorService driverBehaviorService
    ) {
        this.shipmentItemRepository = shipmentItemRepository;
        this.vehicleRepository = vehicleRepository;
        this.multiOrderGA = multiOrderGA;
        this.assignmentMaterializer = assignmentMaterializer;
        this.dataInitializer = dataInitializer;
        this.driverBehaviorService = driverBehaviorService;
    }

    @Autowired(required = false)
    public void setSandboxRunRuntimeContext(SandboxRunRuntimeContext sandboxRunRuntimeContext) {
        this.sandboxRunRuntimeContext = sandboxRunRuntimeContext;
    }

    @Autowired
    public void setSimulationContext(SimulationContext simulationContext) {
        this.simulationContext = simulationContext;
    }

    @Override
    @Transactional
    public void dispatch() {
        long dispatchStart = System.currentTimeMillis();

        List<ShipmentItem> pendingItems = new java.util.ArrayList<>(shipmentItemRepository.findByStatus(
                ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED
        ));
        List<Vehicle> queriedIdleVehicles = vehicleRepository.findByCurrentStatus(Vehicle.VehicleStatus.IDLE);
        List<Vehicle> idleVehicles = new java.util.ArrayList<>(driverBehaviorService == null
                ? queriedIdleVehicles
                : driverBehaviorService.filterMaintenanceVehicles(queriedIdleVehicles));
        pendingItems.sort(Comparator.comparing(ShipmentItem::getId,
                Comparator.nullsLast(Long::compareTo)));
        idleVehicles.sort(Comparator.comparing(Vehicle::getId,
                Comparator.nullsLast(Long::compareTo)));

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

        long optimizeStart = System.currentTimeMillis();
        MultiOrderSolution solution;
        if (sandboxRunRuntimeContext != null) {
            int loopIndex = simulationContext == null ? 0 : simulationContext.getLoopCount();
            Map<String, Object> decisionKey = Map.of(
                    "loopIndex", loopIndex,
                    "dispatchOrdinal", 0);
            SandboxAlgorithmRuntimeConfiguration.HeuristicConfiguration configuration =
                    SandboxAlgorithmRuntimeConfiguration.heuristic(
                            sandboxRunRuntimeContext.algorithmProfile());
            java.time.LocalDateTime decisionTime = simulationContext == null
                    ? sandboxRunRuntimeContext.specification().simulationClock().startLocalDateTime()
                    : simulationContext.getCurrentSimTime();
            configuration.costNormalization().setEvaluationTime(decisionTime);
            configuration.mutation().setEvaluationTime(decisionTime);
            solution = multiOrderGA.optimize(
                    pendingItems,
                    idleVehicles,
                    configuration.ga(),
                    configuration.initialPopulation(),
                    configuration.costNormalization(),
                    configuration.mutation(),
                    sandboxRunRuntimeContext.javaRandom(
                            SandboxRandomDomain.HEURISTIC_INITIAL_POPULATION, decisionKey),
                    sandboxRunRuntimeContext.javaRandom(
                            SandboxRandomDomain.HEURISTIC_EVOLUTION, decisionKey));
        } else {
            solution = multiOrderGA.optimize(
                    pendingItems,
                    idleVehicles,
                    new org.example.roadsimulation.optimizer.multi.ga.MultiOrderGAConfig(),
                    new org.example.roadsimulation.optimizer.multi.init.InitialPopulationConfig(),
                    new org.example.roadsimulation.optimizer.multi.cost.CostNormalizationConfig(),
                    new org.example.roadsimulation.optimizer.multi.ga.MutationConfig(),
                    System.currentTimeMillis());
        }
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
