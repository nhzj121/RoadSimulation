package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 司机概率行为状态表（马尔可夫转移）。
 * 每个仿真 tick 对状态 ∈ {IDLE, REJECTING, MAINTENANCE} 的司机掷骰子：
 * IDLE → MAINTENANCE 概率 p2、IDLE → REJECTING 概率 p1；
 * REJECTING → IDLE 概率 q1、MAINTENANCE → IDLE 概率 q2。
 * 停留时长服从几何分布。ASSIGNED/OFF 不参与。
 * MAINTENANCE 司机所绑定的车辆在派单时被剔除（filterMaintenanceVehicles）。
 */
@Service
public class DriverBehaviorService {

    public static final double DEFAULT_IDLE_TO_REJECTING = 0.02;
    public static final double DEFAULT_IDLE_TO_MAINTENANCE = 0.01;
    public static final double DEFAULT_REJECTING_TO_IDLE = 0.30;
    public static final double DEFAULT_MAINTENANCE_TO_IDLE = 0.25;

    private static final Logger logger = LoggerFactory.getLogger(DriverBehaviorService.class);

    private final DriverRepository driverRepository;
    private final Random random;

    private double idleToRejecting = DEFAULT_IDLE_TO_REJECTING;
    private double idleToMaintenance = DEFAULT_IDLE_TO_MAINTENANCE;
    private double rejectingToIdle = DEFAULT_REJECTING_TO_IDLE;
    private double maintenanceToIdle = DEFAULT_MAINTENANCE_TO_IDLE;

    @Autowired
    public DriverBehaviorService(DriverRepository driverRepository) {
        this(driverRepository, new Random());
    }

    DriverBehaviorService(DriverRepository driverRepository, Random random) {
        this.driverRepository = driverRepository;
        this.random = random;
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
        List<Driver> drivers = driverRepository.findByCurrentStatusIn(
                List.of(Driver.DriverStatus.IDLE, Driver.DriverStatus.REJECTING, Driver.DriverStatus.MAINTENANCE));
        if (drivers.isEmpty()) {
            return;
        }

        Map<Driver.DriverStatus, Integer> transitions = new EnumMap<>(Driver.DriverStatus.class);
        for (Driver driver : drivers) {
            Driver.DriverStatus next = rollTransition(driver.getCurrentStatus());
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

    private Driver.DriverStatus rollTransition(Driver.DriverStatus current) {
        double r = random.nextDouble();
        switch (current) {
            case IDLE:
                if (r < idleToMaintenance) {
                    return Driver.DriverStatus.MAINTENANCE;
                }
                if (r < idleToMaintenance + idleToRejecting) {
                    return Driver.DriverStatus.REJECTING;
                }
                return Driver.DriverStatus.IDLE;
            case REJECTING:
                return r < rejectingToIdle ? Driver.DriverStatus.IDLE : Driver.DriverStatus.REJECTING;
            case MAINTENANCE:
                return r < maintenanceToIdle ? Driver.DriverStatus.IDLE : Driver.DriverStatus.MAINTENANCE;
            default:
                return current;
        }
    }

    /**
     * 剔除绑定司机中存在 MAINTENANCE 的车辆（司机带车保养期间不可派单）。
     * 依赖事务内 LAZY 加载 vehicle.getDrivers()。
     */
    @Transactional(readOnly = true)
    public List<Vehicle> filterMaintenanceVehicles(List<Vehicle> idleVehicles) {
        if (idleVehicles == null || idleVehicles.isEmpty()) {
            return idleVehicles;
        }
        return idleVehicles.stream()
                .filter(v -> v.getDrivers() == null || v.getDrivers().stream()
                        .noneMatch(d -> d.getCurrentStatus() == Driver.DriverStatus.MAINTENANCE))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
