package org.example.roadsimulation.service;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.RoadSimulationApplication;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProductionBatch;
import org.example.roadsimulation.entity.ProductionPlan;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProductionBatchRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.service.impl.ProductionDeliveryProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/** Explicit opt-in audit against the isolated Docker copy of the four legacy chains. */
@EnabledIfSystemProperty(named = "roadsim.chain.audit", matches = "true")
@SpringBootTest(
        classes = RoadSimulationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/roadsim_chain_audit"
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.task.scheduling.enabled=false",
                "app.vehicle.import.enabled=false",
                "app.simulation.startup-pre-generation.enabled=false"
        }
)
class ProductionFourChainDockerAuditTest {

    @MockitoBean private DataInitializer dataInitializer;
    @MockitoBean private GaodeMapService gaodeMapService;

    @Autowired private ProcessingChainRepository chainRepository;
    @Autowired private ProductionPlanRepository planRepository;
    @Autowired private ProductionBatchRepository batchRepository;
    @Autowired private ProductionDemandGenerator generator;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProductionDeliveryProcessor deliveryProcessor;
    @Autowired private ProductionExecutionService executionService;
    @Autowired private SimulationRuntimeConfig runtimeConfig;

    @Test
    void everyImportedActiveChainCreatesOneReleasedPlanAndBatch() {
        List<ProcessingChain> active = chainRepository.findByStatus(ProcessingChain.ChainStatus.ACTIVE);
        assertThat(active).hasSize(4);
        SimulationTick tick = SimulationTick.of(6,
                LocalDateTime.of(2026, 1, 1, 3, 0), Duration.ofMinutes(30));

        generator.generate("docker-four-chain-audit", tick);
        generator.generate("docker-four-chain-audit", tick);

        int totalNodes = 0;
        int totalFlows = 0;
        int initialTransports = 0;
        for (ProcessingChain chain : active) {
            ProductionPlan plan = planRepository
                    .findBySimulationRunIdAndGenerationRoundAndChainId(
                            "docker-four-chain-audit", 6, chain.getId())
                    .orElseThrow();
            assertThat(plan.getStatus()).isEqualTo(ProductionPlan.PlanStatus.RELEASED);
            assertThat(batchRepository.findByPlanId(plan.getId()))
                    .hasSize(1)
                    .allSatisfy(batch -> assertThat(batch.getStatus())
                            .isNotEqualTo(ProductionBatch.BatchStatus.FAILED));

            Long batchId = batchRepository.findByPlanId(plan.getId()).get(0).getId();
            int stages = count("select count(*) from processing_stage where chain_id = ?", chain.getId());
            int nodes = count("select count(*) from production_plan_node where plan_id = ?", plan.getId());
            int flows = count("select count(*) from production_plan_flow where plan_id = ?", plan.getId());
            int runtimeFlows = count("select count(*) from processing_execution_flow where batch_id = ?", batchId);
            int sourceTransports = count("""
                    select count(*) from processing_execution_flow f
                    join processing_stage_execution e on e.id = f.from_execution_id
                    join production_plan_node n on n.id = e.plan_node_id
                    where f.batch_id = ? and n.node_role = 'SOURCE' and f.shipment_id is not null
                    """, batchId);
            assertThat(nodes).isEqualTo(stages);
            assertThat(flows).isEqualTo(stages - 1);
            assertThat(runtimeFlows).isEqualTo(flows);
            assertThat(sourceTransports).isEqualTo(1);
            assertThat(count("select count(*) from production_plan_node where plan_id = ? and selected_poi_id is null", plan.getId())).isZero();
            assertThat(count("select count(*) from production_plan_node where plan_id = ? and node_role = 'SINK'", plan.getId())).isEqualTo(1);
            assertThat(count("""
                    select count(*) from processing_execution_flow f
                    join processing_stage_execution e on e.id = f.from_execution_id
                    join production_plan_node n on n.id = e.plan_node_id
                    where f.batch_id = ? and n.node_role <> 'SOURCE' and f.shipment_id is not null
                    """, batchId)).isZero();
            totalNodes += nodes;
            totalFlows += flows;
            initialTransports += sourceTransports;
        }
        assertThat(totalNodes).isEqualTo(15);
        assertThat(totalFlows).isEqualTo(11);
        assertThat(initialTransports).isEqualTo(4);
    }

