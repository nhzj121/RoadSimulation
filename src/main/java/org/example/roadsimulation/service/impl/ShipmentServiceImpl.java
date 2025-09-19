package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.service.ShipmentService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ShipmentServiceImpl implements ShipmentService {
    private final ShipmentRepository shipmentRepository;

    @Autowired
    public ShipmentServiceImpl(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @Override
    public Shipment createShipment(@NotNull Shipment shipment){
        // 检查系统参考号是否已存在
        if(shipmentRepository.existsByRefNo(shipment.getRefNo())){
            throw new IllegalArgumentException("运单系统参考号已存在：" + shipment.getRefNo());
        }

        return shipmentRepository.save(shipment);
    }
    // 更新运单信息
    @Override
    public Shipment updateShipment(Long id, Shipment shipmentDetails) {
        return shipmentRepository.findById(id)
                .map(shipment -> {
                    // 检查参考号是否与其他运单冲突（排除自己）
                    if (!shipment.getRefNo().equals(shipmentDetails.getRefNo()) &&
                            shipmentRepository.existsByRefNo(shipmentDetails.getRefNo())) {
                        throw new IllegalArgumentException("运单参考号已存在: " + shipmentDetails.getRefNo());
                    }

                    // 更新字段
                    shipment.setRefNo(shipmentDetails.getRefNo());
                    shipment.setCargoType(shipmentDetails.getCargoType());
                    shipment.setTotalWeight(shipmentDetails.getTotalWeight());
                    shipment.setTotalVolume(shipmentDetails.getTotalVolume());
                    shipment.setStatus(shipmentDetails.getStatus());
                    shipment.setPickupAppoint(shipmentDetails.getPickupAppoint());
                    shipment.setDeliveryAppoint(shipmentDetails.getDeliveryAppoint());

                    // 注意：关联实体（如customer, originPOI, destPOI）的更新需要特殊处理
                    // 通常需要先通过它们的ID查找并设置完整的实体对象

                    return shipmentRepository.save(shipment);
                })
                .orElseThrow(() -> new RuntimeException("运单不存在，ID: " + id));
    }

    @Override
    public Shipment updateStatus(Long id, Shipment.ShipmentStatus newStatus) {
        return shipmentRepository.findById(id)
                .map(shipment -> {
                    // 这里可以添加状态转换的业务规则校验
                    // 例如：不能从CANCELLED直接变为IN_TRANSIT
                    validateStatusTransition(shipment.getStatus(), newStatus);

                    shipment.setStatus(newStatus);
                    return shipmentRepository.save(shipment);
                })
                .orElseThrow(() -> new RuntimeException("运单不存在，ID: " + id));
    }

    // 获取所有运单
    @Override
    @Transactional(readOnly = true)
    public List<Shipment> getAllShipments(){
        return shipmentRepository.findAll();
    }


    // 分页获取运单
    @Override
    @Transactional(readOnly = true)
    public Page<Shipment> getAllShipments(Pageable pageable){
        return shipmentRepository.findAll(pageable);
    }

    // 根据系统参考号获取运单
    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> getShipmentByRefNo(String refNo){
        return shipmentRepository.findByRefNo(refNo);
    }

    // 根据ID获取运单
    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> getShipmentById(Long id){
        return shipmentRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Shipment> getShipmentsByStatus(Shipment.ShipmentStatus status) {
        return shipmentRepository.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Shipment> getShipmentsByCustomerId(Long customerId) {
        return shipmentRepository.findByCustomer_Id(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Shipment> getShipmentsByCustomerCode(String customerCode) {
        return shipmentRepository.findByCustomer_Code(customerCode);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Shipment> searchShipments(Long customerId, Shipment.ShipmentStatus status,
                                          LocalDateTime startDate, LocalDateTime endDate,
                                          Pageable pageable) {
        return shipmentRepository.findShipments(customerId, status, startDate, endDate, pageable);
    }

    // 删除运单
    @Override
    public void deleteShipment(Long id) {
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("运单信息不存在"));

        // 检查运单状态 - 可能只有特定状态的运单才能删除
        if (shipment.getStatus() != Shipment.ShipmentStatus.CREATED &&
                shipment.getStatus() != Shipment.ShipmentStatus.CANCELLED) {
            throw new IllegalStateException("只能删除已创建或已取消状态的运单");
        }

        // 检查是否已被分配运输任务等其它业务关联
        // 这里需要根据你的业务规则实现
        // if (shipment.hasAssignedTasks()) {
        //     throw new IllegalStateException("无法删除已分配运输任务的运单");
        // }
        // ToDo

        shipmentRepository.delete(shipment);
    }

    // 检查运单系统参考号是否存在
    @Override
    @Transactional(readOnly = true)
    public boolean existsByRefNo(String refNo){
        return shipmentRepository.existsByRefNo(refNo);
    }

    // 状态转换验证的辅助方法
    private void validateStatusTransition(Shipment.ShipmentStatus current, Shipment.ShipmentStatus next) {
        // 实现你的状态机规则
        // 例如：
        if (current == Shipment.ShipmentStatus.CANCELLED &&
                next != Shipment.ShipmentStatus.CREATED) {
            throw new IllegalStateException("已取消的运单只能重新创建");
        }
        // 添加其他规则...ToDo
    }

}
