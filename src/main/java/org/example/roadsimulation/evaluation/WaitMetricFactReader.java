package org.example.roadsimulation.evaluation;

import org.example.roadsimulation.repository.CargoWaitEpisodeRepository;
import org.example.roadsimulation.repository.TaskWaitEpisodeRepository;
import org.example.roadsimulation.repository.VehicleWaitEpisodeRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 9A-2：按评价 runId 一次性读取三类等待账本及其健康状态。
 *
 * <p>该组件只返回不可变列表，不查询或修改业务实体；计算器据此生成同一运行的等待指标。</p>
 */
@Component
public class WaitMetricFactReader {

    private final VehicleWaitEpisodeRepository vehicleRepository;
    private final CargoWaitEpisodeRepository cargoRepository;
    private final TaskWaitEpisodeRepository taskRepository;
    private final WaitFactLedgerHealth health;

    public WaitMetricFactReader(
            VehicleWaitEpisodeRepository vehicleRepository,
            CargoWaitEpisodeRepository cargoRepository,
            TaskWaitEpisodeRepository taskRepository,
            WaitFactLedgerHealth health
    ) {
        this.vehicleRepository = vehicleRepository;
        this.cargoRepository = cargoRepository;
        this.taskRepository = taskRepository;
        this.health = health;
    }

    public WaitFacts read(String simulationRunId) {
        if (simulationRunId == null || simulationRunId.isBlank()) {
            throw new IllegalArgumentException("simulationRunId must not be blank");
        }
        // Phase 9A-2：按 runId 隔离普通仿真及对比实验的每个策略样本。
        return new WaitFacts(
                List.copyOf(vehicleRepository.findAllBySimulationRunId(simulationRunId)),
                List.copyOf(cargoRepository.findAllBySimulationRunId(simulationRunId)),
                List.copyOf(taskRepository.findAllBySimulationRunId(simulationRunId)),
                health.hasProjectionFailures(),
                health.hasProjectionFailures() ? health.describeFirstFailure() : null
        );
    }

    /** Phase 9A-2：指标计算输入只包含等待事实，不泄露可变仓库或业务对象。 */
    public record WaitFacts(
            List<VehicleWaitEpisode> vehicleEpisodes,
            List<CargoWaitEpisode> cargoEpisodes,
            List<TaskWaitEpisode> taskEpisodes,
            boolean projectionFailed,
            String failureReason
    ) {
    }
}
