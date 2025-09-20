package org.example.roadsimulation.controller;

import jakarta.validation.Valid;
import org.example.roadsimulation.dto.BatchOperationResult;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.service.ShipmentItemService;
import org.example.roadsimulation.service.ShipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/shipments/{shipmentId}/items") // 父资源路径
public class ShipmentItemController {

    private final ShipmentService shipmentService;
    private final ShipmentItemService shipmentItemService;

    @Autowired
    public ShipmentItemController(ShipmentService shipmentService, ShipmentItemService shipmentItemService) {
        this.shipmentService = shipmentService;
        this.shipmentItemService = shipmentItemService;
    }
    // 创建单个运单清单项
    @PostMapping
    public ResponseEntity<ShipmentItem> createItemForShipment(
            @PathVariable Long shipmentId,
            @Valid @RequestBody ShipmentItem item) {

        // 【关键步骤】验证并建立从属关系
        // 1. 确保指定的shipment存在
        Shipment shipment = shipmentService.getShipmentById(shipmentId)
                .orElseThrow(() -> new RuntimeException("运单不存在，ID: " + shipmentId));

        // 2. 将运单项关联到该运单（防止客户端在body中传递错误的shipmentId）
        item.setShipment(shipment);

        // 3. 创建运单项
        ShipmentItem createdItem = shipmentItemService.createShipmentItem(item);

        // 4. 构造响应。Location头部可以指向新创建的子资源
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdItem.getId())
                .toUri();

