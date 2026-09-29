package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.DriverRepository;
import org.example.roadsimulation.sandbox.random.SandboxRandomDomain;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * 司机概率行为状态表（马尔可夫转移）。
 * 每个仿真 tick 对状态 ∈ {IDLE, REJECTING, MAINTENANCE} 的司机掷骰子：
 * IDLE → MAINTENANCE 概率 p2、IDLE → REJECTING 概率 p1；
 * REJECTING → IDLE 概率 q1、MAINTENANCE → IDLE 概率 q2。
 * 停留时长服从几何分布。ASSIGNED/OFF 不参与。
 * 每名司机每一轮只消费由“根种子 + 随机领域 + loopIndex + driverId”派生的一次随机值。
 * 派单候选必须至少存在一名 IDLE 司机。
 */
@Service
public class DriverBehaviorService {

    public static final double DEFAULT_IDLE_TO_REJECTING = 0.02;
    public static final double DEFAULT_IDLE_TO_MAINTENANCE = 0.01;
    public static final double DEFAULT_REJECTING_TO_IDLE = 0.30;
    public static final double DEFAULT_MAINTENANCE_TO_IDLE = 0.25;

    private static final Logger logger = LoggerFactory.getLogger(DriverBehaviorService.class);

    private final DriverRepository driverRepository;
    private final SimulationContext simulationContext;
    private final SandboxRandomProtocol randomProtocol;
    private final DoubleSupplier testRollSupplier;
    private SandboxRunRuntimeContext sandboxRunRuntimeContext;

    private boolean enabled;
    private String ordinaryRootSeed = "0";
    private double idleToRejecting = DEFAULT_IDLE_TO_REJECTING;
    private double idleToMaintenance = DEFAULT_IDLE_TO_MAINTENANCE;
    private double rejectingToIdle = DEFAULT_REJECTING_TO_IDLE;
    private double maintenanceToIdle = DEFAULT_MAINTENANCE_TO_IDLE;

    @Autowired
    public DriverBehaviorService(
            DriverRepository driverRepository,
            SimulationContext simulationContext,
            ObjectMapper objectMapper
    ) {
        this.driverRepository = driverRepository;
        this.simulationContext = simulationContext;
        this.randomProtocol = new SandboxRandomProtocol(objectMapper);
        this.testRollSupplier = null;
    }

    DriverBehaviorService(DriverRepository driverRepository, DoubleSupplier testRollSupplier) {
        this.driverRepository = driverRepository;
        this.simulationContext = null;
        this.randomProtocol = null;
        this.testRollSupplier = testRollSupplier;
        this.enabled = true;
    }

    @Autowired(required = false)
    public void setSandboxRunRuntimeContext(SandboxRunRuntimeContext sandboxRunRuntimeContext) {
        this.sandboxRunRuntimeContext = sandboxRunRuntimeContext;
    }

    @Value("${app.simulation.driver-behavior.enabled:false}")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Value("${app.simulation.driver-behavior.root-seed:0}")
    public void setOrdinaryRootSeed(String ordinaryRootSeed) {
        SandboxRandomProtocol.validateRootSeed(ordinaryRootSeed);
        this.ordinaryRootSeed = ordinaryRootSeed;
    }

    @Value("${app.simulation.driver-behavior.idle-to-rejecting:" + DEFAULT_IDLE_TO_REJECTING + "}")
    public void setIdleToRejecting(double idleToRejecting) {
        this.idleToRejecting = idleToRejecting;
    }

    @Value("${app.simulation.driver-behavior.idle-to-maintenance:" + DEFAULT_IDLE_TO_MAINTENANCE + "}")
    public void setIdleToMaintenance(double idleToMaintenance) {
        this.idleToMaintenance = idleToMaintenance;
    }

    @Value("${app.simulation.driver-behavior.rejecting-to-idle:" + DEFAULT_REJECTING_TO_IDLE + "}")
    public void setRejectingToIdle(double rejectingToIdle) {
        this.rejectingToIdle = rejectingToIdle;
    }

    @Value("${app.simulation.driver-behavior.maintenance-to-idle:" + DEFAULT_MAINTENANCE_TO_IDLE + "}")
    public void setMaintenanceToIdle(double maintenanceToIdle) {
        this.maintenanceToIdle = maintenanceToIdle;
    }

