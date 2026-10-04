package org.example.roadsimulation.service;

import jakarta.persistence.EntityManager;
import org.example.roadsimulation.RoadSimulationApplication;
import org.example.roadsimulation.SimulationDataCleanupService;
import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.config.DemandGenerationMode;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingExecutionFlow;
import org.example.roadsimulation.entity.ProcessingStage;
import org.example.roadsimulation.entity.ProcessingStageEdge;
import org.example.roadsimulation.entity.ProcessingStageExecution;
import org.example.roadsimulation.entity.ProcessingStageInput;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlan;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentDemandSource;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.evaluation.EvaluationMetricId;
import org.example.roadsimulation.evaluation.EvaluationMetricValueStatus;
import org.example.roadsimulation.evaluation.EvaluationSnapshotCalculator;
import org.example.roadsimulation.evaluation.EvaluationSnapshotService;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.DriverRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProcessingExecutionFlowRepository;
import org.example.roadsimulation.repository.ProcessingStageExecutionRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.impl.SimulationDispatchRouter;
import org.example.roadsimulation.service.impl.ProductionDeliveryProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = RoadSimulationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "sandbox.management.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:production-demand-e2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.task.scheduling.enabled=false",
                "app.vehicle.import.enabled=false",
                "app.simulation.startup-pre-generation.enabled=false"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductionDemandEndToEndIntegrationTest {

    @Autowired private POIRepository poiRepository;
    @Autowired private GoodsRepository goodsRepository;
    @Autowired private ProcessingChainRepository chainRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private ProductionPlanRepository planRepository;
    @Autowired private ProductionBatchRepository batchRepository;
    @Autowired private ProcessingStageExecutionRepository executionRepository;
    @Autowired private ProcessingExecutionFlowRepository flowRepository;
    @Autowired private ShipmentRepository shipmentRepository;
    @Autowired private ShipmentItemRepository shipmentItemRepository;
    @Autowired private ProductionDemandGenerator demandGenerator;
    @Autowired private ProductionPlanningService planningService;
    @Autowired private SimulationDataCleanupService cleanupService;
    @Autowired private ProductionExecutionService executionService;
    @Autowired private ProductionDeliveryProcessor deliveryProcessor;
    @Autowired private TransportLifecycleService transportLifecycleService;
    @Autowired private SimulationRuntimeConfig runtimeConfig;
    @Autowired private SimulationContext simulationContext;
    @Autowired private SimulationDispatchRouter dispatchRouter;
    @Autowired private SimulationMainLoop mainLoop;
    @Autowired private EvaluationSnapshotCalculator evaluationCalculator;
    @Autowired private EvaluationSnapshotService evaluationSnapshotService;
    @Autowired private EntityManager entityManager;
    @MockitoBean private RoutePlanningService routePlanningService;
    @MockitoBean private GaodeMapService gaodeMapService;

    @Test
    void generatedSourceShipmentEntersExistingDispatcher() {
        seedLinearChain();
        simulationContext.setRunning(true);
        try {
            SimulationTick tick = SimulationTick.of(6,
                    LocalDateTime.of(2026, 1, 1, 3, 0), Duration.ofMinutes(30));
            demandGenerator.generate("dispatch-run", tick);
            assertThat(assignmentRepository.count()).isZero();

            dispatchRouter.dispatch();

            assertThat(assignmentRepository.count()).isPositive();
            assertThat(shipmentItemRepository.findAll())
                    .anySatisfy(item -> assertThat(item.getStatus())
                            .isNotEqualTo(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED));
        } finally {
            simulationContext.setRunning(false);
        }
    }

    @Test
    void oneDemandCanAdvanceThroughScheduledDispatchAndTransportWithoutManualDelivery() {
        seedLinearChain();
        runtimeConfig.setDemandRandomSeed(20260922L);
        mainLoop.start();
        try {
            mainLoop.executeMainLoop();
            assertThat(planRepository.count()).isEqualTo(1);
            ProcessingChain chain = chainRepository.findAll().get(0);
            chain.setStatus(ProcessingChain.ChainStatus.INACTIVE);
            chainRepository.saveAndFlush(chain);

            for (int i = 0; i < 3; i++) {
                mainLoop.executeMainLoop();
            }
            int pausedAtLoop = simulationContext.getLoopCount();
            String runId = simulationContext.getSimulationRunId().orElseThrow();
            long plansAtPause = planRepository.count();
            mainLoop.stop();
            mainLoop.executeMainLoop();
            assertThat(simulationContext.getLoopCount()).isEqualTo(pausedAtLoop);
            mainLoop.start();
            assertThat(simulationContext.getSimulationRunId()).contains(runId);
            assertThat(planRepository.count()).isEqualTo(plansAtPause);

            for (int round = 1; round < 80 && batchRepository.findAll().stream()
                    .noneMatch(batch -> batch.getStatus() == ProductionBatch.BatchStatus.COMPLETED); round++) {
                mainLoop.executeMainLoop();
            }

            assertThat(assignmentRepository.count()).isPositive();
            assertThat(batchRepository.findAll())
                    .anySatisfy(batch -> assertThat(batch.getStatus())
                            .isEqualTo(ProductionBatch.BatchStatus.COMPLETED));
            ProductionPlan plan = planRepository.findAll().get(0);
            assertThat(batchRepository.findByPlanId(plan.getId()).get(0)
                    .getActualFinalOutputWeight()).isCloseTo(plan.getFinalDemandWeight(),
                    org.assertj.core.data.Offset.offset(1.0e-9));
            assertThat(shipmentRepository.findAll()).hasSize(2)
                    .allSatisfy(shipment -> {
                        assertThat(shipment.getDemandSource())
                                .isEqualTo(ShipmentDemandSource.PRODUCTION);
                        assertThat(shipment.getStatus())
                                .isEqualTo(Shipment.ShipmentStatus.DELIVERED);
                    });
            double transportedWeight = shipmentItemRepository.findAll().stream()
                    .mapToDouble(ShipmentItem::getWeightTonnes).sum();
            var snapshot = evaluationSnapshotService.latest().orElseThrow();
            var requiredTonnes = snapshot.metrics()
                    .get(EvaluationMetricId.CARGO_REQUIRED_TONNES.getMetricId());
            assertThat(requiredTonnes.status()).isEqualTo(EvaluationMetricValueStatus.AVAILABLE);
            assertThat(requiredTonnes.value()).isCloseTo(transportedWeight,
                    org.assertj.core.data.Offset.offset(1.0e-9));
        } finally {
            mainLoop.stop();
        }
    }

    @Test
    void automaticDemandRunsFromSourceTransportThroughIntermediateTransportToSink() {
        seedLinearChain();
        assertThat(runtimeConfig.getDemandGenerationMode()).isEqualTo(DemandGenerationMode.PRODUCTION);
        runtimeConfig.setDemandRandomSeed(20260922L);
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 0, 0);
        SimulationTick tick = SimulationTick.of(6, t0, Duration.ofMinutes(30));

        demandGenerator.generate("e2e-run", tick);
        demandGenerator.generate("e2e-run", tick);

        assertThat(planRepository.count()).isEqualTo(1);
        ProductionPlan plan = planRepository.findAll().get(0);
        assertThat(plan.getStatus()).isEqualTo(ProductionPlan.PlanStatus.RELEASED);
        assertThat(plan.getSimulationRunId()).isEqualTo("e2e-run");
        assertThat(plan.getGenerationRound()).isEqualTo(6);

        ProductionBatch batch = batchRepository.findByPlanId(plan.getId()).get(0);
        List<ProcessingExecutionFlow> flows = flowRepository.findByBatchId(batch.getId());
        assertThat(flows).hasSize(2);
        ProcessingExecutionFlow rawFlow = flows.stream()
                .filter(flow -> "RAW_E2E".equals(flow.getSku()))
                .findFirst().orElseThrow();
        ProcessingExecutionFlow finalFlow = flows.stream()
                .filter(flow -> "FINAL_E2E".equals(flow.getSku()))
                .findFirst().orElseThrow();
        assertThat(rawFlow.getShipment()).isNotNull();
        assertThat(finalFlow.getShipment()).isNull();
        assertThat(rawFlow.getPlannedWeight()).isCloseTo(
                plan.getFinalDemandWeight() / 0.5,
                org.assertj.core.data.Offset.offset(1.0e-9));

        Shipment rawShipment = shipmentRepository.findById(rawFlow.getShipment().getId()).orElseThrow();
        List<ShipmentItem> rawItems = shipmentItemRepository.findByShipmentId(rawShipment.getId());
        assertThat(rawShipment.getDemandSource()).isEqualTo(ShipmentDemandSource.PRODUCTION);
        assertThat(rawItems).hasSizeGreaterThan(1);
        assertThat(rawItems).allSatisfy(item -> {
            assertThat(item.getWeightTonnes()).isLessThanOrEqualTo(5.0);
            assertThat(item.getVolume()).isLessThanOrEqualTo(5.0);
        });
        assertCargoMetric(tick, EvaluationMetricId.CARGO_REQUIRED_TONNES,
                rawItems.stream().mapToDouble(ShipmentItem::getWeightTonnes).sum());

        ShipmentItem firstRawItem = rawItems.get(0);
        deliverItem(firstRawItem, t0.plusMinutes(30));
        assertThat(shipmentRepository.findById(rawShipment.getId()).orElseThrow().getStatus())
                .isEqualTo(Shipment.ShipmentStatus.IN_TRANSIT);
        assertThat(executionRepository.findByBatchIdAndStageOrder(batch.getId(), 2)
                .orElseThrow().getStatus())
                .isEqualTo(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);

        deliver(rawShipment, t0.plusMinutes(30));
        ProcessingStageExecution middleExecution = executionRepository
                .findByBatchIdAndStageOrder(batch.getId(), 2).orElseThrow();
        assertThat(middleExecution.getStatus())
                .isEqualTo(ProcessingStageExecution.ExecutionStatus.PROCESSING);

        executionService.updateProgress(t0.plusMinutes(61), 30);
        entityManager.clear();
        finalFlow = flowRepository.findById(finalFlow.getId()).orElseThrow();
        assertThat(finalFlow.getShipment()).isNotNull();
        assertThat(finalFlow.getStatus()).isEqualTo(ProcessingExecutionFlow.FlowStatus.WAITING_TRANSPORT);
        assertThat(shipmentRepository.count()).isEqualTo(2);

        Shipment finalShipment = shipmentRepository
                .findById(finalFlow.getShipment().getId()).orElseThrow();
        deliver(finalShipment, t0.plusMinutes(90));
        entityManager.clear();
        ProductionBatch completed = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ProductionBatch.BatchStatus.COMPLETED);
        assertThat(completed.getActualFinalOutputWeight())
                .isCloseTo(plan.getFinalDemandWeight(), org.assertj.core.data.Offset.offset(1.0e-9));
        assertThat(flowRepository.findByBatchId(batch.getId()))
                .allSatisfy(flow -> assertThat(flow.getStatus())
                        .isEqualTo(ProcessingExecutionFlow.FlowStatus.DELIVERED));
        double transportedWeight = shipmentItemRepository.findAll().stream()
                .mapToDouble(ShipmentItem::getWeightTonnes).sum();
        assertCargoMetric(tick, EvaluationMetricId.CARGO_REQUIRED_TONNES, transportedWeight);
        assertCargoMetric(tick, EvaluationMetricId.CARGO_DELIVERED_TONNES, transportedWeight);

        demandGenerator.generate("e2e-run", tick);
        assertThat(planRepository.count()).isEqualTo(1);

        ProductionPlan duplicate = new ProductionPlan();
        duplicate.setPlanNo("E2E-DUPLICATE");
        duplicate.setChain(plan.getChain());
        duplicate.setFinalSku(plan.getFinalSku());
        duplicate.setFinalDemandWeight(plan.getFinalDemandWeight());
        duplicate.setSimulationRunId("e2e-run");
        duplicate.setGenerationRound(6);
        duplicate.setStatus(ProductionPlan.PlanStatus.CALCULATED);
        assertThatThrownBy(() -> planRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(planRepository.count()).isEqualTo(1);

        demandGenerator.generate("e2e-run-2", tick);
        assertThat(planRepository.count()).isEqualTo(2);

        Long manualPlanId = planningService.createRandomPlan(new CreateProductionPlanRequest(
                plan.getChain().getId(), plan.getFinalDemandWeight(), plan.getFinalDemandWeight(),
                null, 123L, null, "e2e-test"
        )).id();
        assertThat(planRepository.count()).isEqualTo(3);

        cleanupService.cleanupAllSimulationData();
        assertThat(planRepository.count()).isEqualTo(1);
        assertThat(planRepository.findById(manualPlanId)).isPresent();
        assertThat(batchRepository.count()).isZero();
        assertThat(shipmentRepository.count()).isZero();
        assertThat(chainRepository.count()).isEqualTo(1);

        demandGenerator.generate("e2e-run", tick);
        assertThat(planRepository.count()).isEqualTo(2);
        assertThat(planRepository.findBySimulationRunIdAndGenerationRoundAndChainId(
                "e2e-run", 6, plan.getChain().getId())).isPresent();
    }

    @Test
    void yShapeWaitsForBothTransportedInputsBeforeAssemblyAndFinalDelivery() {
        seedYChain();
        runtimeConfig.setDemandRandomSeed(20260922L);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        demandGenerator.generate("y-e2e-run", SimulationTick.of(
                6, start, Duration.ofMinutes(30)));

        ProductionPlan plan = planRepository.findAll().get(0);
        ProductionBatch batch = batchRepository.findByPlanId(plan.getId()).get(0);
        List<ProcessingExecutionFlow> flows = flowRepository.findByBatchId(batch.getId());
        assertThat(flows).hasSize(5);
        ProcessingExecutionFlow ore = flowBySku(flows, "ORE_Y");
        ProcessingExecutionFlow steel = flowBySku(flows, "STEEL_Y");
        ProcessingExecutionFlow rubber = flowBySku(flows, "RUBBER_Y");
        ProcessingExecutionFlow tire = flowBySku(flows, "TIRE_Y");
        ProcessingExecutionFlow car = flowBySku(flows, "CAR_Y");
        assertThat(ore.getPlannedWeight()).isCloseTo(
                plan.getFinalDemandWeight() * 10, org.assertj.core.data.Offset.offset(0.002));
        assertThat(rubber.getPlannedWeight()).isCloseTo(
                plan.getFinalDemandWeight() * 16, org.assertj.core.data.Offset.offset(0.002));
        assertThat(ore.getShipment()).isNotNull();
        assertThat(rubber.getShipment()).isNotNull();
        assertThat(steel.getShipment()).isNull();
        assertThat(tire.getShipment()).isNull();
        assertThat(car.getShipment()).isNull();

        deliver(shipmentRepository.findById(ore.getShipment().getId()).orElseThrow(),
                start.plusMinutes(30));
        executionService.updateProgress(start.plusHours(2), 30);
        steel = flowRepository.findById(steel.getId()).orElseThrow();
        assertThat(steel.getShipment()).isNotNull();
        deliver(shipmentRepository.findById(steel.getShipment().getId()).orElseThrow(),
                start.plusHours(3));
        deliveryProcessor.processShipment(steel.getShipment().getId(), start.plusHours(3));

        ProcessingStageExecution assembly = executionRepository
                .findByBatchIdAndStageOrder(batch.getId(), 5).orElseThrow();
        assertThat(assembly.getStatus())
                .isEqualTo(ProcessingStageExecution.ExecutionStatus.WAITING_INPUT);
        assertThat(car.getShipment()).isNull();

        deliver(shipmentRepository.findById(rubber.getShipment().getId()).orElseThrow(),
                start.plusHours(4));
        executionService.updateProgress(start.plusHours(6), 30);
        tire = flowRepository.findById(tire.getId()).orElseThrow();
        assertThat(tire.getShipment()).isNotNull();
        deliver(shipmentRepository.findById(tire.getShipment().getId()).orElseThrow(),
                start.plusHours(7));
        assembly = executionRepository.findById(assembly.getId()).orElseThrow();
        assertThat(assembly.getStatus())
                .isEqualTo(ProcessingStageExecution.ExecutionStatus.PROCESSING);

        executionService.updateProgress(start.plusHours(9), 30);
        car = flowRepository.findById(car.getId()).orElseThrow();
        assertThat(car.getShipment()).isNotNull();
        deliver(shipmentRepository.findById(car.getShipment().getId()).orElseThrow(),
                start.plusHours(10));
        deliveryProcessor.processShipment(car.getShipment().getId(), start.plusHours(10));
        executionService.updateProgress(start.plusHours(11), 30);
        ProductionBatch completed = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ProductionBatch.BatchStatus.COMPLETED);
        assertThat(completed.getActualFinalOutputWeight()).isCloseTo(
                plan.getFinalDemandWeight(), org.assertj.core.data.Offset.offset(0.01));
        assertThat(flowRepository.findByBatchId(batch.getId()))
                .allSatisfy(flow -> assertThat(flow.getStatus())
                        .isEqualTo(ProcessingExecutionFlow.FlowStatus.DELIVERED));
        assertThat(shipmentRepository.findAll()).hasSize(5)
                .allSatisfy(shipment -> assertThat(shipment.getStatus())
                        .isEqualTo(Shipment.ShipmentStatus.DELIVERED));
    }

    private ProcessingExecutionFlow flowBySku(List<ProcessingExecutionFlow> flows, String sku) {
        return flows.stream().filter(flow -> sku.equals(flow.getSku())).findFirst().orElseThrow();
    }

    private void deliver(Shipment shipment, LocalDateTime deliveredAt) {
        List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(shipment.getId());
        items.stream()
                .filter(item -> item.getStatus() != ShipmentItem.ShipmentItemStatus.DELIVERED)
                .forEach(item -> deliverItem(item, deliveredAt));
        assertThat(shipmentRepository.findById(shipment.getId()).orElseThrow().getStatus())
                .isEqualTo(Shipment.ShipmentStatus.DELIVERED);
    }

    private void assertCargoMetric(SimulationTick tick, EvaluationMetricId metricId, double expected) {
        var metric = evaluationCalculator.calculate(tick, "e2e-run")
                .metrics().get(metricId.getMetricId());
        assertThat(metric).isNotNull();
        assertThat(metric.status()).isEqualTo(EvaluationMetricValueStatus.AVAILABLE);
        assertThat(metric.value()).isCloseTo(expected,
                org.assertj.core.data.Offset.offset(1.0e-9));
    }

    private void deliverItem(ShipmentItem item, LocalDateTime deliveredAt) {
        Shipment shipment = shipmentRepository.findById(item.getShipment().getId()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByLicensePlate("E2E-001");
        Assignment assignment = new Assignment(item, null);
        assignment.setStatus(Assignment.AssignmentStatus.ASSIGNED);
        assignment.setOriginPOI(shipment.getOriginPOI());
        assignment.setDestPOI(shipment.getDestPOI());
        assignment.setAssignedVehicle(vehicle);
        assignment = assignmentRepository.saveAndFlush(assignment);
        item.setAssignment(assignment);
        shipmentItemRepository.saveAndFlush(item);

        assignment = transportLifecycleService.startAssignmentExecution(
                assignment, vehicle, deliveredAt.minusMinutes(60), "e2e-test");
        transportLifecycleService.markLoadingCompleted(
                assignment, deliveredAt.minusMinutes(30), "e2e-test");
        transportLifecycleService.markTransportStarted(
                assignment, deliveredAt.minusMinutes(15), "e2e-test");
        transportLifecycleService.completeDelivery(
                assignment, vehicle, shipment.getDestPOI(), deliveredAt, "e2e-test");
    }

    private void seedLinearChain() {
        Goods raw = goodsRepository.save(goods("RAW_E2E", 1.0, 1.0));
        Goods finished = goodsRepository.save(goods("FINAL_E2E", 0.5, 0.25));
        raw.setVehicleFit("E2E_TRUCK");
        finished.setVehicleFit("E2E_TRUCK");
        goodsRepository.saveAllAndFlush(List.of(raw, finished));
        POI sourcePoi = poiRepository.save(poi("Source", 104.00, 30.00, POI.POIType.TIMBER_YARD));
        POI middlePoi = poiRepository.save(poi("Middle", 104.10, 30.10, POI.POIType.SAWMILL));
        POI sinkPoi = poiRepository.save(poi("Sink", 104.20, 30.20, POI.POIType.BOARD_FACTORY));
        poiRepository.save(poi("Warehouse", 104.05, 30.05, POI.POIType.WAREHOUSE));

        ProcessingChain chain = new ProcessingChain();
        chain.setChainCode("E2E-LINEAR");
        chain.setChainName("E2E Linear Chain");
        chain.setStatus(ProcessingChain.ChainStatus.ACTIVE);

        ProcessingStage source = stage(chain, 1, "source", "Source", sourcePoi, raw, raw, 1.0, 1);
        ProcessingStage middle = stage(chain, 2, "middle", "Middle", middlePoi, raw, finished, 0.5, 30);
        ProcessingStage sink = stage(chain, 3, "sink", "Sink", sinkPoi, finished, finished, 1.0, 1);
        ProcessingStageInput sourceInput = input(source, "generated-raw", raw);
        ProcessingStageInput middleInput = input(middle, "raw", raw);
        ProcessingStageInput sinkInput = input(sink, "finished", finished);
        source.setInputs(List.of(sourceInput));
        middle.setInputs(List.of(middleInput));
        sink.setInputs(List.of(sinkInput));
        chain.setStages(List.of(source, middle, sink));
        chain.setEdges(List.of(
                new ProcessingStageEdge(chain, source, middle, middleInput),
                new ProcessingStageEdge(chain, middle, sink, sinkInput)
        ));
        chainRepository.saveAndFlush(chain);

        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate("E2E-001");
        vehicle.setVehicleType("E2E_TRUCK");
        vehicle.setMaxLoadCapacityTonnes(5.0);
        vehicle.setCargoVolume(5.0);
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
        vehicle.setCurrentPOI(sourcePoi);
        vehicle = vehicleRepository.saveAndFlush(vehicle);
        attachIdleDriver(vehicle);
    }

    private void seedYChain() {
        Goods oreGoods = goodsRepository.save(goods("ORE_Y", 1.0, 1.0));
        Goods steelGoods = goodsRepository.save(goods("STEEL_Y", 1.0, 1.0));
        Goods rubberGoods = goodsRepository.save(goods("RUBBER_Y", 1.0, 1.0));
        Goods tireGoods = goodsRepository.save(goods("TIRE_Y", 1.0, 1.0));
        Goods carGoods = goodsRepository.save(goods("CAR_Y", 0.1, 0.1));
        List<Goods> goods = List.of(oreGoods, steelGoods, rubberGoods, tireGoods, carGoods);
        goods.forEach(item -> item.setVehicleFit("E2E_TRUCK"));
        goodsRepository.saveAllAndFlush(goods);

        POI orePoi = poiRepository.save(poi("Ore source", 104.00, 30.00, POI.POIType.IRON_MINE));
        POI steelPoi = poiRepository.save(poi("Steel works", 104.10, 30.10, POI.POIType.STEEL_MILL));
        POI rubberPoi = poiRepository.save(poi("Rubber source", 104.20, 30.20,
                POI.POIType.RUBBER_PROCESSING_PLANT));
        POI tirePoi = poiRepository.save(poi("Tire works", 104.30, 30.30,
                POI.POIType.TIRE_MANUFACTURING_PLANT));
        POI assemblyPoi = poiRepository.save(poi("Assembly", 104.40, 30.40,
                POI.POIType.AUTO_ASSEMBLY_PLANT));
        POI sinkPoi = poiRepository.save(poi("Car receiver", 104.50, 30.50,
                POI.POIType.DISTRIBUTION_CENTER));
        poiRepository.save(poi("Warehouse", 104.05, 30.05, POI.POIType.WAREHOUSE));

        ProcessingChain chain = new ProcessingChain();
        chain.setChainCode("E2E-Y");
        chain.setChainName("E2E Y Chain");
        chain.setStatus(ProcessingChain.ChainStatus.ACTIVE);
        ProcessingStage ore = stage(chain, 1, "ore", "Ore source", orePoi,
                oreGoods, oreGoods, 1.0, 1);
        ProcessingStage steel = stage(chain, 2, "steel", "Steel works", steelPoi,
                oreGoods, steelGoods, 0.5, 60);
        ProcessingStage rubber = stage(chain, 3, "rubber", "Rubber source", rubberPoi,
                rubberGoods, rubberGoods, 1.0, 1);
        ProcessingStage tire = stage(chain, 4, "tire", "Tire works", tirePoi,
                rubberGoods, tireGoods, 0.25, 60);
        ProcessingStage assembly = stage(chain, 5, "assembly", "Assembly", assemblyPoi,
                null, carGoods, 1.0 / 9.0, 60);
        ProcessingStage sink = stage(chain, 6, "sink", "Car receiver", sinkPoi,
                carGoods, carGoods, 1.0, 1);
        ProcessingStageInput steelOre = input(steel, "ore", oreGoods);
        ProcessingStageInput tireRubber = input(tire, "rubber", rubberGoods);
        ProcessingStageInput assemblySteel = new ProcessingStageInput(
                assembly, "steel", steelGoods.getSku(), 5.0 / 9.0);
        assemblySteel.setGoods(steelGoods);
        ProcessingStageInput assemblyTire = new ProcessingStageInput(
                assembly, "tire", tireGoods.getSku(), 4.0 / 9.0);
        assemblyTire.setGoods(tireGoods);
        ProcessingStageInput sinkCar = input(sink, "car", carGoods);
        steel.setInputs(List.of(steelOre));
        tire.setInputs(List.of(tireRubber));
        assembly.setInputs(List.of(assemblySteel, assemblyTire));
        sink.setInputs(List.of(sinkCar));
        chain.setStages(List.of(ore, steel, rubber, tire, assembly, sink));
        chain.setEdges(List.of(
                new ProcessingStageEdge(chain, ore, steel, steelOre),
                new ProcessingStageEdge(chain, rubber, tire, tireRubber),
                new ProcessingStageEdge(chain, steel, assembly, assemblySteel),
                new ProcessingStageEdge(chain, tire, assembly, assemblyTire),
                new ProcessingStageEdge(chain, assembly, sink, sinkCar)
        ));
        chainRepository.saveAndFlush(chain);

        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate("E2E-001");
        vehicle.setVehicleType("E2E_TRUCK");
        vehicle.setMaxLoadCapacityTonnes(100.0);
        vehicle.setCargoVolume(100.0);
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
        vehicle.setCurrentPOI(orePoi);
        vehicle = vehicleRepository.saveAndFlush(vehicle);
        attachIdleDriver(vehicle);
    }

    private void attachIdleDriver(Vehicle vehicle) {
        Driver driver = new Driver();
        driver.setDriverName("E2E Driver");
        driver.setDriverPhone("13900000001");
        driver.setCurrentStatus(Driver.DriverStatus.IDLE);
        driver.setPreferredCargoType("钢铁");
        driver.setPreferredMaxDistanceKm(500.0);
        driver.setPreferredMaxWeightTons(100.0);
        driver.addVehicle(vehicle);
        driverRepository.saveAndFlush(driver);
    }

    private ProcessingStage stage(
            ProcessingChain chain,
            int order,
            String key,
            String name,
            POI poi,
            Goods input,
            Goods output,
            double ratio,
            int processingMinutes
    ) {
        ProcessingStage stage = new ProcessingStage();
        stage.setProcessingChain(chain);
        stage.setStageOrder(order);
        stage.setStageKey(key);
        stage.setStageName(name);
        stage.setProcessingPOI(poi);
        stage.setInputGoods(input);
        stage.setInputGoodsSku(input == null ? null : input.getSku());
        stage.setOutputGoods(output);
        stage.setOutputGoodsSku(output == null ? null : output.getSku());
        stage.setOutputWeightRatio(ratio);
        stage.setProcessingTimeMinutes(processingMinutes);
        return stage;
    }

    private ProcessingStageInput input(ProcessingStage stage, String key, Goods goods) {
        ProcessingStageInput input = new ProcessingStageInput(stage, key, goods.getSku(), 1.0);
        input.setGoods(goods);
        return input;
    }

    private Goods goods(String sku, double weight, double volume) {
        Goods goods = new Goods("Goods " + sku, sku);
        goods.setWeightPerUnit(weight);
        goods.setVolumePerUnit(volume);
        return goods;
    }

    private POI poi(String name, double longitude, double latitude, POI.POIType type) {
        return new POI(
                name,
                BigDecimal.valueOf(longitude),
                BigDecimal.valueOf(latitude),
                type
        );
    }
}
