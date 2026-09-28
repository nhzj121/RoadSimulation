package org.example.roadsimulation.phase7c;

import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.evaluation.EnvironmentScenarioPhase;
import org.example.roadsimulation.evaluation.EnvironmentScenarioSnapshot;
import org.example.roadsimulation.evaluation.ReproducibleEnvironmentScenarioService;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV1;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Phase 7C 验收：环境场景由 seed + loopIndex 确定，重复计算不依赖调用历史。 */
@Tag("phase7c")
class ReproducibleEnvironmentScenarioServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    void sameConfigurationAndTickProduceIdenticalSnapshots() {
        ReproducibleEnvironmentScenarioService first = service(7L);
        ReproducibleEnvironmentScenarioService second = service(7L);
        SimulationTick tick = tick(9);

        // Phase 7C：跨实例相等证明结果不依赖内存游标、随机调用次序或墙上时间。
        assertEquals(first.snapshotFor(tick), second.snapshotFor(tick));
        assertEquals(first.snapshotFor(tick), first.snapshotFor(tick));
    }

    @Test
    void fixedCyclePublishesExpectedNormalRainPeakIncidentAndRecoveryFacts() {
        ReproducibleEnvironmentScenarioService service = service(0L);

        EnvironmentScenarioSnapshot normal = service.snapshotFor(tick(0));
        EnvironmentScenarioSnapshot rain = service.snapshotFor(tick(4));
        EnvironmentScenarioSnapshot peak = service.snapshotFor(tick(8));
        EnvironmentScenarioSnapshot incident = service.snapshotFor(tick(12));
        EnvironmentScenarioSnapshot recovery = service.snapshotFor(tick(13));

        assertEquals(EnvironmentScenarioPhase.NORMAL, normal.phase());
        assertEquals(EnvironmentScenarioPhase.RAIN, rain.phase());
        assertEquals(EnvironmentScenarioPhase.PEAK_CONGESTION, peak.phase());
        assertEquals(EnvironmentScenarioPhase.INCIDENT, incident.phase());
        assertEquals(EnvironmentScenarioPhase.RECOVERY, recovery.phase());
        assertEquals(60.0, normal.networkAverageSpeedKph(), 1.0e-9);
        assertEquals(48.0, rain.networkAverageSpeedKph(), 1.0e-9);
        assertEquals(40.0, peak.networkAverageSpeedKph(), 1.0e-9);
        assertEquals(2, incident.closedRoadCount());
        assertEquals(0.98, incident.roadPassabilityRatio(), 1.0e-9);
        assertEquals(1, recovery.abnormalEventCount());
        assertTrue(normal.shadowMode());
    }

    @Test
    void cycleWrapsWithoutDependingOnElapsedWallTime() {
        ReproducibleEnvironmentScenarioService service = service(0L);

        // Phase 7C：相差一个完整周期时场景数值相同，但快照仍保留各自 tick 身份。
        EnvironmentScenarioSnapshot first = service.snapshotFor(tick(0));
        EnvironmentScenarioSnapshot wrapped = service.snapshotFor(tick(16));

        assertEquals(first.phase(), wrapped.phase());
        assertEquals(first.travelTimeFactor(), wrapped.travelTimeFactor(), 1.0e-9);
        assertEquals(first.networkAverageSpeedKph(), wrapped.networkAverageSpeedKph(), 1.0e-9);
        assertEquals(16, wrapped.loopIndex());
    }

    @Test
    void sandboxPhaseAndEnvironmentParametersComeFromPublishedRunSpecification() {
        ReproducibleEnvironmentScenarioService service = service(0L);
        SandboxRunRuntimeContext runtime = mock(SandboxRunRuntimeContext.class);
        SandboxRunSpecificationV1 specification = mock(SandboxRunSpecificationV1.class);
        when(runtime.environmentPhaseSeed()).thenReturn(5L);
        when(runtime.specification()).thenReturn(specification);
        when(specification.environment()).thenReturn(new SandboxRunSpecificationV1.Environment(
                "sandbox-environment", "1", 200, 80.0,
                "PROGRESS_AFFECTING", "DERIVED_FROM_ROOT"));
        service.setSandboxRunRuntimeContext(runtime);

        EnvironmentScenarioSnapshot snapshot = service.snapshotFor(tick(0));

        assertEquals("sandbox-environment", snapshot.scenarioId());
        assertEquals(5L, snapshot.seed());
        assertEquals(200, snapshot.modeledRoadCount());
        assertEquals(80.0 / 1.25, snapshot.networkAverageSpeedKph(), 1.0e-9);
        assertTrue(snapshot.progressInfluenceEnabled());
    }

    private ReproducibleEnvironmentScenarioService service(long seed) {
        return new ReproducibleEnvironmentScenarioService("phase7c-test", seed, 100, 60.0);
    }

    private SimulationTick tick(int loopIndex) {
        LocalDateTime tickStart = START.plusMinutes(30L * loopIndex);
        return SimulationTick.of(loopIndex, tickStart, Duration.ofMinutes(30));
    }
}