    private int count(String sql, Object id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    @Test
    void importedChainsAdvanceFromSourceToSinkAfterEachDelivery() {
        String runId = "docker-four-chain-progress";
        int round = 12;
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        generator.generate(runId, SimulationTick.of(round, start, Duration.ofMinutes(30)));

        // Inject completed transport facts here. Real assignment and vehicle transitions are
        // covered by ProductionDemandEndToEndIntegrationTest; this audits four imported DAGs.
        for (int step = 0; step < 15; step++) {
            List<Long> pending = jdbc.queryForList("""
                    select f.shipment_id from processing_execution_flow f
                    join production_batch b on b.id = f.batch_id
                    join production_plan p on p.id = b.plan_id
                    where p.simulation_run_id = ? and p.generation_round = ?
                      and f.shipment_id is not null and f.status <> 'DELIVERED'
                    """, Long.class, runId, round);
            if (pending.isEmpty()) {
                break;
            }
            LocalDateTime deliveredAt = start.plusDays(step * 2L + 1);
            for (Long shipmentId : pending) {
                jdbc.update("update shipment_item set status = 'DELIVERED' where shipment_id = ?", shipmentId);
                jdbc.update("update shipment set status = 'DELIVERED' where id = ?", shipmentId);
                deliveryProcessor.processShipment(shipmentId, deliveredAt);
            }
            executionService.updateProgress(deliveredAt.plusDays(1), 30);
        }

        int shipmentCount = 0;
        for (ProcessingChain chain : chainRepository.findByStatus(ProcessingChain.ChainStatus.ACTIVE)) {
            ProductionPlan plan = planRepository.findBySimulationRunIdAndGenerationRoundAndChainId(
                    runId, round, chain.getId()).orElseThrow();
            ProductionBatch batch = batchRepository.findByPlanId(plan.getId()).get(0);
            assertThat(batch.getStatus()).isEqualTo(ProductionBatch.BatchStatus.COMPLETED);
            assertThat(batch.getActualFinalOutputWeight())
                    .isCloseTo(plan.getFinalDemandWeight(), offset(0.001));
            int edges = count("select count(*) from production_plan_flow where plan_id = ?", plan.getId());
            assertThat(count("""
                    select count(*) from processing_execution_flow
                    where batch_id = ? and status = 'DELIVERED' and shipment_id is not null
                    """, batch.getId())).isEqualTo(edges);
            shipmentCount += edges;
        }
        assertThat(shipmentCount).isEqualTo(11);
    }

    @Test
    void fixedSeedReproducesDemandAndPoiSnapshotsAcrossRuns() {
        Long previousSeed = runtimeConfig.getDemandRandomSeed();
        runtimeConfig.setDemandRandomSeed(20260922L);
        try {
            SimulationTick tick = SimulationTick.of(18,
                    LocalDateTime.of(2026, 1, 1, 9, 0), Duration.ofMinutes(30));
            generator.generate("docker-fixed-seed-a", tick);
            generator.generate("docker-fixed-seed-b", tick);

            for (ProcessingChain chain : chainRepository.findByStatus(ProcessingChain.ChainStatus.ACTIVE)) {
                ProductionPlan a = planRepository.findBySimulationRunIdAndGenerationRoundAndChainId(
                        "docker-fixed-seed-a", 18, chain.getId()).orElseThrow();
                ProductionPlan b = planRepository.findBySimulationRunIdAndGenerationRoundAndChainId(
                        "docker-fixed-seed-b", 18, chain.getId()).orElseThrow();
                assertThat(a.getFinalDemandWeight()).isEqualTo(b.getFinalDemandWeight());
                assertThat(a.getRandomSeed()).isEqualTo(b.getRandomSeed());
                assertThat(nodeSnapshots(a.getId())).isEqualTo(nodeSnapshots(b.getId()));
            }
        } finally {
            runtimeConfig.setDemandRandomSeed(previousSeed);
        }
    }

    private List<java.util.Map<String, Object>> nodeSnapshots(Long planId) {
        return jdbc.queryForList("""
                select stage_order, selected_poi_id, planned_input_weight, planned_output_weight
                from production_plan_node where plan_id = ? order by stage_order
                """, planId);
    }
}
