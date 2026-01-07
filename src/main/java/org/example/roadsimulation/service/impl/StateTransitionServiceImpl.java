package org.example.roadsimulation.service.impl;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Vehicle.VehicleStatus;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.StateTransitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 车辆状态转移服务实现类
 */
@Service
public class StateTransitionServiceImpl implements StateTransitionService {

    private static final Logger logger = LoggerFactory.getLogger(StateTransitionServiceImpl.class);
    private final Random random = new Random();

    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private AssignmentRepository assignmentRepository;

    // 状态顺序（必须与矩阵行/列严格对应）
    private static final List<VehicleStatus> STATES = List.of(
            VehicleStatus.IDLE,
            VehicleStatus.ORDER_DRIVING,
            VehicleStatus.LOADING,
            VehicleStatus.TRANSPORT_DRIVING,
            VehicleStatus.UNLOADING,
            VehicleStatus.WAITING,
            VehicleStatus.BREAKDOWN
    );

    private double[][] transitionMatrix;

    @PostConstruct
    public void initTransitionMatrix() {
        transitionMatrix = new double[7][7];
        transitionMatrix[0] = new double[]{0.13, 0.55, 0.0,  0.0,   0.0,   0.255, 0.065};
        transitionMatrix[1] = new double[]{0.0,  0.065,0.775, 0.0,   0.0,   0.112, 0.048};
        transitionMatrix[2] = new double[]{0.0,  0.0,  0.08, 0.78,  0.055, 0.043, 0.042};
        transitionMatrix[3] = new double[]{0.0,  0.0,  0.0,  0.09,  0.778, 0.07,  0.062};
        transitionMatrix[4] = new double[]{0.637,0.125,0.0,  0.0,   0.093, 0.083, 0.062};
        transitionMatrix[5] = new double[]{0.0,  0.30, 0.225,0.138, 0.10,  0.195, 0.042};
        transitionMatrix[6] = new double[]{0.212,0.0,  0.0,  0.0,   0.0,   0.0,   0.788};
    }

    /**
     * 核心方法：结合任务上下文选择下一状态
     */
    @Override
    public VehicleStatus selectNextStateWithFullContext(
            VehicleStatus currentStatus, Assignment assignment, Vehicle vehicle) {

        if (assignment == null) {
            return selectNextStateWithMarkovOnly(currentStatus);
        }

        switch (assignment.getStatus()) {
            case ASSIGNED:
                return VehicleStatus.ORDER_DRIVING;

            case IN_PROGRESS:
                Integer idx = assignment.getCurrentActionIndex();
                if (idx != null && assignment.getActionLine() != null && idx < assignment.getActionLine().size()) {
                    Long actionId = assignment.getActionLine().get(idx);
                    if (actionId != null) {
                        long lastDigit = actionId % 10;
                        if (lastDigit == 1) return VehicleStatus.LOADING;
                        if (lastDigit == 2) return VehicleStatus.TRANSPORT_DRIVING;
                        if (lastDigit == 3) return VehicleStatus.UNLOADING;
                        if (lastDigit == 4) return VehicleStatus.WAITING;
                        if (lastDigit == 5) return VehicleStatus.BREAKDOWN;
                    }
                }
                return VehicleStatus.TRANSPORT_DRIVING;

            case COMPLETED:
            case CANCELLED:
                return VehicleStatus.IDLE;

            default:
                return selectNextStateWithMarkovOnly(currentStatus);
        }
    }

    /**
     * 纯马尔科夫链转移（无任务时使用）
     */
    private VehicleStatus selectNextStateWithMarkovOnly(VehicleStatus currentStatus) {
        int idx = STATES.indexOf(currentStatus);
        if (idx == -1) return VehicleStatus.IDLE;

        double[] probs = transitionMatrix[idx];
        double rand = random.nextDouble();
        double sum = 0.0;
        for (int i = 0; i < probs.length; i++) {
            sum += probs[i];
            if (rand < sum) {
                return STATES.get(i);
            }
        }
        return STATES.get(probs.length - 1);
    }

