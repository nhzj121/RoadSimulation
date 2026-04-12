package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ShipmentItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, Long> {
    
    Optional<ShipmentItem> findBySku(@Param("sku") String sku);

    Optional<ShipmentItem> findByName(@Param("name") String name);

    List<ShipmentItem> findByShipmentId(Long shipmentId);
    
    Page<ShipmentItem> findByShipmentId(Long shipmentId, Pageable pageable);

    List<ShipmentItem> findByGoodsId(Long goodsId);
    
    Page<ShipmentItem> findByGoodsId(Long goodsId, Pageable pageable);
    
    List<ShipmentItem> findByAssignmentId(Long assignmentId);

    List<ShipmentItem> findByStatus(String status);
    List<ShipmentItem> findByStatus(ShipmentItem.ShipmentItemStatus status);
    /**
     * 统计运单的运单项数量
     */
    Long countByShipmentId(Long shipmentId);

    Long countByShipmentIdAndStatus(Long shipmentId, String status);

    @Query("SELECT si FROM ShipmentItem si WHERE " +
            "(:shipmentId IS NULL OR si.shipment.id = :shipmentId) AND " +
            "(:goodsId IS NULL OR si.goods.id = :goodsId) AND " +
            "(:assignmentId IS NULL OR si.assignment.id = :assignmentId)")
    Page<ShipmentItem> findShipmentItems(
            @Param("shipmentId") Long shipmentId,
            @Param("goodsId") Long goodsId,
            @Param("assignmentId") Long assignmentId,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(si.volume), 0) FROM ShipmentItem si WHERE si.shipment.id = :shipmentId")
    Double sumVolumeByShipmentId(@Param("shipmentId") Long shipmentId);

    @Query("SELECT COALESCE(SUM(si.weight), 0) FROM ShipmentItem si WHERE si.shipment.id = :shipmentId")
    Double sumWeightByShipmentId(@Param("shipmentId") Long shipmentId);

    @Query("SELECT si FROM ShipmentItem si LEFT JOIN FETCH si.assignment WHERE si.goods.id = :goodsId")
    List<ShipmentItem> findByGoodsIdWithAssignment(@Param("goodsId") Long goodsId);

    // ==================== 加工物料项查询方法 ====================
    
    List<ShipmentItem> findByShipmentIdOrderByStageOrder(Long shipmentId);
    
    Optional<ShipmentItem> findByShipmentIdAndStageOrder(Long shipmentId, Integer stageOrder);
    
    List<ShipmentItem> findByStageId(Long stageId);
    
    List<ShipmentItem> findByProcessingStatus(ShipmentItem.ProcessingItemStatus status);
    
    long countByStageId(Long stageId);
}
