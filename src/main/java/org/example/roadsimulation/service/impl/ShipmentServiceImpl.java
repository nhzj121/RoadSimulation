package org.example.roadsimulation.service.impl;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final VehicleRepository vehicleRepository;
    private final GaodeMapService gaodeMapService;

    @Autowired
    public ShipmentServiceImpl(ShipmentRepository shipmentRepository,
                               VehicleRepository vehicleRepository,
                               GaodeMapService gaodeMapService) {
        this.shipmentRepository = shipmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.gaodeMapService = gaodeMapService;
    }

    @Override
    public Shipment createShipment(@NotNull Shipment shipment) {
        // 检查系统参考号是否已存在
        if (shipmentRepository.existsByRefNo(shipment.getRefNo())) {
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
    public List<Shipment> getAllShipments() {
        return shipmentRepository.findAll();
    }

    // 分页获取运单
    @Override
    @Transactional(readOnly = true)
    public Page<Shipment> getAllShipments(Pageable pageable) {
        return shipmentRepository.findAll(pageable);
    }

    // 根据系统参考号获取运单
    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> getShipmentByRefNo(String refNo) {
        return shipmentRepository.findByRefNo(refNo);
    }

    // 根据ID获取运单
    @Override
    @Transactional(readOnly = true)
    public Optional<Shipment> getShipmentById(Long id) {
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

        Integer emptyDistance = emptyRouteResponse.getData().getTotalDistance();
        Integer emptyDuration = emptyRouteResponse.getData().getTotalDuration();

        vehicle.setEmptyDrivingDistance(emptyDistance == null ? null : emptyDistance.doubleValue());
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

        Integer totalDistance = totalRouteResponse.getData().getTotalDistance();
        Integer totalDuration = totalRouteResponse.getData().getTotalDuration();

        shipment.setTotalDrivingDistance(totalDistance == null ? null : totalDistance.doubleValue());
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
    public boolean existsByRefNo(String refNo) {
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
}
