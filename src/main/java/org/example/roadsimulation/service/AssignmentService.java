package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.AssignmentBriefDTO;
import org.example.roadsimulation.dto.AssignmentDTO;
import org.example.roadsimulation.dto.AssignmentRequestDTO;
import org.example.roadsimulation.dto.AssignmentResponseDTO;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Assignment.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AssignmentService {

    // 基本CRUD操作
    AssignmentResponseDTO createAssignment(AssignmentRequestDTO requestDTO);
    AssignmentResponseDTO getAssignmentById(Long id);
    Page<AssignmentResponseDTO> getAllAssignments(Pageable pageable);
    AssignmentResponseDTO updateAssignment(Long id, AssignmentRequestDTO requestDTO);
    void deleteAssignment(Long id);

    // 查询操作
    List<AssignmentResponseDTO> getAssignmentsByStatus(AssignmentStatus status);
    List<AssignmentResponseDTO> getAssignmentsByVehicle(Long vehicleId);
    List<AssignmentResponseDTO> getAssignmentsByDriver(Long driverId);
    List<AssignmentResponseDTO> getAssignmentsByRoute(Long routeId);

    // 业务操作
    AssignmentResponseDTO startAssignment(Long id);
    AssignmentResponseDTO completeAssignment(Long id);
    AssignmentResponseDTO cancelAssignment(Long id);
    AssignmentResponseDTO moveToNextAction(Long id);
    AssignmentResponseDTO updateAssignmentStatus(Long id, AssignmentStatus status);

    // 批量操作
    List<AssignmentResponseDTO> batchCreateAssignments(List<AssignmentRequestDTO> requestDTOs);
    void batchUpdateStatus(List<Long> assignmentIds, AssignmentStatus status);

    // 前后端数据传输
    List<AssignmentBriefDTO> getActiveAssignments();
    List<AssignmentBriefDTO> getNewAssignments();
    AssignmentDTO getAssignmentDetail(Long assignmentId); // ToDo 这里需要从数据库获取完整的 Assignment 信息并转换
    void markAssignmentAsDrawn(Long assignmentId);
    List<Long> getCompletedAssignments();

}