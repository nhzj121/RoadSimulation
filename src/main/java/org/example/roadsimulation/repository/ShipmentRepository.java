package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
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
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    // 根据系统参考号来查找运单
    Optional<Shipment> findByRefNo(String refNo);

    // 根据运单状态来查询相应运单
    List<Shipment> findByStatus(Shipment.ShipmentStatus status);

    // 检查运单系统参考号是否存在
    boolean existsByRefNo(String refNo);

    // 1. 查询某个客户的所有运单
    List<Shipment> findByCustomer_Id(Long customerId); // 通过客户ID查询
    List<Shipment> findByCustomer_Code(String customerCode); // 通过客户编码查询（更业务化）

    // 2. 查询起运地或目的地为某个POI的所有运单
    List<Shipment> findByOriginPOI_Id(Long originPOIId);
    List<Shipment> findByDestPOI_Id(Long destPOIId);

    // 3. (复杂关联查询示例) 查询某个客户且状态为指定的值的所有运单
    List<Shipment> findByCustomer_IdAndStatus(Long customerId, Shipment.ShipmentStatus status);

    // 4. 查询在某个时间段内创建的运单（常用于报表、筛选）
    List<Shipment> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 5. 查询预约提货时间在某个时间之后的运单（常用于调度）
    List<Shipment> findByPickupAppointAfter(LocalDateTime dateTime);
    List<Shipment> findByPickupAppointBefore(LocalDateTime dateTime);

    // 6. 根据状态和创建时间查询
    List<Shipment> findByStatusAndCreatedAtBefore(Shipment.ShipmentStatus status, LocalDateTime dateTime);

    // 7. 综合查询：根据客户、状态、时间范围进行筛选（这是一个非常常见的业务场景）
    @Query("SELECT s FROM Shipment s WHERE " +
            "(:customerId IS NULL OR s.customer.id = :customerId) AND " +
            "(:status IS NULL OR s.status = :status) AND " +
            "(:startDate IS NULL OR s.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR s.createdAt <= :endDate)")
    Page<Shipment> findShipments(
            @Param("customerId") Long customerId,
            @Param("status") Shipment.ShipmentStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable); // 使用分页，对于大量数据结果非常必要
}