    @Override
    public VehicleStatus selectNextState(VehicleStatus currentStatus) {
        return selectNextStateWithMarkovOnly(currentStatus);
    }

    @Override
    public Map<Long, VehicleStatus> batchSelectNextState(Map<Long, VehicleStatus> currentStates) {
        Map<Long, VehicleStatus> result = new HashMap<>();
        for (Map.Entry<Long, VehicleStatus> entry : currentStates.entrySet()) {
            result.put(entry.getKey(), selectNextState(entry.getValue()));
        }
        return result;
    }

    @Override
    public VehicleStatus selectNextStateWithMarkov(
            VehicleStatus currentStatus,
            Map<VehicleStatus, Map<VehicleStatus, Double>> markovMatrix) {
        // 你现在用的是 transitionMatrix，因此忽略参数 markovMatrix
        return selectNextStateWithMarkovOnly(currentStatus);
    }

    /**
     * 当一个状态“结束”时推进任务阶段 / actionIndex
     */
    private void onStateFinished(Vehicle vehicle, Assignment assignment, LocalDateTime simNow) {
        if (assignment == null) {
            logger.info("[任务推进] vehicle={} 无任务，跳过", vehicle.getLicensePlate());
            return;
        }

        logger.info("[任务推进-进入] vehicle={} vStatus={} aId={} aStatus={} actionIdx={} lineSize={} simNow={}",
                vehicle.getLicensePlate(),
                vehicle.getCurrentStatus(),
                assignment.getId(),
                assignment.getStatus(),
                assignment.getCurrentActionIndex(),
                (assignment.getActionLine() == null ? 0 : assignment.getActionLine().size()),
                simNow
        );

        // ASSIGNED -> IN_PROGRESS：当 ORDER_DRIVING 结束时，任务进入运输流程
        if (assignment.getStatus() == AssignmentStatus.ASSIGNED) {
            if (vehicle.getCurrentStatus() == VehicleStatus.ORDER_DRIVING) {
                assignment.setStatus(AssignmentStatus.IN_PROGRESS);

                if (assignment.getCurrentActionIndex() == null) {
                    assignment.setCurrentActionIndex(0);
                }
                if (assignment.getStartTime() == null) {
                    assignment.setStartTime(simNow);
                }

                assignmentRepository.save(assignment);

                logger.info("[任务推进] aId={} ASSIGNED → IN_PROGRESS, actionIdx={}, startTime={}",
                        assignment.getId(),
                        assignment.getCurrentActionIndex(),
                        assignment.getStartTime()
                );
            }
            return;
        }

        // IN_PROGRESS：完成一次动作状态后推进 actionIndex
        if (assignment.getStatus() == AssignmentStatus.IN_PROGRESS) {
            List<Long> line = assignment.getActionLine();
            if (line == null || line.isEmpty()) {
                logger.info("[任务推进-跳过] assignmentId={} actionLine empty, do nothing", assignment.getId());
                return;
            }

            VehicleStatus s = vehicle.getCurrentStatus();
            boolean actionLikeState =
                    s == VehicleStatus.LOADING ||
                            s == VehicleStatus.TRANSPORT_DRIVING ||
                            s == VehicleStatus.UNLOADING ||
                            s == VehicleStatus.WAITING ||
                            s == VehicleStatus.BREAKDOWN;

            if (actionLikeState) {
                Integer before = assignment.getCurrentActionIndex();
                logger.info("[任务推进-触发] assignmentId={} moveToNextAction beforeIndex={} simNow={} (vehicleStatus={})",
                        assignment.getId(), before, simNow, s);

                assignment.moveToNextAction(simNow);
                assignmentRepository.save(assignment);

                logger.info("[任务推进] aId={} actionIdx {} → {} (simNow={})",
                        assignment.getId(),
                        before,
                        assignment.getCurrentActionIndex(),
                        simNow
                );
            } else {
                logger.info("[任务推进-不推进] assignmentId={} vehicleStatus={} not action-like", assignment.getId(), s);
            }
        }
    }