    /**
     * 每个仿真 tick 执行一次马尔可夫状态转移。
     */
    @Transactional
    public void tick(LocalDateTime simNow) {
        BehaviorParameters parameters = parameters();
        if (!parameters.enabled()) {
            return;
        }
        List<Driver> drivers = driverRepository.findByCurrentStatusIn(
                        List.of(Driver.DriverStatus.IDLE, Driver.DriverStatus.REJECTING,
                                Driver.DriverStatus.MAINTENANCE))
                .stream()
                .sorted(Comparator.comparing(Driver::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        if (drivers.isEmpty()) {
            return;
        }

        Map<Driver.DriverStatus, Integer> transitions = new EnumMap<>(Driver.DriverStatus.class);
        for (Driver driver : drivers) {
            Driver.DriverStatus next = rollTransition(
                    driver.getCurrentStatus(), roll(driver), parameters);
            if (next == driver.getCurrentStatus()) {
                continue;
            }
            driver.setCurrentStatus(next);
            driver.setUpdatedBy("DriverBehaviorService");
            driver.setUpdatedTime(simNow);
            transitions.merge(next, 1, Integer::sum);
        }
        if (transitions.isEmpty()) {
            return;
        }
        driverRepository.saveAll(drivers);
        logger.info("[DriverBehavior] tick 转移: {}", transitions);
    }

    private double roll(Driver driver) {
        if (testRollSupplier != null) {
            return testRollSupplier.getAsDouble();
        }
        if (driver.getId() == null) {
            throw new IllegalStateException("Persisted driver id is required for deterministic behavior");
        }
        Map<String, Object> key = Map.of(
                "loopIndex", simulationContext == null ? 0 : simulationContext.getLoopCount(),
                "driverId", driver.getId());
        if (sandboxRunRuntimeContext != null) {
            return sandboxRunRuntimeContext.random(
                    SandboxRandomDomain.DRIVER_BEHAVIOR_TRANSITION, key).nextDouble();
        }
        return randomProtocol.random(
                ordinaryRootSeed, SandboxRandomDomain.DRIVER_BEHAVIOR_TRANSITION, key).nextDouble();
    }

    private BehaviorParameters parameters() {
        if (sandboxRunRuntimeContext == null) {
            return new BehaviorParameters(enabled, idleToRejecting, idleToMaintenance,
                    rejectingToIdle, maintenanceToIdle);
        }
        SandboxRunSpecificationV1.DriverBehavior specification =
                sandboxRunRuntimeContext.driverBehavior();
        return new BehaviorParameters(
                specification.enabled(), specification.idleToRejecting(),
                specification.idleToMaintenance(), specification.rejectingToIdle(),
                specification.maintenanceToIdle());
    }

    private Driver.DriverStatus rollTransition(
            Driver.DriverStatus current,
            double r,
            BehaviorParameters parameters
    ) {
        switch (current) {
            case IDLE:
                if (r < parameters.idleToMaintenance()) {
                    return Driver.DriverStatus.MAINTENANCE;
                }
                if (r < parameters.idleToMaintenance() + parameters.idleToRejecting()) {
                    return Driver.DriverStatus.REJECTING;
                }
                return Driver.DriverStatus.IDLE;
            case REJECTING:
                return r < parameters.rejectingToIdle()
                        ? Driver.DriverStatus.IDLE : Driver.DriverStatus.REJECTING;
            case MAINTENANCE:
                return r < parameters.maintenanceToIdle()
                        ? Driver.DriverStatus.IDLE : Driver.DriverStatus.MAINTENANCE;
            default:
                return current;
        }
    }

    /**
     * 只保留至少绑定一名 IDLE 司机的车辆；无司机、拒单、维护和下线司机均不可支持派单。
     * 依赖事务内 LAZY 加载 vehicle.getDrivers()。
     */
    @Transactional(readOnly = true)
    public List<Vehicle> filterVehiclesWithIdleDriver(List<Vehicle> idleVehicles) {
        if (idleVehicles == null || idleVehicles.isEmpty()) {
            return idleVehicles;
        }
        return idleVehicles.stream()
                .filter(v -> v.getDrivers() != null && v.getDrivers().stream()
                        .anyMatch(d -> d != null && d.getCurrentStatus() == Driver.DriverStatus.IDLE))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private record BehaviorParameters(
            boolean enabled,
            double idleToRejecting,
            double idleToMaintenance,
            double rejectingToIdle,
            double maintenanceToIdle
    ) {}
}
