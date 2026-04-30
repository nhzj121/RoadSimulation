package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.EnrollmentRepository;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.example.roadsimulation.dto.RouteMetricsResponse;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.ShipmentRepository;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.GaodeMapService;
import org.example.roadsimulation.service.ShipmentService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final VehicleRepository vehicleRepository;
    private final GaodeMapService gaodeMapService;

    @Autowired
    public ShipmentServiceImpl(ShipmentRepository shipmentRepository,
                               VehicleRepository vehicleRepository,
                               GaodeMapService gaodeMapService,
                               POIRepository poiRepository,
                               EnrollmentRepository enrollmentRepository,
                               GoodsRepository goodsRepository,
                               DataInitializer dataInitializer) {
        this.shipmentRepository = shipmentRepository;
        this.poiRepository = poiRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.goodsRepository = goodsRepository;
        this.dataInitializer = dataInitializer;
        this.vehicleRepository = vehicleRepository;
        this.gaodeMapService = gaodeMapService;
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

                    // 如需允许修改起点终点，可放开这两行
                    shipment.setOriginPOI(shipmentDetails.getOriginPOI());
                    shipment.setDestPOI(shipmentDetails.getDestPOI());

                    return shipmentRepository.save(shipment);
                })
                .orElseThrow(() -> new RuntimeException("运单不存在，ID: " + id));
    }

    @Override
    public Shipment updateStatus(Long id, Shipment.ShipmentStatus newStatus) {
        return shipmentRepository.findById(id)
                .map(shipment -> {
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

    @Override
    public RouteMetricsResponse calculateAndStoreRouteMetrics(Long shipmentId, Long vehicleId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("运单不存在，ID: " + shipmentId));

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("车辆不存在，ID: " + vehicleId));

        validateCoordinates(vehicle, shipment);

        // 1. 空驶：车辆当前位置 -> 运单起点
        String vehicleLocation = buildLocation(vehicle.getCurrentLongitude(), vehicle.getCurrentLatitude());
        String shipmentOrigin = buildLocation(
                shipment.getOriginPOI().getLongitude(),
                shipment.getOriginPOI().getLatitude()
        );

        GaodeRouteResponse emptyRouteResponse = gaodeMapService.planDrivingRoute(
                new GaodeRouteRequest(vehicleLocation, shipmentOrigin)
        );

        if (!emptyRouteResponse.isSuccess() || emptyRouteResponse.getData() == null) {
            throw new RuntimeException("空驶路线计算失败: " + emptyRouteResponse.getMessage());
        }

        Double emptyDistance = emptyRouteResponse.getData().getTotalDistance();
        Double emptyDuration = emptyRouteResponse.getData().getTotalDuration();

        vehicle.setEmptyDrivingDistance(emptyDistance);
        vehicle.setEmptyDrivingTime(emptyDuration == null ? null : emptyDuration.longValue());

        // 2. 总行驶：运单起点 -> 运单终点
        String shipmentDestination = buildLocation(
                shipment.getDestPOI().getLongitude(),
                shipment.getDestPOI().getLatitude()
        );

        GaodeRouteResponse totalRouteResponse = gaodeMapService.planDrivingRoute(
                new GaodeRouteRequest(shipmentOrigin, shipmentDestination)
        );

        if (!totalRouteResponse.isSuccess() || totalRouteResponse.getData() == null) {
            throw new RuntimeException("总行驶路线计算失败: " + totalRouteResponse.getMessage());
        }

        Double totalDistance = totalRouteResponse.getData().getTotalDistance();
        Double totalDuration = totalRouteResponse.getData().getTotalDuration();

        shipment.setTotalDrivingDistance(totalDistance);
        shipment.setTotalDrivingTime(totalDuration == null ? null : totalDuration.longValue());

        vehicleRepository.save(vehicle);
        shipmentRepository.save(shipment);

        RouteMetricsResponse response = new RouteMetricsResponse();
        response.setVehicleId(vehicleId);
        response.setShipmentId(shipmentId);
        response.setEmptyDrivingDistance(vehicle.getEmptyDrivingDistance());
        response.setEmptyDrivingTime(vehicle.getEmptyDrivingTime());
        response.setTotalDrivingDistance(shipment.getTotalDrivingDistance());
        response.setTotalDrivingTime(shipment.getTotalDrivingTime());

        return response;
    }

    // 删除运单
    @Override
    public void deleteShipment(Long id) {
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("运单信息不存在"));

        // 检查运单状态
        if (shipment.getStatus() != Shipment.ShipmentStatus.CREATED &&
                shipment.getStatus() != Shipment.ShipmentStatus.CANCELLED) {
            throw new IllegalStateException("只能删除已创建或已取消状态的运单");
        }

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
        if (current == Shipment.ShipmentStatus.CANCELLED &&
                next != Shipment.ShipmentStatus.CREATED) {
            throw new IllegalStateException("已取消的运单只能重新创建");
        }
    }

    private void validateCoordinates(Vehicle vehicle, Shipment shipment) {
        if (vehicle.getCurrentLongitude() == null || vehicle.getCurrentLatitude() == null) {
            throw new IllegalArgumentException("车辆当前经纬度为空，无法计算空驶路线");
        }

        if (shipment.getOriginPOI() == null) {
            throw new IllegalArgumentException("运单起点为空，无法计算路线");
        }

        if (shipment.getDestPOI() == null) {
            throw new IllegalArgumentException("运单终点为空，无法计算路线");
        }

        validatePoiCoordinates(shipment.getOriginPOI(), "运单起点");
        validatePoiCoordinates(shipment.getDestPOI(), "运单终点");
    }

    private void validatePoiCoordinates(POI poi, String poiName) {
        if (poi.getLongitude() == null || poi.getLatitude() == null) {
            throw new IllegalArgumentException(poiName + "经纬度为空，无法计算路线");
        }
    }

    private String buildLocation(BigDecimal longitude, BigDecimal latitude) {
        return longitude.toPlainString() + "," + latitude.toPlainString();
    }

    // ToDo 出于演示需要，这里的生成逻辑先是本地的，后续需要改成 加工链
    // 1. 定义加工链规则（实际项目中可以从数据库配置表里读，这里为了直观用内部类模拟）
    private record TransportRule(POI.POIType startType, POI.POIType endType, String goodsSku) {}

    // 初始化支持的完整加工链
    private final List<TransportRule> VALID_RULES = List.of(
            new TransportRule(POI.POIType.TIMBER_YARD, POI.POIType.SAWMILL, "LOG"), // 原木 -> 锯木厂
            new TransportRule(POI.POIType.IRON_MINE, POI.POIType.STEEL_MILL, "IRON_ORE"),   // 玻璃厂 -> 仓库
            new TransportRule(POI.POIType.RUBBER_PROCESSING_PLANT, POI.POIType.TIRE_MANUFACTURING_PLANT, "RUBBER_SEMI") // 菜基地 -> 菜市场
            // 以后新增规则，只需加在这里或数据库里
    );

    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<Shipment> batchGenerateShipments(int count) {
        if (count <= 0) return Collections.emptyList();

        Random random = new Random();
        List<Shipment> shipmentsToSave = new ArrayList<>(count);
        Map<String, Enrollment> enrollmentMap = new HashMap<>();

        // 核心优化：使用 Map 做方法级别的本地缓存，避免同一种类型的 POI 被重复查库
        Map<POI.POIType, List<POI>> poiCache = new EnumMap<>(POI.POIType.class);
        Map<String, Goods> goodsCache = new HashMap<>();
        int generated = 0;
        // 【优化】设置最大尝试次数，防止因数据库缺少某些基础数据导致死循环
        int maxRetries = count * 3;
        int attempts = 0;

        // 【修复2】逻辑漏洞：改用 while 循环，确保实际生成的数量达到预期
        while (generated < count && attempts < maxRetries) {
            attempts++;
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
            Enrollment enrollment = enrollmentMap.computeIfAbsent(enrollKey, k ->
                    enrollmentRepository.findByPoiAndGoods(startPOI, goods)
                            .orElse(new Enrollment(startPOI, goods, 0))
            );
            enrollment.setQuantity(enrollment.getQuantity() + quantity);

            startPOI.addGoodsEnrollment(enrollment);
            goods.addPOIEnrollment(enrollment);

            // 6. 生成运单
            String refNo = generateUniqueRefNo(goods.getSku(), generated);
            Shipment shipment = new Shipment(refNo, startPOI, endPOI, totalWeight, totalVolume);
            shipment.setStatus(Shipment.ShipmentStatus.CREATED);

            shipmentsToSave.add(shipment);
            generated++; // 成功生成一个，计数器加1
        }

        // 7. 统一落库
        if (!enrollmentMap.isEmpty()) {
            enrollmentRepository.saveAll(enrollmentMap.values());
        }
        if (!shipmentsToSave.isEmpty()) {
            shipmentRepository.saveAll(shipmentsToSave);
        }

        return shipmentsToSave;
    }

    private String generateUniqueRefNo(String sku, int index) {
        // 内部消化单号生成逻辑，格式：SKU_时间戳_6位随机数
        String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
        String randomStr = String.format("%03d", new java.util.Random().nextInt(1000));
        return sku + "_" + timestamp + "_" + String.format("%04d", index) + randomStr;
    }

    private Integer generateRandomQuantity(Random random) {
        return random.nextInt(10) + 5;
    }

}
