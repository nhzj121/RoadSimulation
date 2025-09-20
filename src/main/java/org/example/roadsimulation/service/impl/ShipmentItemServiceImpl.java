package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.BatchOperationResult;
import org.example.roadsimulation.dto.BatchError;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.repository.ShipmentItemRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.service.ShipmentItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShipmentItemServiceImpl implements ShipmentItemService {
    private final ShipmentItemRepository shipmentItemRepository;
    private final ShipmentRepository shipmentRepository;

    @Autowired
    public ShipmentItemServiceImpl(ShipmentItemRepository shipmentItemRepository,
                                   ShipmentRepository shipmentRepository) {
        this.shipmentItemRepository = shipmentItemRepository;
        this.shipmentRepository = shipmentRepository;
    }

    // 创建运单清单
    @Override
    public ShipmentItem createShipmentItem(ShipmentItem shipmentItem){
        // 添加业务验证：确保关联的运单存在
        if (shipmentItem.getShipment() == null || shipmentItem.getShipment().getId() == null) {
            throw new IllegalArgumentException("运单项必须属于一个有效运单");
        }
        // 验证运单确实存在
        Long shipmentId = shipmentItem.getShipment().getId();
        if (!shipmentRepository.existsById(shipmentId)) {
            throw new IllegalArgumentException("运单不存在，ID: " + shipmentId);
        }
        // 可以在此处自动计算或验证一些字段，比如如果goods不为空，可以从goods中获取默认的name/sku  ToDo
        return shipmentItemRepository.save(shipmentItem);
    }

    // 批量加入
    @Override
    public List<ShipmentItem> createShipmentItems(List<ShipmentItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 数据验证
        validateShipmentItems(items);

        // 2. 批量保存
        return shipmentItemRepository.saveAll(items);
    }

    /**
     * 验证运单项列表的合法性
     */
    private void validateShipmentItems(List<ShipmentItem> items) {
        Set<Long> shipmentIds = new HashSet<>();

        for (ShipmentItem item : items) {
            // 基本字段验证
            if (item.getShipment() == null || item.getShipment().getId() == null) {
                throw new IllegalArgumentException("运单项必须关联到一个有效运单");
            }

            if (item.getName() == null || item.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("运单项名称不能为空");
            }

            if (item.getQty() == null || item.getQty() < 1) {
                throw new IllegalArgumentException("运单项数量必须大于0");
            }

            // 收集所有涉及的运单ID，用于批量验证
            shipmentIds.add(item.getShipment().getId());
        }

        // 批量验证所有运单是否存在
        validateShipmentsExist(shipmentIds);

        // 如果需要，还可以添加其他业务规则验证
        // 例如：检查SKU是否重复、总重量/体积是否超过运单限制等
    }

    /**
     * 批量验证运单是否存在
     */
    private void validateShipmentsExist(Set<Long> shipmentIds) {
        if (shipmentIds.isEmpty()) {
            return;
        }

        List<Long> existingShipmentIds = shipmentRepository.findAllById(shipmentIds)
                .stream()
                .map(Shipment::getId)
                .toList();

        // 找出不存在的运单ID
        Set<Long> nonExistingIds = shipmentIds.stream()
                .filter(id -> !existingShipmentIds.contains(id))
                .collect(Collectors.toSet());

        if (!nonExistingIds.isEmpty()) {
            throw new IllegalArgumentException("以下运单不存在: " + nonExistingIds);
        }
    }
    @Override
    @Transactional(readOnly = true)
    public BatchOperationResult<ShipmentItem> createShipmentItemsWithResult(List<ShipmentItem> items) {
        if (items == null || items.isEmpty()) {
            return new BatchOperationResult<>(Collections.emptyList(), Collections.emptyList());
        }

        List<ShipmentItem> successfulItems = new ArrayList<>();
        List<BatchError> errors = new ArrayList<>();

        // 按运单分组处理，可以提高性能
        Map<Long, List<ShipmentItem>> itemsByShipment = items.stream()
                .collect(Collectors.groupingBy(item -> item.getShipment().getId()));

        // 批量验证所有涉及的运单
        Set<Long> shipmentIds = itemsByShipment.keySet();
        Map<Long, Shipment> existingShipments = shipmentRepository.findAllById(shipmentIds)
                .stream()
                .collect(Collectors.toMap(Shipment::getId, Function.identity()));

        // 处理每个运单项
        for (int i = 0; i < items.size(); i++) {
            ShipmentItem item = items.get(i);

            try {
                // 验证运单是否存在
                Long shipmentId = item.getShipment().getId();
                if (!existingShipments.containsKey(shipmentId)) {
                    throw new IllegalArgumentException("运单不存在: " + shipmentId);
                }

                // 替换为完整的运单实体（避免只包含ID的代理对象）
                item.setShipment(existingShipments.get(shipmentId));

                // 其他验证
                if (item.getName() == null || item.getName().trim().isEmpty()) {
                    throw new IllegalArgumentException("名称不能为空");
                }

                if (item.getQty() == null || item.getQty() < 1) {
                    throw new IllegalArgumentException("数量必须大于0");
                }

                // 验证通过，添加到成功列表
                successfulItems.add(item);

            } catch (Exception e) {
                // 记录错误信息但继续处理其他项
                errors.add(new BatchError(i, e.getMessage()));
            }
        }

        // 批量保存所有通过验证的项
        if (!successfulItems.isEmpty()) {
            successfulItems = shipmentItemRepository.saveAll(successfulItems);
        }

        return new BatchOperationResult<>(successfulItems, errors);
    }

    @Override
    public ShipmentItem updateShipmentItem(Long id, ShipmentItem itemDetails) {
        return shipmentItemRepository.findById(id)
                .map(existingItem -> {
                    // 更新字段 - 注意：有些字段可能不应该被更新，如shipment
                    if (itemDetails.getName() != null) {
                        existingItem.setName(itemDetails.getName());
                    }
                    if (itemDetails.getSku() != null) {
                        existingItem.setSku(itemDetails.getSku());
                    }
                    if (itemDetails.getQty() != null) {
                        existingItem.setQty(itemDetails.getQty());
                    }
                    if (itemDetails.getWeight() != null) {
                        existingItem.setWeight(itemDetails.getWeight());
                    }
                    if (itemDetails.getVolume() != null) {
                        existingItem.setVolume(itemDetails.getVolume());
                    }
                    // goods和assignment的更新可能需要更复杂的逻辑
                    // ToDo

                    return shipmentItemRepository.save(existingItem);
                })
                .orElseThrow(() -> new RuntimeException("运单项不存在，ID: " + id));
    }


    // 获取所有运单清单
    @Override
    @Transactional(readOnly = true)
    public List<ShipmentItem> getAllShipmentItems(){
        return shipmentItemRepository.findAll();
    }
    // 根据ID获取运单清单
    @Override
    @Transactional(readOnly = true)
    public Optional<ShipmentItem> getShipmentItemById(Long shipmentItemId){
        return shipmentItemRepository.findById(shipmentItemId);
    }
    // 分页获取运单清单
    @Override
    @Transactional(readOnly = true)
    public Page<ShipmentItem> getShipmentItems(Pageable pageable){
        return shipmentItemRepository.findAll(pageable);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<ShipmentItem> getItemsByShipmentId(Long shipmentId, Pageable pageable){
        return shipmentItemRepository.findByShipmentId(shipmentId, pageable);
    }

    @Override
    public ShipmentItem assignToAssignment(Long itemId, Long assignmentId){
        // 验证shipmentItem是否存在
        ShipmentItem item = shipmentItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("运单项不存在，ID: "+ itemId));
        // 验证Assignment是否存在
//        Assignment assignment = assignmentRepository.findById(assignmentId)
//                .orElseThrow(() -> RuntimeException("分配任务不存在， ID: "+ assignmentId));
        //上面的判断需要等待AssignmentRepository的完成 ToDo
        // 3. 【关键】业务规则校验
        // 检查运单是否处于可分配状态（例如，不能分配已取消运单的项）
        if (!isShipmentAssignable(item.getShipment())) {
            throw new IllegalStateException("运单状态为" + item.getShipment().getStatus() + "，无法分配其项");
        }
        // 检查该运单清单是否被分配给了其它任务，是否需要先解除原分配再重新分配
        if(item.getAssignment() != null && !item.getAssignment().getId().equals(assignmentId)){
            // 1. 不允许直接的重新分配
            throw new IllegalStateException("运单已分配给任务 ID： " + item.getAssignment().getId() + ", 请先尝试解除分配后重试");
            // 2. 允许直接重新分配
            // unassignFromAssignment(itemId);
        }
        // 检查分配任务本身状态是否可以接收新项（如判断任务是否已经开始或结束）
//        if(!assignment.getStatus().equals(Assignment.AssignmentStatus.COMPLETED) /*||*/ ){
//            throw new IllegalStateException("分配任务状态为" + assignment.getStatus() + "，无法再添加运单项");
//        }
        // ToDo

        //item.setAssignment(item.getAssignment());
        return shipmentItemRepository.save(item);
    }

    // 辅助方法：判断运单状态是否允许分配
    private boolean isShipmentAssignable(Shipment shipment) {
        // 例如，只有状态为 CREATED 或 PLANNED 的运单才能被分配
        return shipment.getStatus() == Shipment.ShipmentStatus.CREATED
                || shipment.getStatus() == Shipment.ShipmentStatus.PLANNED;
    }

    public ShipmentItem unassignFromAssignment(Long itemId){
        ShipmentItem item = shipmentItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("运单项不存在，ID: " + itemId));

        // 业务规则校验
        if(item.getAssignment() == null){
            // 可以选择静默返回或者抛出错误
            return item;
            // throw new IllegalStateException("运单项未被分配，无法解除分配");
        }
        // 已分配任务的状态为已开始的，不允许解除
        Assignment currentAssignment = item.getAssignment();
        if(currentAssignment.getStatus().equals(Assignment.AssignmentStatus.IN_PROGRESS)){
            throw new IllegalStateException("分配任务正在进行中，无法解除运单项");
        }
        item.setAssignment(null);

        return shipmentItemRepository.save(item);
    }

    // 删除运单清单
    @Override
    public void deleteShipmentItem(Long id) {
        ShipmentItem item = shipmentItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("运单项不存在，ID: " + id));

        // 业务规则检查：如果已经分配了任务，不允许删除
        if (item.getAssignment() != null) {
            throw new IllegalStateException("无法删除已分配任务的运单项");
        }

        shipmentItemRepository.delete(item);
    }

    // 通过Shipment获取运单清单
    @Override
    public List<ShipmentItem> getItemsByShipmentId(Long shipmentId) {
        return shipmentItemRepository.findByShipmentId(shipmentId);
    }

    // 通过ShipmentItem的ID获取初步的总结，如总体积，总重量
    @Override
    public Map<String, Double> getSummaryByShipmentId(Long shipmentId) {
        Map<String, Double> summary = new HashMap<>();
        summary.put("totalVolume", shipmentItemRepository.sumVolumeByShipmentId(shipmentId));
        summary.put("totalWeight", shipmentItemRepository.sumWeightByShipmentId(shipmentId));
        return summary;
    }
}
