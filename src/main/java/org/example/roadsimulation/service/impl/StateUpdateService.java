package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.core.SimulationTime;
import org.example.roadsimulation.core.TimeEventScheduler;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Vehicle.VehicleStatus;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.StateTransitionService;  // 使用接口
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 状态更新服务（最终完整版：修复构造函数注入问题）
 */
@Service
public class StateUpdateService implements TimeEventScheduler.TimeEventListener {

    private final SimulationTime simulationTime;
    private final TimeEventScheduler eventScheduler;
    private final StateTransitionService stateTransitionService;  // 使用接口
    private final VehicleRepository vehicleRepository;
    private final AssignmentRepository assignmentRepository;
    private final Random random = new Random();

    /**
     * 构造函数注入（唯一方式，删除所有字段上的 @Autowired）
     */
    @Autowired
    public StateUpdateService(StateTransitionService stateTransitionService,
                              VehicleRepository vehicleRepository,
                              AssignmentRepository assignmentRepository) {
        this.stateTransitionService = stateTransitionService;
        this.vehicleRepository = vehicleRepository;
        this.assignmentRepository = assignmentRepository;

        this.simulationTime = new SimulationTime(LocalDateTime.now(), 1.0);
        this.eventScheduler = new TimeEventScheduler(simulationTime);

        eventScheduler.addListener(this);
        scheduleStateUpdates();

        // 启动后30分钟开始第一次状态统计，以后每30分钟一次
        eventScheduler.scheduleRelativeEvent(30, this::printStateStatistics, "state_statistics");
    }

    /**
     * 打印所有车辆当前状态分布统计（每30分钟模拟时间执行一次）
     */
    private void printStateStatistics() {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        Map<VehicleStatus, Long> stats = vehicles.stream()
                .collect(Collectors.groupingBy(Vehicle::getCurrentStatus, Collectors.counting()));

        System.out.printf("[状态统计] 时间: %s | 车辆总数: %d | 分布: %s%n",
                simulationTime.getCurrentTime(),
                vehicles.size(),
                stats);
    }

    private void scheduleStateUpdates() {
        eventScheduler.scheduleRelativeEvent(1, this::updateVehicleStates, "vehicle_state_update");
    }

    public void updateVehicleStates() {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        LocalDateTime now = simulationTime.getCurrentTime();

        for (Vehicle vehicle : vehicles) {
            try {
                Assignment assignment = getCurrentAssignment(vehicle);

                if (vehicle.getStatusStartTime() == null) {
                    vehicle.setStatusStartTime(now);
                    vehicleRepository.save(vehicle);
                    continue;
                }

                long minutesInCurrentState = Duration.between(vehicle.getStatusStartTime(), now).toMinutes();

                if (shouldEndCurrentState(vehicle.getCurrentStatus(), minutesInCurrentState)) {
                    VehicleStatus nextStatus = stateTransitionService.selectNextStateWithFullContext(
                            vehicle.getCurrentStatus(), assignment, vehicle);

                    if (nextStatus != vehicle.getCurrentStatus()) {
                        String taskInfo = assignment != null ? "是(ID=" + assignment.getId() + ")" : "否";

                        System.out.printf("[状态转移] 时间: %s | 车辆: %s | %s → %s | 持续: %d分 | 有任务: %s%n",
                                now,
                                vehicle.getLicensePlate(),
                                vehicle.getCurrentStatus(),
                                nextStatus,
                                minutesInCurrentState,
                                taskInfo);

                        vehicle.setCurrentStatus(nextStatus);
                        vehicle.setStatusStartTime(now);
                        vehicleRepository.save(vehicle);
                    }
                }
            } catch (Exception e) {
                System.err.println("更新车辆状态失败: " + (vehicle != null ? vehicle.getId() : "null") + ", 错误: " + e.getMessage());
            }
        }

        // 重新调度下次状态更新（每1分钟模拟时间）
        eventScheduler.scheduleRelativeEvent(1, this::updateVehicleStates, "vehicle_state_update");

        // 每30分钟再次调度状态统计（形成循环）
        eventScheduler.scheduleRelativeEvent(30, this::printStateStatistics, "state_statistics");
    }

    private boolean shouldEndCurrentState(VehicleStatus status, long minutesInCurrentState) {
        return switch (status) {
            case IDLE -> minutesInCurrentState >= 10;
            case ORDER_DRIVING -> minutesInCurrentState >= randomRange(5, 15);
            case LOADING -> minutesInCurrentState >= randomRange(10, 30);
            case TRANSPORT_DRIVING -> minutesInCurrentState >= randomRange(60, 300);
            case UNLOADING -> minutesInCurrentState >= randomRange(10, 30);
            case WAITING -> minutesInCurrentState >= randomRange(5, 20);
            case BREAKDOWN -> minutesInCurrentState >= randomRange(30, 120);
            default -> false;
        };
    }

    private int randomRange(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private Assignment getCurrentAssignment(Vehicle vehicle) {
        return assignmentRepository.findAll().stream()
                .filter(a -> a.getAssignedVehicle() != null && a.getAssignedVehicle().equals(vehicle))
                .filter(a -> a.getStatus() == AssignmentStatus.ASSIGNED || a.getStatus() == AssignmentStatus.IN_PROGRESS)
                .findFirst()
                .orElse(null);
    }

    // ================ 仿真控制方法 ================
    public void startSimulation() {
        simulationTime.resume();
        System.out.println("模拟已启动: " + simulationTime.getCurrentTime());
    }

    public void pauseSimulation() {
        simulationTime.pause();
        System.out.println("模拟已暂停: " + simulationTime.getCurrentTime());
    }

    public void setTimeScale(double scale) {
        simulationTime.setTimeScale(scale);
        System.out.println("时间缩放设置为: " + scale + "x");
    }

    public void advanceTime(long milliseconds) {
        simulationTime.advanceTime(milliseconds);
        eventScheduler.processDueEvents();
        System.out.println("时间推进 " + milliseconds + " 毫秒，当前时间: " + simulationTime.getCurrentTime());

//        // 新增：确认批量更新被触发
//        System.out.println(">>> 触发车辆状态批量更新 <<<");
//        if (stateTransitionService instanceof StateTransitionServiceImpl impl) {
//            impl.batchUpdateAllVehicleStates();
//        }
    }

    // =====================================================================

    @Override
    public void onEventExecuted(TimeEventScheduler.TimeEvent event) {
        System.out.printf("时间: %s, 事件执行: %s%n", simulationTime.getCurrentTime(), event.getEventId());
    }

    @Override
    public void onEventScheduled(TimeEventScheduler.TimeEvent event) {
        System.out.printf("时间: %s, 事件安排: %s, 计划时间: %s%n", simulationTime.getCurrentTime(), event.getEventId(), event.getScheduledTime());
    }

    public SimulationTime getSimulationTime() {
        return simulationTime;
    }

    public TimeEventScheduler getEventScheduler() {
        return eventScheduler;
    }
}