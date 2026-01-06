package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.example.roadsimulation.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    // 根据状态查询任务
    List<Assignment> findByStatus(AssignmentStatus status);

    // 根据状态分页查询
    Page<Assignment> findByStatus(AssignmentStatus status, Pageable pageable);

    // 根据车辆ID查询任务
    List<Assignment> findByAssignedVehicleId(Long vehicleId);

    // 根据司机ID查询任务
    List<Assignment> findByAssignedDriverId(Long driverId);

    // 根据路线ID查询任务
    List<Assignment> findByRouteId(Long routeId);

    // 根据状态和车辆ID查询
    List<Assignment> findByStatusAndAssignedVehicleId(AssignmentStatus status, Long vehicleId);

    // 根据状态和司机ID查询
    List<Assignment> findByStatusAndAssignedDriverId(AssignmentStatus status, Long driverId);

    // 查询指定时间范围内的任务
    List<Assignment> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    // 查询过期的任务（应该开始但还未开始）
    @Query("SELECT a FROM Assignment a WHERE a.status = :status AND a.startTime < :currentTime")
    List<Assignment> findOverdueAssignments(@Param("status") AssignmentStatus status,
                                            @Param("currentTime") LocalDateTime currentTime);

    // 统计各种状态的任务数量
    @Query("SELECT a.status, COUNT(a) FROM Assignment a GROUP BY a.status")
    List<Object[]> countAssignmentsByStatus();

    // 查询司机当前正在执行的任务
    @Query("SELECT a FROM Assignment a WHERE a.assignedDriver.id = :driverId AND a.status IN :statusList")
    List<Assignment> findDriverCurrentAssignments(@Param("driverId") Long driverId,
                                                  @Param("statusList") List<AssignmentStatus> statusList);

    // 查询车辆当前任务负载
    @Query("SELECT a.assignedVehicle.id, COUNT(a) FROM Assignment a WHERE a.status IN :activeStatuses GROUP BY a.assignedVehicle.id")
    List<Object[]> countActiveAssignmentsPerVehicle(@Param("activeStatuses") List<AssignmentStatus> activeStatuses);
    List<Assignment> findByAssignedVehicleAndStatusIn(Vehicle vehicle, List<AssignmentStatus> statuses);
}