    /**
     * 计算某状态驻留时间（对齐主循环粒度）
     */
    private Duration calcStayDuration(VehicleStatus status, Assignment assignment, Vehicle vehicle, int minutesPerLoop) {
        int minutes;

        switch (status) {
            case ORDER_DRIVING: {
                Integer travel = calcTravelMinutesFromRoute(assignment);
                minutes = (travel != null) ? Math.max(30, (int) Math.ceil(travel * 0.2)) : 60;
                break;
            }
            case LOADING:
                minutes = 30;
                break;

            case TRANSPORT_DRIVING: {
                Integer travel = calcTravelMinutesFromRoute(assignment);
                minutes = (travel != null) ? travel : 120;
                break;
            }

            case UNLOADING:
                minutes = 30;
                break;

            case WAITING:
                minutes = 30;
                break;

            case BREAKDOWN:
                minutes = 120;
                break;

            case IDLE:
            default:
                minutes = 30;
        }

        // 1) 先对齐到主循环粒度（保证至少 1 个 loop）
        int loops = (int) Math.ceil(minutes / (double) minutesPerLoop);
        loops = Math.max(1, loops);
        int alignedMinutes = loops * minutesPerLoop;

        // 2) ✅ 第一种方法：加“最大停留时间上限”，避免 endTime 被拉到很久以后
        // 你可以把 60 改成 90/120，看你希望转移快慢
        int maxStayMinutes = 60;
        alignedMinutes = Math.min(alignedMinutes, maxStayMinutes);

        return Duration.ofMinutes(alignedMinutes);
    }
    @Transactional
    public void resetVehicleStateWindows(LocalDateTime simNow, int minutesPerLoop) {
        List<Vehicle> vehicles = vehicleRepository.findAll();

        for (Vehicle v : vehicles) {

            // 1) 状态为空就初始化
            if (v.getCurrentStatus() == null) {
                v.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
                v.setPreviousStatus(null);
            }

            // 2) 强制把窗口起点挪到现在
            v.setStatusStartTime(simNow);

            // 3) 用你原来的逻辑算驻留时间
            Duration stay = calcStayDuration(
                    v.getCurrentStatus(),
                    v.getCurrentAssignment(),
                    v,
                    minutesPerLoop
            );

            // ✅ 4) 调试阶段：限制最长驻留时间（最多 2 个循环）
            Duration maxStay = Duration.ofMinutes((long) minutesPerLoop * 2);
            if (stay == null || stay.isZero() || stay.isNegative()) {
                stay = Duration.ofMinutes(minutesPerLoop); // 最少 1 个循环
            }
            if (stay.compareTo(maxStay) > 0) {
                stay = maxStay;
            }

            // 5) 写回 duration
            v.setStatusDuration(stay);

            logger.info("RESET窗口: vehicle={} status={} start={} end={}",
                    v.getLicensePlate(),
                    v.getCurrentStatus(),
                    simNow,
                    simNow.plus(stay));
        }

        vehicleRepository.saveAll(vehicles);
        logger.info("✅ 已重置所有车辆状态窗口：vehicles={}, simNow={}, minutesPerLoop={}",
                vehicles.size(), simNow, minutesPerLoop);
    }


    /**
     * route 的 estimatedTime / distance 单位不确定时自适应，避免出现 20+ 小时这种离谱 endTime
     */
    private Integer calcTravelMinutesFromRoute(Assignment assignment) {
        if (assignment == null) return null;

        Route route = assignment.getRoute();
        if (route == null) return null;

        // 1) estimatedTime 优先
        Double est = route.getEstimatedTime();
        if (est != null && est > 0) {
            // A：小时
            if (est <= 24) {
                return Math.max(1, (int) Math.round(est * 60.0));
            }
            // B：分钟
            if (est <= 24 * 60) {
                return Math.max(1, (int) Math.round(est));
            }
            logger.warn("[Route耗时] estimatedTime={} 过大，忽略该字段，routeId={}", est, route.getId());
        }

        // 2) distance 估算
        Double dist = route.getDistance();
        if (dist != null && dist > 0) {
            // 如果像“米”
            if (dist > 2000) dist = dist / 1000.0;

            double speedKmph = 40.0;
            int mins = (int) Math.round((dist / speedKmph) * 60.0);
            return Math.max(1, mins);
        }

        return null;
    }

