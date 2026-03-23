package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.EnrollmentRepository;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.service.ShipmentService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class ShipmentServiceImpl implements ShipmentService {
    private final ShipmentRepository shipmentRepository;
    private final POIRepository poiRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final GoodsRepository goodsRepository;
    private final DataInitializer dataInitializer;

    @Autowired
    public ShipmentServiceImpl(ShipmentRepository shipmentRepository,
                               POIRepository poiRepository,
                               EnrollmentRepository enrollmentRepository,
                               GoodsRepository goodsRepository,
                               DataInitializer dataInitializer) {
        this.shipmentRepository = shipmentRepository;
        this.poiRepository = poiRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.goodsRepository = goodsRepository;
        this.dataInitializer = dataInitializer;
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

    // ToDo 出于演示需要，这里的生成逻辑先是本地的，后续需要改成 加工链
    // 1. 定义加工链规则（实际项目中可以从数据库配置表里读，这里为了直观用内部类模拟）
    private record TransportRule(POI.POIType startType, POI.POIType endType, String goodsSku) {}

    // 初始化支持的完整加工链
    private final List<TransportRule> VALID_RULES = List.of(
            new TransportRule(POI.POIType.TIMBER_YARD, POI.POIType.SAWMILL, "00001"), // 原木 -> 锯木厂
            new TransportRule(POI.POIType.IRON_MINE, POI.POIType.STEEL_MILL, "00002"),   // 玻璃厂 -> 仓库
            new TransportRule(POI.POIType.RUBBER_PROCESSING_PLANT, POI.POIType.TIRE_MANUFACTURING_PLANT, "00003") // 菜基地 -> 菜市场
            // 以后新增规则，只需加在这里或数据库里
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchGenerateShipments(int count) {
        if (count <= 0) return;

        Random random = new Random();
        List<Shipment> shipmentsToSave = new ArrayList<>(count);
        Map<Long, Enrollment> enrollmentMap = new HashMap<>();

        // 核心优化：使用 Map 做方法级别的本地缓存，避免同一种类型的 POI 被重复查库
        Map<POI.POIType, List<POI>> poiCache = new EnumMap<>(POI.POIType.class);
        Map<String, Goods> goodsCache = new HashMap<>();

        for (int i = 0; i < count; i++) {
            // 1. 随机命中一条加工链规则
            TransportRule rule = VALID_RULES.get(random.nextInt(VALID_RULES.size()));

            // 2. 从缓存获取起点和终点列表，如果没有再查库 (Lazy Loading 思维)
            List<POI> startPOIs = poiCache.computeIfAbsent(rule.startType(), poiRepository::findByPoiType);
            List<POI> endPOIs = poiCache.computeIfAbsent(rule.endType(), poiRepository::findByPoiType);

            // 3. 从缓存获取货物
            Goods goods = goodsCache.computeIfAbsent(rule.goodsSku(), sku ->
                    goodsRepository.findBySku(sku)
                            .orElseThrow(() -> new RuntimeException("缺失关键货物 SKU: " + sku))
            );

            // 校验：如果当前规则对应的起点或终点在数据库里还没建，跳过本次生成
            if (startPOIs.isEmpty() || endPOIs.isEmpty()) {
                continue;
            }

            // 4. 在内存中极速随机挑选具体的起点和终点
            POI startPOI = startPOIs.get(random.nextInt(startPOIs.size()));
            POI endPOI = endPOIs.get(random.nextInt(endPOIs.size()));

            Integer quantity = generateRandomQuantity(random);
            Double totalWeight = quantity * goods.getWeightPerUnit();
            Double totalVolume = quantity * goods.getVolumePerUnit();

            // 5. 聚合库存 (复合键防止冲突)
            // 这里需要注意：不同的规则如果在同一个起点产生了相同的货物，依然能正确累加
            String enrollKey = startPOI.getId() + "_" + goods.getId();
            Enrollment enrollment = enrollmentMap.computeIfAbsent(startPOI.getId(), k ->
                    enrollmentRepository.findByPoiAndGoods(startPOI, goods)
                            .orElse(new Enrollment(startPOI, goods, 0))
            );
            enrollment.setQuantity(enrollment.getQuantity() + quantity);

            startPOI.addGoodsEnrollment(enrollment);
            goods.addPOIEnrollment(enrollment);

            // 6. 生成运单
            String refNo = generateUniqueRefNo(goods.getSku());
            Shipment shipment = new Shipment(refNo, startPOI, endPOI, totalWeight, totalVolume);
            shipment.setStatus(Shipment.ShipmentStatus.CREATED);

            shipmentsToSave.add(shipment);
        }

        // 7. 统一落库
        if (!enrollmentMap.isEmpty()) {
            enrollmentRepository.saveAll(enrollmentMap.values());
        }
        if (!shipmentsToSave.isEmpty()) {
            shipmentRepository.saveAll(shipmentsToSave);
        }
    }

    private Integer generateRandomQuantity(Random random) {
        // 保持原有的逻辑：生成 50 - 299 之间的随机数
        return random.nextInt(250) + 50;
    }

    private String generateUniqueRefNo(String sku) {
        // 内部消化单号生成逻辑，格式：SKU_时间戳_6位随机数
        String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
        String randomStr = String.format("%06d", new java.util.Random().nextInt(1000000));
        return sku + "_" + timestamp + "_" + randomStr;
    }

}
