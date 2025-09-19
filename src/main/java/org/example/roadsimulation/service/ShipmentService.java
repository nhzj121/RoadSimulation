package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShipmentService {
    // 创建运单
    Shipment createShipment(Shipment shipment);

    // 更新运单信息
    Shipment updateShipment(Long id, Shipment shipmentDetails);

    // 获取所有运单
    List<Shipment> getAllShipments();

    // 分页获取运单
    Page<Shipment> getAllShipments(Pageable pageable);

    // 根据系统参考号获取运单
    Optional<Shipment> getShipmentByRefNo(String refNo);

    // 根据ID获取运单
    Optional<Shipment> getShipmentById(Long id);

    // 根据状态查询运单
    List<Shipment> getShipmentsByStatus(Shipment.ShipmentStatus status);

    // 根据客户ID查询运单
    List<Shipment> getShipmentsByCustomerId(Long customerId);

    // 根据客户编码查询运单
    List<Shipment> getShipmentsByCustomerCode(String customerCode);

    // 综合查询（使用Repository中的复杂查询）
    Page<Shipment> searchShipments(Long customerId, Shipment.ShipmentStatus status,
                                   LocalDateTime startDate, LocalDateTime endDate,
                                   Pageable pageable);

    // 更新运单状态（可能有特定的状态转换规则）
    Shipment updateStatus(Long id, Shipment.ShipmentStatus newStatus);

    // 删除运单
    void deleteShipment(Long id);

    // 检查运单系统参考号是否存在
    boolean existsByRefNo(String refNo);
}
