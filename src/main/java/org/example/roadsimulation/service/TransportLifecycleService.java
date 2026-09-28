package org.example.roadsimulation.service;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.AssignmentDriverHistory;
import org.example.roadsimulation.entity.AssignmentLeg;
import org.example.roadsimulation.entity.AssignmentNode;
import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.AssignmentDriverHistoryRepository;
import org.example.roadsimulation.repository.AssignmentRepository;
import org.example.roadsimulation.repository.DriverRepository;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class TransportLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(TransportLifecycleService.class);

    // Phase 1：兼容前端的状态窗口也引用唯一后端 tick；它仍不改变当前前端完成权，留待 Phase 4。
    private static final Duration FRONTEND_ORDER_DRIVING_WINDOW = SimulationContext.TICK_DURATION;
    // Phase 4：路段完成后的装卸/途经动作至少占用一个完整仿真 tick。
    private static final Duration BACKEND_NODE_ACTION_WINDOW = SimulationContext.TICK_DURATION;

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final AssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final DriverPreferenceScorer driverPreferenceScorer;
    private final AssignmentDriverHistoryRepository driverHistoryRepository;
    // Phase 1：生产环境中缺省业务时间必须回到唯一的 SimulationContext，而不是系统墙上时间。
    private final SimulationContext simulationContext;

    /**
     * Phase 1：保留四参数构造器供现有纯单元测试使用；测试应优先显式传入 simNow。
     */
    public TransportLifecycleService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            AssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository
    ) {
        this(
                shipmentRepository,
                shipmentItemRepository,
                assignmentRepository,
                vehicleRepository,
                null,
                null
        );
    }

    /**
     * Phase 1：Spring 生产构造器注入唯一仿真时钟；不存在前端或系统时间驱动业务状态的入口。
     */
    public TransportLifecycleService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            AssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository,
            SimulationContext simulationContext
    ) {
        this(
                shipmentRepository,
                shipmentItemRepository,
                assignmentRepository,
                vehicleRepository,
                simulationContext,
                null
        );
    }

    /**
     * 司机接入运输链：生产构造器注入 DriverRepository，任务启动/终态时联动司机状态。
     */
    public TransportLifecycleService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            AssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository,
            SimulationContext simulationContext,
            DriverRepository driverRepository
    ) {
        this(shipmentRepository, shipmentItemRepository, assignmentRepository, vehicleRepository,
                simulationContext, driverRepository, null);
    }

    /**
     * 司机偏好：生产构造器注入 DriverPreferenceScorer，绑定司机时按偏好选择。
     */
    public TransportLifecycleService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            AssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository,
            SimulationContext simulationContext,
            DriverRepository driverRepository,
            DriverPreferenceScorer driverPreferenceScorer
    ) {
        this(shipmentRepository, shipmentItemRepository, assignmentRepository, vehicleRepository,
                simulationContext, driverRepository, driverPreferenceScorer, null);
    }

    /**
     * 司机交接历史：生产构造器注入 AssignmentDriverHistoryRepository，绑定/释放/换司机时记录交接链。
     */
    @Autowired
    public TransportLifecycleService(
            ShipmentRepository shipmentRepository,
            ShipmentItemRepository shipmentItemRepository,
            AssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository,
            SimulationContext simulationContext,
            DriverRepository driverRepository,
            DriverPreferenceScorer driverPreferenceScorer,
            AssignmentDriverHistoryRepository driverHistoryRepository
    ) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.simulationContext = simulationContext;
        this.driverRepository = driverRepository;
        this.driverPreferenceScorer = driverPreferenceScorer;
        this.driverHistoryRepository = driverHistoryRepository;
    }

    public record LoadingCompletionResult(
            Long assignmentId,
            Long vehicleId,
            Double currentLoad,
            Double currentVolume
    ) {
    }

    @Transactional
    public Assignment startAssignmentExecution(
            Assignment assignment,
            Vehicle vehicle,
            LocalDateTime simNow,
            String actor
    ) {
        if (assignment == null) {
            return null;
        }
        LocalDateTime now = resolveTime(simNow);
        Vehicle managedVehicle = resolveVehicle(vehicle, assignment);

        assignment.setStatus(Assignment.AssignmentStatus.IN_PROGRESS);
        if (assignment.getStartTime() == null) {
            assignment.setStartTime(now);
        }
        if (assignment.getCurrentActionIndex() == null) {
            assignment.setCurrentActionIndex(0);
        }
        assignment.setUpdatedBy(actor);
        assignment.setUpdatedTime(LocalDateTime.now());

        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || isTerminalItem(item)) {
                continue;
            }
            item.setAssignment(assignment);
            item.setStatus(ShipmentItem.ShipmentItemStatus.ASSIGNED);
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        if (managedVehicle != null) {
            managedVehicle.addAssignment(assignment);
            managedVehicle.transitionToStatus(
                    Vehicle.VehicleStatus.ORDER_DRIVING,
                    now,
                    FRONTEND_ORDER_DRIVING_WINDOW
            );
            // Phase 1：运输生命周期中的车辆载重统一通过吨制入口写入。
            managedVehicle.setCurrentLoadTonnes(0.0);
            managedVehicle.setCurrentVolumn(0.0);
            managedVehicle.setUpdatedBy(actor);
            managedVehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(managedVehicle);
        }

        bindDriverIfPossible(assignment, managedVehicle, actor);

        Assignment saved = assignmentRepository.save(assignment);
        refreshShipments(touchedShipments);
        return saved;
    }

    @Transactional
    public void markLoadingCompleted(Assignment assignment, LocalDateTime simNow, String actor) {
        if (!isActiveAssignment(assignment)) {
            return;
        }
        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || isTerminalItem(item)) {
                continue;
            }
            if (item.getStatus() == ShipmentItem.ShipmentItemStatus.ASSIGNED) {
                item.setStatus(ShipmentItem.ShipmentItemStatus.LOADED);
                item.setUpdatedBy(actor);
                item.setUpdatedTime(LocalDateTime.now());
                shipmentItemRepository.save(item);
            }
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }
        refreshShipments(touchedShipments);
        syncVehicleRuntimeLoad(assignment, null, actor);
    }

    @Transactional
    public LoadingCompletionResult markFrontendLoadingCompleted(
            Long assignmentId,
            Long vehicleId,
            LocalDateTime simNow,
            String actor
    ) {
        if (assignmentId == null) {
            throw new IllegalArgumentException("assignmentId is required");
        }
        if (vehicleId == null) {
            throw new IllegalArgumentException("vehicleId is required");
        }

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        if (isClosedAssignment(assignment)) {
            throw new IllegalStateException("Assignment is closed: " + assignmentId);
        }
        if (hasNodes(assignment)) {
            throw new IllegalArgumentException("VRP assignments are not supported by assignment-loaded");
        }

        Vehicle managedVehicle = resolveVehicle(null, assignment);
        if (managedVehicle == null || managedVehicle.getId() == null) {
            throw new IllegalStateException("No vehicle assigned to assignment: " + assignmentId);
        }
        if (!vehicleId.equals(managedVehicle.getId())) {
            throw new IllegalArgumentException("Vehicle does not match assignment: " + vehicleId);
        }

        LocalDateTime now = resolveTime(simNow);
        String effectiveActor = actor != null ? actor : "Frontend loading completion";

        if (assignment.getStatus() == Assignment.AssignmentStatus.ASSIGNED) {
            assignment = startAssignmentExecution(assignment, managedVehicle, now, effectiveActor);
            managedVehicle = resolveVehicle(managedVehicle, assignment);
        }
        if (!isActiveAssignment(assignment)) {
            throw new IllegalStateException("Assignment is not active: " + assignment.getStatus());
        }

        Set<ShipmentItem> items = getAssignmentItems(assignment);
        if (items.isEmpty()) {
            throw new IllegalStateException("Assignment has no shipment items: " + assignmentId);
        }

        boolean shouldCompleteLoading = hasAssignedItems(items);
        if (shouldCompleteLoading) {
            markLoadingCompleted(assignment, now, effectiveActor);
            assignment.moveToNextAction(now);
            assignment.setUpdatedBy(effectiveActor);
            assignment.setUpdatedTime(LocalDateTime.now());
            assignmentRepository.save(assignment);
        } else {
            syncVehicleRuntimeLoad(assignment, managedVehicle, effectiveActor);
        }

        managedVehicle = resolveVehicle(managedVehicle, assignment);
        if (managedVehicle != null && shouldMarkTransportDriving(managedVehicle)) {
            managedVehicle.transitionToStatus(Vehicle.VehicleStatus.TRANSPORT_DRIVING, now, Duration.ofMinutes(30));
            managedVehicle.setUpdatedBy(effectiveActor);
            managedVehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(managedVehicle);
        }

        return new LoadingCompletionResult(
                assignment.getId(),
                managedVehicle != null ? managedVehicle.getId() : vehicleId,
                // Phase 1：前端兼容响应数值仍不变，但来源明确为吨制运行时载重。
                managedVehicle != null ? safe(managedVehicle.getCurrentLoadTonnes()) : 0.0,
                managedVehicle != null ? safe(managedVehicle.getCurrentVolumn()) : 0.0
        );
    }

    @Transactional
    public void markTransportStarted(Assignment assignment, LocalDateTime simNow, String actor) {
        if (!isActiveAssignment(assignment)) {
            return;
        }
        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || isTerminalItem(item)) {
                continue;
            }
            // Phase 4 修复：VRP 后续提货点的 ASSIGNED 货物尚未上车，不能因另一票货物出发而提前进入运输中。
            // 普通任务保留旧 ASSIGNED 兼容入口；节点任务只有已完成装货的 LOADED 项可以转为 IN_TRANSIT。
            if (item.getStatus() == ShipmentItem.ShipmentItemStatus.LOADED
                    || (!hasNodes(assignment) && item.getStatus() == ShipmentItem.ShipmentItemStatus.ASSIGNED)) {
                item.setStatus(ShipmentItem.ShipmentItemStatus.IN_TRANSIT);
                item.setUpdatedBy(actor);
                item.setUpdatedTime(LocalDateTime.now());
                shipmentItemRepository.save(item);
            }
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }
        refreshShipments(touchedShipments);
        syncVehicleRuntimeLoad(assignment, null, actor);
    }

    @Transactional
    public void markCurrentNodeCompleted(
            Assignment assignment,
            Vehicle vehicle,
            LocalDateTime simNow,
            String actor
    ) {
        if (!isActiveAssignment(assignment) || !hasNodes(assignment)) {
            return;
        }

        LocalDateTime now = resolveTime(simNow);
        AssignmentNode node = resolveCurrentNode(assignment);
        if (node == null || node.isCompleted()) {
            return;
        }

        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        ShipmentItem item = node.getShipmentItem();
        if (item != null && item.getStatus() != ShipmentItem.ShipmentItemStatus.CANCELLED) {
            if (node.getActionType() == AssignmentNode.NodeActionType.LOAD) {
                item.setAssignment(assignment);
                item.setStatus(ShipmentItem.ShipmentItemStatus.LOADED);
            } else if (node.getActionType() == AssignmentNode.NodeActionType.UNLOAD) {
                item.setStatus(ShipmentItem.ShipmentItemStatus.DELIVERED);
            }
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        node.setCompleted(true);
        node.setActualArrivalTime(now);
        advanceNodeIndex(assignment);
        assignment.setUpdatedBy(actor);
        assignment.setUpdatedTime(LocalDateTime.now());
        assignmentRepository.save(assignment);

        syncVehicleRuntimeLoad(assignment, vehicle, actor);
        refreshShipments(touchedShipments);
    }

    @Transactional
    public void syncVehicleRuntimeLoad(Assignment assignment, Vehicle vehicle, String actor) {
        if (assignment == null) {
            return;
        }
        Vehicle managedVehicle = resolveVehicle(vehicle, assignment);
        if (managedVehicle == null) {
            return;
        }

        double[] loadAndVolume = hasNodes(assignment)
                ? calculateCompletedNodeLoad(assignment)
                : calculateLoadedItemLoad(assignment);

        // Phase 1：loadAndVolume[0] 的运输语义固定为吨，写入明确的吨制入口。
        managedVehicle.setCurrentLoadTonnes(loadAndVolume[0]);
        managedVehicle.setCurrentVolumn(loadAndVolume[1]);
        managedVehicle.setUpdatedBy(actor);
        managedVehicle.setUpdatedTime(LocalDateTime.now());
        vehicleRepository.save(managedVehicle);
    }

    public boolean hasPendingNodes(Assignment assignment) {
        return resolveCurrentNode(assignment) != null;
    }

    /**
     * 司机接入运输链：任务启动时绑定司机。
     * 优先沿用任务预设的司机；否则从车辆 driver_vehicle 关联司机中选空闲（IDLE）者。
     * 找不到空闲司机时不阻断任务执行（任务无司机照常运行），OFF 司机不参与。
     */
    private void bindDriverIfPossible(Assignment assignment, Vehicle vehicle, String actor) {
        if (driverRepository == null || assignment == null || vehicle == null || vehicle.getId() == null) {
            return;
        }
        Driver driver;
        if (assignment.getAssignedDriver() != null && assignment.getAssignedDriver().getId() != null) {
            driver = driverRepository.findById(assignment.getAssignedDriver().getId()).orElse(null);
            // 拒单中/保养中/下线司机不可绑定（ASSIGNED 放行，兼容同司机多任务）
            if (driver == null
                    || driver.getCurrentStatus() == Driver.DriverStatus.OFF
                    || driver.getCurrentStatus() == Driver.DriverStatus.REJECTING
                    || driver.getCurrentStatus() == Driver.DriverStatus.MAINTENANCE) {
                return;
            }
        } else {
            driver = selectIdleDriver(assignment, vehicle, null);
            if (driver == null) {
                return;
            }
        }
        Driver.DriverStatus beforeStatus = driver.getCurrentStatus();
        driver.setCurrentStatus(Driver.DriverStatus.ASSIGNED);
        driver.setUpdatedBy(actor);
        driver.setUpdatedTime(LocalDateTime.now());
        driver.addAssignment(assignment);
        driverRepository.save(driver);
        recordDriverHistory(assignment, driver, AssignmentDriverHistory.Action.BIND,
                "任务启动绑定", actor, beforeStatus, Driver.DriverStatus.ASSIGNED);
    }

    /**
     * 从任务车辆的空闲司机中选最优（多名时按对首个运单项的偏好得分），
     * exclude 用于换司机时排除原司机；无候选返回 null。
     */
    private Driver selectIdleDriver(Assignment assignment, Vehicle vehicle, Driver exclude) {
        if (vehicle == null || vehicle.getDrivers() == null || vehicle.getDrivers().isEmpty()) {
            return null;
        }
        List<Driver> idleDrivers = vehicle.getDrivers().stream()
                .filter(d -> d != null && d.getCurrentStatus() == Driver.DriverStatus.IDLE)
                .filter(d -> exclude == null || d.getId() == null || !d.getId().equals(exclude.getId()))
                .toList();
        if (idleDrivers.isEmpty()) {
            return null;
        }
        if (driverPreferenceScorer == null || idleDrivers.size() == 1) {
            return idleDrivers.get(0);
        }
        // 司机偏好：多名空闲司机时按对首个运单项的偏好得分选择
        ShipmentItem scoringItem = getAssignmentItems(assignment).stream().findFirst().orElse(null);
        return idleDrivers.stream()
                .max(Comparator.comparingDouble(d -> driverPreferenceScorer.scoreFor(d, scoringItem)))
                .orElse(idleDrivers.get(0));
    }

    /**
     * 司机接入运输链：任务进入终态（完成/取消/失败）后释放司机。
     * 保留 assignedDriver 历史归属；仅当司机没有其他进行中任务时恢复空闲。
     */
    private void releaseDriverIfIdle(Assignment assignment, String actor) {
        if (driverRepository == null || assignment == null
                || assignment.getAssignedDriver() == null
                || assignment.getAssignedDriver().getId() == null) {
            return;
        }
        Driver driver = driverRepository.findById(assignment.getAssignedDriver().getId()).orElse(null);
        // 仅任务中司机可释放；防止覆盖行为状态表新状态（REJECTING/MAINTENANCE）
        if (driver == null
                || driver.getCurrentStatus() != Driver.DriverStatus.ASSIGNED
                || driver.getCurrentAssignment() != null) {
            return;
        }
        driver.setCurrentStatus(Driver.DriverStatus.IDLE);
        driver.setUpdatedBy(actor);
        driver.setUpdatedTime(LocalDateTime.now());
        driverRepository.save(driver);
        recordDriverHistory(assignment, driver, AssignmentDriverHistory.Action.RELEASE,
                actor, actor, Driver.DriverStatus.ASSIGNED, Driver.DriverStatus.IDLE);
    }

    /**
     * 手动 PATCH 触发换司机：司机状态离开 ASSIGNED 且仍持有未完成任务时，
     * 从任务车辆的空闲司机池选替补接手；无替补时任务继续无司机运行
     * （与 bindDriverIfPossible 的容忍策略一致）。
     * 返回是否有任务完成了换绑。
     */
    @Transactional
    public boolean reassignDriverIfNeeded(Long driverId, String reason) {
        if (driverRepository == null || driverId == null) {
            return false;
        }
        Driver oldDriver = driverRepository.findById(driverId).orElse(null);
        if (oldDriver == null || oldDriver.getCurrentStatus() == Driver.DriverStatus.ASSIGNED) {
            return false;
        }
        List<Assignment> openAssignments = oldDriver.getAssignments().stream()
                .filter(a -> a != null && !a.isCompleted() && !a.isCancelled()
                        && a.getStatus() != Assignment.AssignmentStatus.FAILED)
                .toList();
        if (openAssignments.isEmpty()) {
            return false;
        }
        boolean replaced = false;
        for (Assignment assignment : openAssignments) {
            if (replaceDriverForAssignment(oldDriver, assignment, reason)) {
                replaced = true;
            }
        }
        return replaced;
    }

    /**
     * 单个任务的换司机：解绑老司机（保留其新状态，由状态表或人工恢复），
     * 记录 RELEASE 历史行；有替补则绑定并置 ASSIGNED，记录 BIND 历史行。
     */
    private boolean replaceDriverForAssignment(Driver oldDriver, Assignment assignment, String reason) {
        Driver.DriverStatus oldDriverNewStatus = oldDriver.getCurrentStatus();
        Vehicle vehicle = assignment.getAssignedVehicle();
        Driver replacement = selectIdleDriver(assignment, vehicle, oldDriver);

        oldDriver.removeAssignment(assignment);
        driverRepository.save(oldDriver);
        recordDriverHistory(assignment, oldDriver, AssignmentDriverHistory.Action.RELEASE,
                reason, reason, Driver.DriverStatus.ASSIGNED, oldDriverNewStatus);

        if (replacement == null) {
            assignmentRepository.save(assignment);
            logger.warn("司机 {}（{}）离开任务 {}，同车无空闲司机替补，任务继续无司机运行，原因: {}",
                    oldDriver.getId(), oldDriverNewStatus, assignment.getId(), reason);
            return false;
        }

        Driver.DriverStatus beforeStatus = replacement.getCurrentStatus();
        replacement.setCurrentStatus(Driver.DriverStatus.ASSIGNED);
        replacement.setUpdatedBy(reason);
        replacement.setUpdatedTime(LocalDateTime.now());
        replacement.addAssignment(assignment);
        driverRepository.save(replacement);
        recordDriverHistory(assignment, replacement, AssignmentDriverHistory.Action.BIND,
                reason, reason, beforeStatus, Driver.DriverStatus.ASSIGNED);
        assignmentRepository.save(assignment);

        logger.info("任务 {} 换司机：司机 {}（{}）→ 司机 {}（{}），原因: {}",
                assignment.getId(), oldDriver.getId(), oldDriverNewStatus,
                replacement.getId(), beforeStatus, reason);
        return true;
    }

    /**
     * 记录司机-任务交接历史行（append-only），历史仓库未注入时静默跳过。
     */
    private void recordDriverHistory(Assignment assignment, Driver driver,
                                     AssignmentDriverHistory.Action action,
                                     String reason, String actor,
                                     Driver.DriverStatus fromStatus, Driver.DriverStatus toStatus) {
        if (driverHistoryRepository == null || assignment == null || assignment.getId() == null
                || driver == null) {
            return;
        }
        AssignmentDriverHistory history = new AssignmentDriverHistory();
        history.setAssignmentId(assignment.getId());
        history.setDriverId(driver.getId());
        history.setDriverName(driver.getDriverName());
        history.setAction(action);
        history.setReason(reason);
        history.setFromStatus(fromStatus != null ? fromStatus.name() : null);
        history.setToStatus(toStatus != null ? toStatus.name() : null);
        history.setSimTime(simulationContext != null ? simulationContext.getCurrentSimTime() : LocalDateTime.now());
        history.setActor(actor);
        driverHistoryRepository.save(history);
    }

    /**
     * Phase 4：消费唯一的 LegCompleted 事件，把车辆从行驶态转入目标节点动作。
     *
     * <p>这里只处理“到达后做什么”，不再计算路段秒数。最后一段到达也仅进入
     * UNLOADING，Assignment.COMPLETED 必须等后端卸货窗口结束。</p>
     */
    @Transactional
    public void handleLegCompleted(
            Assignment assignment,
            AssignmentLeg leg,
            Vehicle vehicle,
            LocalDateTime nextActionStart,
            boolean allLegsCompleted,
            String actor
    ) {
        if (!isActiveAssignment(assignment) || leg == null
                || leg.getProgressStatus() != AssignmentLeg.ProgressStatus.COMPLETED) {
            return;
        }

        Vehicle managedVehicle = resolveVehicle(vehicle, assignment);
        if (managedVehicle == null) {
            throw new IllegalStateException("Completed leg has no assigned vehicle: assignmentId=" + assignment.getId());
        }

        // Phase 4：到达坐标与路段完成同事务落库，后续前端只需投影后端位置。
        if (leg.getToPOI() != null) {
            managedVehicle.setCurrentPOI(leg.getToPOI());
            managedVehicle.setCurrentLongitude(leg.getToPOI().getLongitude());
            managedVehicle.setCurrentLatitude(leg.getToPOI().getLatitude());
        }

        Vehicle.VehicleStatus nextStatus = resolveArrivalActionStatus(leg, allLegsCompleted);
        LocalDateTime actionStart = resolveTime(nextActionStart);
        managedVehicle.transitionToStatus(nextStatus, actionStart, BACKEND_NODE_ACTION_WINDOW);
        managedVehicle.setUpdatedBy(actor);
        managedVehicle.setUpdatedTime(LocalDateTime.now());
        vehicleRepository.save(managedVehicle);
    }

    /**
     * Phase 4：VRP 优先使用路段目标节点动作；普通两段任务使用“中间到达=装货，最终到达=卸货”。
     */
    private Vehicle.VehicleStatus resolveArrivalActionStatus(AssignmentLeg leg, boolean allLegsCompleted) {
        AssignmentNode toNode = leg.getToNode();
        if (allLegsCompleted) {
            // Phase 4：最终路段必须落到卸货阶段；VRP 若把最终节点规划成 LOAD/PASS_BY，
            // 说明节点链与路段链已经错位，失败封闭比静默完成错误任务更安全。
            if (toNode != null && toNode.getActionType() != null
                    && toNode.getActionType() != AssignmentNode.NodeActionType.UNLOAD) {
                throw new IllegalStateException(
                        "Final assignment leg must target an UNLOAD node: assignmentId="
                                + (leg.getAssignment() == null ? null : leg.getAssignment().getId())
                                + ", legIndex=" + leg.getSequenceIndex()
                                + ", action=" + toNode.getActionType()
                );
            }
            return Vehicle.VehicleStatus.UNLOADING;
        }
        if (toNode != null && toNode.getActionType() != null) {
            return switch (toNode.getActionType()) {
                case LOAD -> Vehicle.VehicleStatus.LOADING;
                case UNLOAD -> Vehicle.VehicleStatus.UNLOADING;
                case PASS_BY -> Vehicle.VehicleStatus.WAITING;
            };
        }
        // Phase 4：普通任务的非最终路段固定视为到达装货点。
        return Vehicle.VehicleStatus.LOADING;
    }

    @Transactional
    public void completeDelivery(
            Assignment assignment,
            Vehicle vehicle,
            POI endPOI,
            LocalDateTime simNow,
            String actor
    ) {
        if (assignment == null || isClosedAssignment(assignment)) {
            return;
        }
        LocalDateTime now = resolveTime(simNow);
        Set<Shipment> touchedShipments = new LinkedHashSet<>();

        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED) {
                continue;
            }
            item.setStatus(ShipmentItem.ShipmentItemStatus.DELIVERED);
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        assignment.setStatus(Assignment.AssignmentStatus.COMPLETED);
        assignment.setEndTime(now);
        assignment.setUpdatedBy(actor);
        assignment.setUpdatedTime(LocalDateTime.now());
        assignmentRepository.save(assignment);

        releaseDriverIfIdle(assignment, actor);

        Vehicle managedVehicle = resolveVehicle(vehicle, assignment);
        if (managedVehicle != null) {
            managedVehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, now, Duration.ZERO);
            if (endPOI != null) {
                managedVehicle.setCurrentPOI(endPOI);
                managedVehicle.setCurrentLongitude(endPOI.getLongitude());
                managedVehicle.setCurrentLatitude(endPOI.getLatitude());
            }
            // Phase 1：交付完成释放车辆时，吨制运行载重归零。
            managedVehicle.setCurrentLoadTonnes(0.0);
            managedVehicle.setCurrentVolumn(0.0);
            managedVehicle.setUpdatedBy(actor);
            managedVehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(managedVehicle);
        }

        refreshShipments(touchedShipments);
    }

    @Transactional
    public void rollbackAssignmentForRetry(
            Assignment assignment,
            Vehicle vehicle,
            Collection<ShipmentItem> items,
            String reason
    ) {
        Set<Shipment> touchedShipments = new LinkedHashSet<>();
        Collection<ShipmentItem> targetItems = items != null ? items : getAssignmentItems(assignment);
        for (ShipmentItem item : targetItems) {
            if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED
                    || item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED) {
                continue;
            }
            item.setAssignment(null);
            item.setStatus(ShipmentItem.ShipmentItemStatus.NOT_ASSIGNED);
            item.setUpdatedBy("Route planning rollback");
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        Vehicle managedVehicle = resolveVehicle(vehicle, assignment);
        if (managedVehicle != null) {
            if (assignment != null) {
                managedVehicle.removeAssignment(assignment);
            }
            managedVehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, resolveTime(null), Duration.ZERO);
            // Phase 1：回滚释放车辆时，吨制运行载重归零。
            managedVehicle.setCurrentLoadTonnes(0.0);
            managedVehicle.setCurrentVolumn(0.0);
            managedVehicle.setUpdatedBy("Route planning rollback");
            managedVehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(managedVehicle);
        }

        if (assignment != null) {
            assignment.setAssignedVehicle(null);
            assignment.setStatus(Assignment.AssignmentStatus.FAILED);
            assignment.setUpdatedBy(reason);
            assignment.setUpdatedTime(LocalDateTime.now());
            assignmentRepository.save(assignment);

            releaseDriverIfIdle(assignment, "Route planning rollback");
        }

        refreshShipments(touchedShipments);
    }

    @Transactional
    public void cancelAssignment(Assignment assignment, String reason, LocalDateTime simNow, String actor) {
        if (assignment == null || isClosedAssignment(assignment)) {
            return;
        }
        LocalDateTime now = resolveTime(simNow);
        Set<Shipment> touchedShipments = new LinkedHashSet<>();

        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED) {
                continue;
            }
            item.setStatus(ShipmentItem.ShipmentItemStatus.CANCELLED);
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getShipment() != null) {
                touchedShipments.add(item.getShipment());
            }
        }

        assignment.setStatus(Assignment.AssignmentStatus.CANCELLED);
        assignment.setEndTime(now);
        assignment.setUpdatedBy(reason != null ? reason : actor);
        assignment.setUpdatedTime(LocalDateTime.now());
        assignmentRepository.save(assignment);

        releaseDriverIfIdle(assignment, actor);

        Vehicle vehicle = resolveVehicle(null, assignment);
        if (vehicle != null) {
            vehicle.removeAssignment(assignment);
            vehicle.transitionToStatus(Vehicle.VehicleStatus.IDLE, now, Duration.ZERO);
            // Phase 1：取消任务释放车辆时，吨制运行载重归零。
            vehicle.setCurrentLoadTonnes(0.0);
            vehicle.setCurrentVolumn(0.0);
            vehicle.setUpdatedBy(actor);
            vehicle.setUpdatedTime(LocalDateTime.now());
            vehicleRepository.save(vehicle);
            assignmentRepository.save(assignment);
        }

        refreshShipments(touchedShipments);
    }

    @Transactional
    public void cancelShipment(Shipment shipment, String reason, LocalDateTime simNow, String actor) {
        if (shipment == null || shipment.getId() == null
                || shipment.getStatus() == Shipment.ShipmentStatus.DELIVERED
                || shipment.getStatus() == Shipment.ShipmentStatus.CANCELLED) {
            return;
        }

        List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(shipment.getId());
        Set<Assignment> assignments = new LinkedHashSet<>();
        for (ShipmentItem item : items) {
            if (item == null || item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED) {
                continue;
            }
            item.setStatus(ShipmentItem.ShipmentItemStatus.CANCELLED);
            item.setUpdatedBy(actor);
            item.setUpdatedTime(LocalDateTime.now());
            shipmentItemRepository.save(item);
            if (item.getAssignment() != null) {
                assignments.add(item.getAssignment());
            }
        }

        for (Assignment assignment : assignments) {
            cancelAssignment(assignment, reason, simNow, actor);
        }

        shipment.setStatus(Shipment.ShipmentStatus.CANCELLED);
        shipment.setUpdatedAt(LocalDateTime.now());
        shipment.setUpdatedBy(actor);
        shipmentRepository.save(shipment);
    }

    @Transactional
    public Shipment refreshShipmentStatus(Shipment shipment) {
        if (shipment == null || shipment.getId() == null) {
            return shipment;
        }

        List<ShipmentItem> items = shipmentItemRepository.findByShipmentId(shipment.getId());
        if (items.isEmpty()) {
            shipment.setStatus(Shipment.ShipmentStatus.CREATED);
            shipment.setUpdatedAt(LocalDateTime.now());
            return shipmentRepository.save(shipment);
        }

        long cancelled = items.stream()
                .filter(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED)
                .count();
        List<ShipmentItem> effectiveItems = items.stream()
                .filter(item -> item.getStatus() != ShipmentItem.ShipmentItemStatus.CANCELLED)
                .toList();

        Shipment.ShipmentStatus nextStatus;
        if (effectiveItems.isEmpty() && cancelled > 0) {
            nextStatus = Shipment.ShipmentStatus.CANCELLED;
        } else if (!effectiveItems.isEmpty()
                && effectiveItems.stream().allMatch(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED)) {
            nextStatus = Shipment.ShipmentStatus.DELIVERED;
        } else if (effectiveItems.stream().anyMatch(item ->
                item.getStatus() == ShipmentItem.ShipmentItemStatus.IN_TRANSIT
                        || item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED)) {
            nextStatus = Shipment.ShipmentStatus.IN_TRANSIT;
        } else if (!effectiveItems.isEmpty()
                && effectiveItems.stream().allMatch(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.LOADED)) {
            nextStatus = Shipment.ShipmentStatus.PICKED_UP;
        } else if (effectiveItems.stream().anyMatch(item ->
                item.getStatus() == ShipmentItem.ShipmentItemStatus.ASSIGNED
                        || item.getStatus() == ShipmentItem.ShipmentItemStatus.LOADED)) {
            nextStatus = Shipment.ShipmentStatus.PLANNED;
        } else {
            nextStatus = Shipment.ShipmentStatus.CREATED;
        }

        shipment.setStatus(nextStatus);
        shipment.setUpdatedAt(LocalDateTime.now());
        return shipmentRepository.save(shipment);
    }

    private void refreshShipments(Set<Shipment> shipments) {
        if (shipments == null) {
            return;
        }
        shipments.stream()
                .filter(Objects::nonNull)
                .forEach(this::refreshShipmentStatus);
    }

    private Set<ShipmentItem> getAssignmentItems(Assignment assignment) {
        if (assignment == null || assignment.getShipmentItems() == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(assignment.getShipmentItems());
    }

    private boolean isTerminalItem(ShipmentItem item) {
        return item.getStatus() == ShipmentItem.ShipmentItemStatus.DELIVERED
                || item.getStatus() == ShipmentItem.ShipmentItemStatus.CANCELLED;
    }

    private boolean isActiveAssignment(Assignment assignment) {
        return assignment != null && assignment.getStatus() == Assignment.AssignmentStatus.IN_PROGRESS;
    }

    private boolean hasAssignedItems(Set<ShipmentItem> items) {
        if (items == null) {
            return false;
        }
        return items.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> item.getStatus() == ShipmentItem.ShipmentItemStatus.ASSIGNED);
    }

    private boolean hasNodes(Assignment assignment) {
        return assignment != null && assignment.getNodes() != null && !assignment.getNodes().isEmpty();
    }

    private AssignmentNode resolveCurrentNode(Assignment assignment) {
        List<AssignmentNode> nodes = orderedNodes(assignment);
        if (nodes.isEmpty()) {
            return null;
        }

        Integer idx = assignment.getCurrentActionIndex();
        if (idx != null && idx >= 0 && idx < nodes.size()) {
            AssignmentNode node = nodes.get(idx);
            if (node != null && !node.isCompleted()) {
                return node;
            }
        }

        return nodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> !node.isCompleted())
                .findFirst()
                .orElse(null);
    }

    private List<AssignmentNode> orderedNodes(Assignment assignment) {
        if (!hasNodes(assignment)) {
            return new ArrayList<>();
        }
        List<AssignmentNode> nodes = new ArrayList<>(assignment.getNodes());
        nodes.sort(Comparator.comparing(
                AssignmentNode::getSequenceIndex,
                Comparator.nullsLast(Integer::compareTo)
        ));
        return nodes;
    }

    private void advanceNodeIndex(Assignment assignment) {
        List<AssignmentNode> nodes = orderedNodes(assignment);
        for (int i = 0; i < nodes.size(); i++) {
            AssignmentNode node = nodes.get(i);
            if (node != null && !node.isCompleted()) {
                assignment.setCurrentActionIndex(i);
                return;
            }
        }
        assignment.setCurrentActionIndex(nodes.size());
    }

    private double[] calculateCompletedNodeLoad(Assignment assignment) {
        double load = 0.0;
        double volume = 0.0;
        for (AssignmentNode node : orderedNodes(assignment)) {
            if (node == null || !node.isCompleted()) {
                continue;
            }
            // Phase 1：VRP 节点载重增量的单位明确为吨；负值表示卸货。
            load += safe(node.getWeightDeltaTonnes());
            volume += safe(node.getVolumeDelta());
        }
        return new double[]{Math.max(0.0, load), Math.max(0.0, volume)};
    }

    private double[] calculateLoadedItemLoad(Assignment assignment) {
        double load = 0.0;
        double volume = 0.0;
        for (ShipmentItem item : getAssignmentItems(assignment)) {
            if (item == null || item.getStatus() == null) {
                continue;
            }
            if (item.getStatus() == ShipmentItem.ShipmentItemStatus.LOADED
                    || item.getStatus() == ShipmentItem.ShipmentItemStatus.IN_TRANSIT) {
                // Phase 1：ShipmentItem.weight 在运输链中表示该货物项总吨数。
                load += safe(item.getWeightTonnes());
                volume += safe(item.getVolume());
            }
        }
        return new double[]{Math.max(0.0, load), Math.max(0.0, volume)};
    }

    private double safe(Double value) {
        return value != null ? value : 0.0;
    }

    private boolean shouldMarkTransportDriving(Vehicle vehicle) {
        if (vehicle == null || vehicle.getCurrentStatus() == null) {
            return true;
        }
        Vehicle.VehicleStatus status = vehicle.getCurrentStatus();
        return status == Vehicle.VehicleStatus.IDLE
                || status == Vehicle.VehicleStatus.ORDER_DRIVING
                || status == Vehicle.VehicleStatus.LOADING;
    }

    private boolean isClosedAssignment(Assignment assignment) {
        return assignment.getStatus() == Assignment.AssignmentStatus.COMPLETED
                || assignment.getStatus() == Assignment.AssignmentStatus.CANCELLED
                || assignment.getStatus() == Assignment.AssignmentStatus.FAILED;
    }

    private Vehicle resolveVehicle(Vehicle vehicle, Assignment assignment) {
        if (vehicle != null && vehicle.getId() != null) {
            return vehicleRepository.findById(vehicle.getId()).orElse(vehicle);
        }
        if (assignment != null && assignment.getAssignedVehicle() != null
                && assignment.getAssignedVehicle().getId() != null) {
            return vehicleRepository.findById(assignment.getAssignedVehicle().getId())
                    .orElse(assignment.getAssignedVehicle());
        }
        return null;
    }

    private LocalDateTime resolveTime(LocalDateTime simNow) {
        // Phase 1：显式事件时间优先；生产缺省值来自 SimulationContext，保证业务时间只有一个权威源。
        if (simNow != null) {
            return simNow;
        }
        if (simulationContext != null) {
            return simulationContext.getCurrentSimTime();
        }
        // Phase 1：仅四参数测试构造器保留墙上时间退路，生产 Spring 构造器不会进入该分支。
        return LocalDateTime.now();
    }
}
