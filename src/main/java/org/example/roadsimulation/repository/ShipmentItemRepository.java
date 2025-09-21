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
    // 根据SKU查找清单实体
    Optional<ShipmentItem> findBySku(@Param("sku") String sku);

    // 根据清单名称查找清单实体
    Optional<ShipmentItem> findByName(@Param("name") String name);

    // 查询某种货物对应的所有运单清单及其所属运单和任务分配情况
    // 1. 查询某个运单的所有明细项 (这是最重要的查询)
    List<ShipmentItem> findByShipmentId(Long shipmentId);
    // 分页版本
    Page<ShipmentItem> findByShipmentId(Long shipmentId, Pageable pageable);

    // 2. 查询某个货物的所有明细项（用于库存追溯等）
    List<ShipmentItem> findByGoodsId(Long goodsId);
    Page<ShipmentItem> findByGoodsId(Long goodsId, Pageable pageable);
    // 3. 查询某个分配任务的所有明细项
    List<ShipmentItem> findByAssignmentId(Long assignmentId);

    // 4. 综合查询：根据运单、货物、分配任务进行筛选
    @Query("SELECT si FROM ShipmentItem si WHERE " +
            "(:shipmentId IS NULL OR si.shipment.id = :shipmentId) AND " +
            "(:goodsId IS NULL OR si.goods.id = :goodsId) AND " +
            "(:assignmentId IS NULL OR si.assignment.id = :assignmentId)")
    Page<ShipmentItem> findShipmentItems(
            @Param("shipmentId") Long shipmentId,
            @Param("goodsId") Long goodsId,
            @Param("assignmentId") Long assignmentId,
            Pageable pageable);

    // 5. 统计某个运单的总体积、总重量等 (聚合查询)
    @Query("SELECT COALESCE(SUM(si.volume), 0) FROM ShipmentItem si WHERE si.shipment.id = :shipmentId")
    Double sumVolumeByShipmentId(@Param("shipmentId") Long shipmentId);

    @Query("SELECT COALESCE(SUM(si.weight), 0) FROM ShipmentItem si WHERE si.shipment.id = :shipmentId")
    Double sumWeightByShipmentId(@Param("shipmentId") Long shipmentId);

    // 根据货物ID查询运单项，并关联获取Assignment
    @Query("SELECT si FROM ShipmentItem si LEFT JOIN FETCH si.assignment WHERE si.goods.id = :goodsId")
    List<ShipmentItem> findByGoodsIdWithAssignment(@Param("goodsId") Long goodsId);

    /*
    // 加入AssignmentRepository中 ToDo
    // 根据货物ID查询分配任务（通过运单项关联）
    @Query("SELECT DISTINCT a FROM Assignment a JOIN a.shipmentItems si WHERE si.goods.id = :goodsId")
    List<Assignment> findByGoodsId(@Param("goodsId") Long goodsId);

    @Query("SELECT DISTINCT a FROM Assignment a JOIN a.shipmentItems si WHERE si.goods.id = :goodsId")
    Page<Assignment> findByGoodsId(@Param("goodsId") Long goodsId, Pageable pageable);
    * */
}
