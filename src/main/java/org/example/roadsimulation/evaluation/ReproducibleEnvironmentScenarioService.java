package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.core.SimulationTick;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Phase 7C/7D：按 seed 与 loopIndex 生成可复现外部环境事实，并冻结其应用模式。
 *
 * <p>实现无可变游标、无墙上时间、无随机数生成器，也不读取运输实体。因此同一配置和同一
 * tick 永远得到相同结果，暂停、恢复或重复计算不会让场景漂移。</p>
 */
@Component
public final class ReproducibleEnvironmentScenarioService {

    public static final String SCENARIO_VERSION = "1";
    public static final int CYCLE_TICKS = 16;

    private final String scenarioId;
    private final long seed;
    private final int modeledRoadCount;
    private final double normalNetworkSpeedKph;
    private final EnvironmentApplicationMode applicationMode;

    public ReproducibleEnvironmentScenarioService(
            String scenarioId,
            long seed,
            int modeledRoadCount,
            double normalNetworkSpeedKph
    ) {
        // Phase 7D：四参数构造器仅保留 Phase 7C Shadow 测试兼容，不作为生产注入入口。
        this(scenarioId, seed, modeledRoadCount, normalNetworkSpeedKph, false);
    }

    @Autowired
    public ReproducibleEnvironmentScenarioService(
            @Value("${simulation.environment.scenario-id:deterministic-network-cycle}") String scenarioId,
            @Value("${simulation.environment.seed:0}") long seed,
            @Value("${simulation.environment.modeled-road-count:100}") int modeledRoadCount,
            @Value("${simulation.environment.normal-network-speed-kph:60.0}") double normalNetworkSpeedKph,
            @Value("${simulation.environment.apply-to-progress:true}") boolean applyToProgress
    ) {
        // Phase 7C：配置在启动时失败封闭，禁止用非法基准生成看似有效的评价数据。
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId must not be blank");
        }
        if (modeledRoadCount <= 0) {
            throw new IllegalArgumentException("modeledRoadCount must be positive");
        }
        if (!Double.isFinite(normalNetworkSpeedKph) || normalNetworkSpeedKph <= 0.0) {
            throw new IllegalArgumentException("normalNetworkSpeedKph must be positive and finite");
        }
        this.scenarioId = scenarioId;
        this.seed = seed;
        this.modeledRoadCount = modeledRoadCount;
        this.normalNetworkSpeedKph = normalNetworkSpeedKph;
        // Phase 7D：模式由启动配置冻结，同一次应用运行中不会随调用漂移。
        this.applicationMode = applyToProgress
                ? EnvironmentApplicationMode.PROGRESS_AFFECTING
                : EnvironmentApplicationMode.SHADOW;
    }

    /** Phase 7C：从完整 tick 直接派生快照，不保存上一轮结果。 */
    public EnvironmentScenarioSnapshot snapshotFor(SimulationTick tick) {
        Objects.requireNonNull(tick, "tick must not be null");

        // Phase 7C：seed 只决定循环相位；floorMod 同时保证负 seed 仍稳定落入合法区间。
        int seedOffset = (int) Math.floorMod(seed, (long) CYCLE_TICKS);
        int cycleStep = Math.floorMod(tick.loopIndex() + seedOffset, CYCLE_TICKS);
        ScenarioProfile profile = profileFor(cycleStep);
        int closedRoads = Math.min(profile.closedRoadCount(), modeledRoadCount);
        double passabilityRatio = (double) (modeledRoadCount - closedRoads) / modeledRoadCount;
        double averageSpeedKph = normalNetworkSpeedKph / profile.travelTimeFactor();

        return new EnvironmentScenarioSnapshot(
                scenarioId,
                SCENARIO_VERSION,
                seed,
                tick.loopIndex(),
                tick.tickStart(),
                tick.tickEnd(),
                profile.phase(),
                applicationMode,
                modeledRoadCount,
                closedRoads,
                profile.abnormalEventCount(),
                profile.weatherRiskLevel(),
                normalNetworkSpeedKph,
                averageSpeedKph,
                profile.congestionIndex(),
                passabilityRatio,
                profile.travelTimeFactor()
        );
    }

    private ScenarioProfile profileFor(int cycleStep) {
        // Phase 7C：固定阶段表便于复现实验和人工核对；不存在依赖调用次数的隐式状态。
        if (cycleStep <= 3) {
            return new ScenarioProfile(EnvironmentScenarioPhase.NORMAL, 0, 0, 0, 1.00, 1.00);
        }
        if (cycleStep <= 7) {
            return new ScenarioProfile(EnvironmentScenarioPhase.RAIN, 0, 0, 2, 1.05, 1.25);
        }
        if (cycleStep <= 11) {
            return new ScenarioProfile(EnvironmentScenarioPhase.PEAK_CONGESTION, 0, 0, 0, 1.50, 1.50);
        }
        if (cycleStep == 12) {
            return new ScenarioProfile(EnvironmentScenarioPhase.INCIDENT, 2, 1, 0, 1.80, 1.80);
        }
        return new ScenarioProfile(EnvironmentScenarioPhase.RECOVERY, 1, 1, 0, 1.15, 1.15);
    }

    /** Phase 7C：阶段常量与外部配置分离；基准速度和逻辑道路规模仍可按实验调整。 */
    private record ScenarioProfile(
            EnvironmentScenarioPhase phase,
            int closedRoadCount,
            int abnormalEventCount,
            int weatherRiskLevel,
            double congestionIndex,
            double travelTimeFactor
    ) {
    }
}
