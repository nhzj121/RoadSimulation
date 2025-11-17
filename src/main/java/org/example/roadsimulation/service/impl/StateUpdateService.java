// StateUpdateService.java - 状态更新服务
package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.core.SimulationTime;
import org.example.roadsimulation.core.TimeEventScheduler;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 状态更新服务
 * 负责在模拟时间推进时更新实体状态
 */
@Service
public class StateUpdateService implements TimeEventScheduler.TimeEventListener {

    private final SimulationTime simulationTime;
    private final TimeEventScheduler eventScheduler;
    private final StateTransitionServiceImpl stateTransitionService;
    private final VehicleRepository vehicleRepository;
    private final AssignmentRepository assignmentRepository;

    @Autowired
    public StateUpdateService(StateTransitionServiceImpl stateTransitionService,
                              VehicleRepository vehicleRepository,
                              AssignmentRepository assignmentRepository) {
        this.simulationTime = new SimulationTime(LocalDateTime.now(), 1.0);
        this.eventScheduler = new TimeEventScheduler(simulationTime);
        this.stateTransitionService = stateTransitionService;
        this.vehicleRepository = vehicleRepository;
        this.assignmentRepository = assignmentRepository;

        // 注册自己作为事件监听器
        eventScheduler.addListener(this);

        // 启动状态更新周期任务
        scheduleStateUpdates();
    }

    /**
     * 安排周期性的状态更新
     */
    private void scheduleStateUpdates() {
        // 每模拟5分钟更新一次车辆状态
        eventScheduler.scheduleRelativeEvent(5, this::updateVehicleStates, "vehicle_state_update");

        // 每模拟10分钟更新一次任务状态
        eventScheduler.scheduleRelativeEvent(10, this::updateAssignmentStates, "assignment_state_update");

        // 每模拟30分钟检查超期任务
        eventScheduler.scheduleRelativeEvent(30, this::checkOverdueAssignments, "overdue_assignment_check");
    }

    /**
     * 更新所有车辆状态
     */
    public void updateVehicleStates() {
        List<Vehicle> vehicles = vehicleRepository.findAll();

        for (Vehicle vehicle : vehicles) {
            try {
                Vehicle.VehicleStatus nextStatus = stateTransitionService.selectNextStateWithContext(vehicle);

                if (nextStatus != vehicle.getCurrentStatus()) {
                    // 记录状态变更
                    System.out.printf("时间: %s, 车辆: %s, 状态变更: %s -> %s%n",
                            simulationTime.getCurrentTime(),
                            vehicle.getLicensePlate(),
                            vehicle.getCurrentStatus(),
                            nextStatus);

                    vehicle.setCurrentStatus(nextStatus);
                    vehicleRepository.save(vehicle);
                }
            } catch (Exception e) {
                System.err.println("更新车辆状态失败: " + vehicle.getId() + ", 错误: " + e.getMessage());
            }
        }

        // 重新安排下一次更新
        eventScheduler.scheduleRelativeEvent(5, this::updateVehicleStates, "vehicle_state_update");
    }

    /**
     * 更新任务分配状态
     */
    public void updateAssignmentStates() {
        List<Assignment> assignments = assignmentRepository.findByStatusIn(
                List.of(Assignment.AssignmentStatus.ASSIGNED, Assignment.AssignmentStatus.IN_PROGRESS)
        );

        for (Assignment assignment : assignments) {
            updateAssignmentProgress(assignment);
        }

        // 重新安排下一次更新
        eventScheduler.scheduleRelativeEvent(10, this::updateAssignmentStates, "assignment_state_update");
    }

    /**
     * 检查超期任务
     */
    public void checkOverdueAssignments() {
        LocalDateTime now = simulationTime.getCurrentTime();
        List<Assignment> overdueAssignments = assignmentRepository.findOverdueAssignments(now);

        for (Assignment assignment : overdueAssignments) {
            System.out.printf("时间: %s, 任务超期: %s, 预计完成: %s%n",
                    now, assignment.getId(), assignment.getEndTime());

            // 可以在这里触发通知或其他处理逻辑
        }

        // 重新安排下一次检查
        eventScheduler.scheduleRelativeEvent(30, this::checkOverdueAssignments, "overdue_assignment_check");
    }

    /**
     * 更新单个任务进度
     */
    private void updateAssignmentProgress(Assignment assignment) {
        if (assignment.isInProgress() && assignment.getCurrentActionIndex() != null) {
            // 模拟任务进度推进
            // 这里可以根据业务逻辑更新任务进度

            // 示例：随机决定是否推进到下一个动作
            if (Math.random() > 0.7) { // 30%概率推进
                assignment.moveToNextAction();
                assignmentRepository.save(assignment);

                System.out.printf("时间: %s, 任务: %s, 推进到动作索引: %d%n",
                        simulationTime.getCurrentTime(),
                        assignment.getId(),
                        assignment.getCurrentActionIndex());
            }
        }
    }

    /**
     * 推进模拟时间
     */
    public void advanceTime(long milliseconds) {
        simulationTime.advanceTime(milliseconds);
        eventScheduler.processDueEvents();
    }

    /**
     * 启动模拟
     */
    public void startSimulation() {
        simulationTime.resume();
        System.out.println("模拟开始: " + simulationTime.getCurrentTime());
    }

    /**
     * 暂停模拟
     */
    public void pauseSimulation() {
        simulationTime.pause();
        System.out.println("模拟暂停: " + simulationTime.getCurrentTime());
    }

    /**
     * 设置时间缩放
     */
    public void setTimeScale(double scale) {
        simulationTime.setTimeScale(scale);
        System.out.println("时间缩放设置为: " + scale + "x");
    }

    // 实现 TimeEventListener 接口
    @Override
    public void onEventExecuted(TimeEventScheduler.TimeEvent event) {
        System.out.printf("时间: %s, 事件执行: %s%n",
                simulationTime.getCurrentTime(), event.getEventId());
    }

    @Override
    public void onEventScheduled(TimeEventScheduler.TimeEvent event) {
        System.out.printf("时间: %s, 事件安排: %s, 计划时间: %s%n",
                simulationTime.getCurrentTime(),
                event.getEventId(),
                event.getScheduledTime());
    }

    // Getter 方法
    public SimulationTime getSimulationTime() {
        return simulationTime;
    }

    public TimeEventScheduler getEventScheduler() {
        return eventScheduler;
    }
}