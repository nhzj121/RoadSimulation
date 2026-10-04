package org.example.roadsimulation.service;

import org.example.roadsimulation.RoadSimulationApplication;
import org.example.roadsimulation.SimulationDataCleanupService;
import org.example.roadsimulation.SimulationMainLoop;
import org.example.roadsimulation.config.DemandGenerationMode;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.evaluation.EvaluationMetricId;
import org.example.roadsimulation.evaluation.EvaluationMetricValueStatus;
import org.example.roadsimulation.evaluation.EvaluationSnapshotService;
import org.example.roadsimulation.evaluation.WaitFactLedgerHealth;
import org.example.roadsimulation.evaluation.DeliverySlaLedgerHealth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.doNothing;

/** Opt-in real scheduler/vehicle audit, isolated from the four-chain planning audit. */
@EnabledIfSystemProperty(named = "roadsim.chain.dispatch.audit", matches = "true")
@SpringBootTest(
        classes = RoadSimulationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "sandbox.management.enabled=false",
                "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/roadsim_collation_audit"
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.task.scheduling.enabled=false",
                "app.vehicle.import.enabled=false",
                "app.simulation.startup-pre-generation.enabled=false",
                "simulation.demand.random-seed=20260922"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductionFourChainRealDispatchDockerAuditTest {

    // Keep the real dispatcher and reset service. After an explicit reset,
    // suppress only shutdown cleanup so audit evidence remains inspectable.
    @MockitoSpyBean private SimulationDataCleanupService cleanupService;
    @MockitoBean private GaodeMapService gaodeMapService;
    @MockitoBean private RoutePlanningService routePlanningService;

    @Autowired private SimulationMainLoop mainLoop;
    @Autowired private SimulationContext simulationContext;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EvaluationSnapshotService evaluationSnapshotService;
    @Autowired private WaitFactLedgerHealth waitFactLedgerHealth;
    @Autowired private DeliverySlaLedgerHealth deliverySlaLedgerHealth;
    @Autowired private SimulationRuntimeConfig runtimeConfig;

    @Test
    void allFourImportedChainsCanCompleteThroughTheNormalMainLoop() {
        prepareIsolatedAuditRun();
        assertThat(runtimeConfig.getDemandGenerationMode())
                .isEqualTo(DemandGenerationMode.PRODUCTION);
        List<Long> activeChainIds = jdbc.queryForList(
                "select id from processing_chain where status = 'ACTIVE'", Long.class);
        assertThat(activeChainIds).hasSize(4);
        mainLoop.start();
        try {
            String runId = simulationContext.getSimulationRunId().orElseThrow();
            mainLoop.executeMainLoop();
            assertThat(count("select count(*) from production_plan where simulation_run_id = ?", runId))
                    .isEqualTo(4);

            // Freeze new demand after the first cycle so this checks completion
            // of one demand per chain rather than unbounded six-round arrivals.
            activeChainIds.forEach(id -> jdbc.update(
                    "update processing_chain set status = 'INACTIVE' where id = ?", id));
            for (int round = 1; round < 120; round++) {
                if (count("""
                        select count(*) from production_batch b
                        join production_plan p on p.id = b.plan_id
                        where p.simulation_run_id = ? and b.status = 'COMPLETED'
                        """, runId) == 4) {
                    break;
                }
                mainLoop.executeMainLoop();
            }

            assertThat(count("""
                    select count(*) from production_batch b
                    join production_plan p on p.id = b.plan_id
                    where p.simulation_run_id = ? and b.status = 'COMPLETED'
                    """, runId)).isEqualTo(4);
            assertThat(count("""
                    select count(*) from processing_execution_flow f
                    join production_batch b on b.id = f.batch_id
                    join production_plan p on p.id = b.plan_id
                    where p.simulation_run_id = ? and f.status = 'DELIVERED'
                    """, runId)).isEqualTo(11);
            assertThat(count("""
                    select count(*) from production_batch b
                    join production_plan p on p.id = b.plan_id
                    where p.simulation_run_id = ?
                      and abs(b.actual_final_output_weight - p.final_demand_weight) > 0.001
                    """, runId)).isZero();
            assertThat(count("""
                    select count(*) from shipment s
                    join processing_execution_flow f on f.shipment_id = s.id
                    join production_batch b on b.id = f.batch_id
                    join production_plan p on p.id = b.plan_id
                    where p.simulation_run_id = ?
                      and (s.status <> 'DELIVERED' or s.demand_source <> 'PRODUCTION')
                    """, runId)).isZero();
            assertThat(count("""
                    select count(*) from shipment_item si
                    join processing_execution_flow f on f.shipment_id = si.shipment_id
                    join production_batch b on b.id = f.batch_id
                    join production_plan p on p.id = b.plan_id
                    where p.simulation_run_id = ?
                      and (si.status <> 'DELIVERED' or si.assignment_id is null)
                    """, runId)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from assignment where status = 'COMPLETED'", Integer.class))
                    .isPositive();

            double transportedTonnes = jdbc.queryForObject(
                    "select coalesce(sum(weight), 0) from shipment_item", Double.class);
            var snapshot = evaluationSnapshotService.latest().orElseThrow();
            var required = snapshot.metrics().get(EvaluationMetricId.CARGO_REQUIRED_TONNES.getMetricId());
            var delivered = snapshot.metrics().get(EvaluationMetricId.CARGO_DELIVERED_TONNES.getMetricId());
            assertThat(required.status()).isEqualTo(EvaluationMetricValueStatus.AVAILABLE);
            assertThat(delivered.status()).isEqualTo(EvaluationMetricValueStatus.AVAILABLE);
            assertThat(required.value()).isCloseTo(transportedTonnes, offset(0.001));
            assertThat(delivered.value()).isCloseTo(transportedTonnes, offset(0.001));
            assertThat(waitFactLedgerHealth.hasProjectionFailures()).isFalse();
            assertThat(deliverySlaLedgerHealth.hasProjectionFailures()).isFalse();
        } finally {
            mainLoop.stop();
            activeChainIds.forEach(id -> jdbc.update(
                    "update processing_chain set status = 'ACTIVE' where id = ?", id));
        }
    }

    @Test
    void mixedModeCreatesBothLegacyAndProductionShipmentsForEvaluation() {
        prepareIsolatedAuditRun();
        runtimeConfig.setDemandGenerationMode(DemandGenerationMode.MIXED);
        mainLoop.start();
        try {
            for (int loop = 0; loop < 24; loop++) {
                mainLoop.executeMainLoop();
                if (sourceCount("LEGACY") > 0) {
                    break;
                }
            }
            assertThat(sourceCount("PRODUCTION")).isPositive();
            assertThat(sourceCount("LEGACY")).isPositive();
            assertThat(jdbc.queryForObject("select count(*) from production_plan", Integer.class))
                    .isPositive();
            double requiredTonnes = jdbc.queryForObject(
                    "select coalesce(sum(weight), 0) from shipment_item", Double.class);
            var metric = evaluationSnapshotService.latest().orElseThrow()
                    .metrics().get(EvaluationMetricId.CARGO_REQUIRED_TONNES.getMetricId());
            assertThat(metric.status()).isEqualTo(EvaluationMetricValueStatus.AVAILABLE);
            assertThat(metric.value()).isCloseTo(requiredTonnes, offset(0.001));
        } finally {
            mainLoop.stop();
            runtimeConfig.setDemandGenerationMode(DemandGenerationMode.PRODUCTION);
        }
    }

    private int sourceCount(String source) {
        return jdbc.queryForObject(
                "select count(*) from shipment where demand_source = ?", Integer.class, source);
    }

    private void prepareIsolatedAuditRun() {
        cleanupService.cleanupAllSimulationData();
        doNothing().when(cleanupService).cleanupAllSimulationData();
        doNothing().when(cleanupService).resetAllVehiclesToRandomInitializationPOIs();
    }

    private int count(String sql, String runId) {
        return jdbc.queryForObject(sql, Integer.class, runId);
    }
}