        return ResponseEntity.created(location).body(createdItem);
    }

    // 批量创建运单项（严格模式）
    @PostMapping("/batch")
    public ResponseEntity<List<ShipmentItem>> createShipmentItemsBatch(
            @PathVariable Long shipmentId,
            @Valid @RequestBody List<ShipmentItem> items) {

        Shipment shipment = shipmentService.getShipmentById(shipmentId)
                .orElseThrow(() -> new RuntimeException("运单不存在，ID: " + shipmentId));

        for (ShipmentItem item : items) {
            item.setShipment(shipment);
        }

        List<ShipmentItem> createdItems = shipmentItemService.createShipmentItems(items);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdItems);
    }

    // 批量添加运单清单，宽松模式
    @PostMapping("/batch-with-results")
    public ResponseEntity<BatchOperationResult<ShipmentItem>> createShipmentItemsBatchWithResults(
            @PathVariable Long shipmentId, // 添加路径变量
            @RequestBody List<ShipmentItem> items) {

        // 验证运单存在
        Shipment shipment = shipmentService.getShipmentById(shipmentId)
                .orElseThrow(() -> new RuntimeException("运单不存在，ID: " + shipmentId));

        // 确保所有项都属于这个运单
        for (ShipmentItem item : items) {
            if (item.getShipment() == null || !item.getShipment().getId().equals(shipmentId)) {
                // 可以统一设置正确的运单关系
                item.setShipment(shipment);
            }
        }

        BatchOperationResult<ShipmentItem> result = shipmentItemService.createShipmentItemsWithResult(items);
        // 只要有成功创建的项，就返回201，否则返回400
        if (result.getSuccessfulItems().isEmpty()) {
            return ResponseEntity.badRequest().body(result);
        } else {
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
    }

    // 从运单ID对应运单中获取运单清单
    // 获取全部运单清单
    @GetMapping
    public ResponseEntity<List<ShipmentItem>> getItemsForShipment(@PathVariable Long shipmentId) {
        // 直接调用Service中获取某个运单所有项的方法
        List<ShipmentItem> items = shipmentItemService.getItemsByShipmentId(shipmentId);
        return ResponseEntity.ok(items);
    }
    // 获取特定运单清单
    @GetMapping("/{itemId}")
    public ResponseEntity<ShipmentItem> getItemForShipment(
            @PathVariable Long shipmentId,
            @PathVariable Long itemId) {

        Optional<ShipmentItem> item = shipmentItemService.getShipmentItemById(itemId);

        // 【关键步骤】校验归属关系：找到的项必须属于URL中指定的运单
        if (item.isPresent() && item.get().getShipment().getId().equals(shipmentId)) {
            return ResponseEntity.ok(item.get());
        } else {
            // 项不存在，或者存在但不属于这个运单，都返回404
            return ResponseEntity.notFound().build();
        }
    }
    // 分页获取运单清单
    @GetMapping("/page")
    public ResponseEntity<Page<ShipmentItem>> getItemsForShipmentPage(
            @PathVariable Long shipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        // 构建分页参数
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        // 查询分页数据
        Page<ShipmentItem> itemPage = shipmentItemService.getItemsByShipmentId(shipmentId, pageable);
        return ResponseEntity.ok(itemPage);
    }

    // 更新运单清单
    @PutMapping("/{itemId}")
    public ResponseEntity<ShipmentItem> updateItemForShipment(
            @PathVariable Long shipmentId,
            @PathVariable Long itemId,
            @Valid @RequestBody ShipmentItem itemDetails) {

        // 验证项存在且属于指定运单
        ShipmentItem existingItem = shipmentItemService.getShipmentItemById(itemId)
                .filter(item -> item.getShipment().getId().equals(shipmentId))
                .orElseThrow(() -> new RuntimeException("运单项不存在或不属于指定运单"));
        // 防止更改运单关联
        itemDetails.setShipment(existingItem.getShipment());
        itemDetails.setId(itemId);
        // 更新项信息
        ShipmentItem updatedItem = shipmentItemService.updateShipmentItem(itemId, itemDetails);
        return ResponseEntity.ok(updatedItem);
    }
    // 暂时的信息汇总，获取运单清单对应总体积和总质量
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Double>> getShipmentSummary(@PathVariable Long shipmentId) {
        Map<String, Double> summary = shipmentItemService.getSummaryByShipmentId(shipmentId);
        return ResponseEntity.ok(summary);
    }

    // 分配与删除对应分配任务（Assignment）
    @PatchMapping("/{itemId}/assign/{assignmentId}")
    public ResponseEntity<ShipmentItem> assignItemToTask(
            @PathVariable Long shipmentId,
            @PathVariable Long itemId,
            @PathVariable Long assignmentId) {

        // 验证项存在且属于指定运单
        shipmentItemService.getShipmentItemById(itemId)
                .filter(item -> item.getShipment().getId().equals(shipmentId))
                .orElseThrow(() -> new RuntimeException("运单项不存在或不属于指定运单"));

        try {
            ShipmentItem assignedItem = shipmentItemService.assignToAssignment(itemId, assignmentId);
            return ResponseEntity.ok(assignedItem);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{itemId}/unassign")
    public ResponseEntity<ShipmentItem> unassignItemFromTask(
            @PathVariable Long shipmentId,
            @PathVariable Long itemId) {

        // 验证项存在且属于指定运单
        shipmentItemService.getShipmentItemById(itemId)
                .filter(item -> item.getShipment().getId().equals(shipmentId))
                .orElseThrow(() -> new RuntimeException("运单项不存在或不属于指定运单"));

        try {
            ShipmentItem unassignedItem = shipmentItemService.unassignFromAssignment(itemId);
            return ResponseEntity.ok(unassignedItem);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 删除运单清单
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteItemForShipment(
            @PathVariable Long shipmentId,
            @PathVariable Long itemId) {

        // 验证项存在且属于指定运单
        ShipmentItem existingItem = shipmentItemService.getShipmentItemById(itemId)
                .filter(item -> item.getShipment().getId().equals(shipmentId))
                .orElseThrow(() -> new RuntimeException("运单项不存在或不属于指定运单"));

        // 删除项
        try {
            shipmentItemService.deleteShipmentItem(itemId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

}
