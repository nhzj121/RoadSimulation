package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.BatchOperationResult;
import org.example.roadsimulation.entity.ShipmentItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ShipmentItemService {
    // 创建运单清单
    ShipmentItem createShipmentItem(ShipmentItem shipmentItem);
    // 批量创建，严格模式：一个出错，全部回滚
    List<ShipmentItem> createShipmentItems(List<ShipmentItem> items);
    // 批量创建，宽松模式：尽可能的读取正确项
    BatchOperationResult<ShipmentItem> createShipmentItemsWithResult(List<ShipmentItem> items);
    // 更新运单清单信息
    ShipmentItem updateShipmentItem(Long shipmentItemId, ShipmentItem itemDetails);
    // 获取所有运单清单
    List<ShipmentItem> getAllShipmentItems();
    // 根据ID获取运单清单
    Optional<ShipmentItem> getShipmentItemById(Long shipmentItemId);
    // 分页获取运单清单
    Page<ShipmentItem> getShipmentItems(Pageable pageable);

    // 根据运单清单查询关联分配任务、所属运单、货物
    // 1. 查询运单的所有明细项
    List<ShipmentItem> getItemsByShipmentId(Long shipmentId);
    Page<ShipmentItem> getItemsByShipmentId(Long shipmentId, Pageable pageable);

    // 3. 统计信息
    Map<String, Double> getSummaryByShipmentId(Long shipmentId); // 返回总体积、总重量等

    // 4. 更新分配任务（这是一个重要的业务操作）
    ShipmentItem assignToAssignment(Long itemId, Long assignmentId);
    ShipmentItem unassignFromAssignment(Long itemId);

    // 删除运单清单
    void deleteShipmentItem(Long shipmentItemId);


}