    /**
     * 单辆车状态更新
     */
    @Override
    @Transactional
    public void updateVehicleStateWithContext(Vehicle vehicle, LocalDateTime simNow, int minutesPerLoop) {
        if (vehicle == null) return;

        // 0) 初始化状态
        if (vehicle.getCurrentStatus() == null) {
            vehicle.setCurrentStatus(VehicleStatus.IDLE);
            vehicle.setPreviousStatus(null);
            vehicle.setStatusStartTime(simNow);
            vehicle.setStatusDuration(calcStayDuration(VehicleStatus.IDLE, null, vehicle, minutesPerLoop));
            vehicleRepository.save(vehicle);
            return;
        }

        if (vehicle.getStatusStartTime() == null) {
            vehicle.setStatusStartTime(simNow);
            vehicle.setStatusDuration(calcStayDuration(vehicle.getCurrentStatus(), vehicle.getCurrentAssignment(), vehicle, minutesPerLoop));
            vehicleRepository.save(vehicle);
            return;
        }

        // ✅ 1) 强制截断历史遗留的超长驻留时间（关键修复）
        // 你可以把 60 改成 30（更快看到状态更新），或者 120（更接近现实）
        Duration maxDur = Duration.ofMinutes(60);

        Duration curDur = vehicle.getStatusDuration();
        if (curDur != null && curDur.compareTo(maxDur) > 0) {
            vehicle.setStatusDuration(maxDur);
            vehicleRepository.save(vehicle);

            logger.warn("车辆[{}] 检测到超长驻留，已截断: {} -> {} (status={}, startTime={})",
                    vehicle.getLicensePlate(),
                    curDur,
                    maxDur,
                    vehicle.getCurrentStatus(),
                    vehicle.getStatusStartTime());
        }

        // 2) 时间门槛：没到 endTime 不允许转移
        LocalDateTime endTime = vehicle.getStatusEndTime();
        if (endTime != null && simNow.isBefore(endTime)) {
            logger.info("车辆[{}] 未到转移时间：current={} simNow={} endTime={}",
                    vehicle.getLicensePlate(),
                    vehicle.getCurrentStatus(),
                    simNow,
                    endTime);
            return;
        }

        // 3) 获取任务上下文
        Assignment assignment = vehicle.getCurrentAssignment();

        // 4) 当前状态结束时推进任务
        onStateFinished(vehicle, assignment, simNow);

        // 5) 选择下一个状态
        VehicleStatus current = vehicle.getCurrentStatus();
        VehicleStatus next = selectNextStateWithFullContext(current, assignment, vehicle);
        if (next == null) next = VehicleStatus.IDLE;

        // 6) 计算 next 驻留时间
        Duration stay = calcStayDuration(next, assignment, vehicle, minutesPerLoop);

        // 7) 写入状态
        if (next != current) {
            vehicle.setPreviousStatus(current);
            vehicle.setCurrentStatus(next);
            logger.info("车辆[{}] 状态更新: {} → {} (simNow={}, until={})",
                    vehicle.getLicensePlate(),
                    current,
                    next,
                    simNow,
                    simNow.plus(stay));
        }

        // 即使状态没变，也刷新驻留窗口
        vehicle.setStatusStartTime(simNow);
        vehicle.setStatusDuration(stay);

        vehicleRepository.save(vehicle);
    }


    /**
     * 批量更新所有车辆状态
     */
    @Transactional
    @Override
    public void batchUpdateAllVehicleStates(LocalDateTime simNow, int minutesPerLoop) {

        System.out.println("=== 开始批量更新所有车辆状态 ===");
        List<Vehicle> vehicles = vehicleRepository.findAll();
        System.out.println("当前车辆数量: " + vehicles.size());

        for (Vehicle vehicle : vehicles) {
            updateVehicleStateWithContext(vehicle, simNow, minutesPerLoop);
        }

        System.out.println("=== 批量更新完成 ===");
    }
}
