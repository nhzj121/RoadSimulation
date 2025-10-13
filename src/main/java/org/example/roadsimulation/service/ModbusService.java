package org.example.roadsimulation.service;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ModbusService {

    @Autowired
    private ModbusTcpMaster modbusMaster;

    @Autowired
    private VehicleRepository vehicleRepository;

    // Modbus寄存器地址定义
    private static final int VEHICLE_ID_REGISTER = 0;
    private static final int LONGITUDE_HIGH_REGISTER = 1;
    private static final int LONGITUDE_LOW_REGISTER = 2;
    private static final int LATITUDE_HIGH_REGISTER = 3;
    private static final int LATITUDE_LOW_REGISTER = 4;
    private static final int STATUS_REGISTER = 5;
    private static final int SPEED_REGISTER = 6;
    private static final int FUEL_LEVEL_REGISTER = 7;

    /**
     * 定时从Modbus Slave读取所有在线车辆的位置信息
     */
    @Scheduled(fixedRate = 10000) // 每分钟读取一次
    public void updateAllVehiclesPosition() {
        try {
            // 获取所有配置了Modbus Slave ID的车辆
            List<Vehicle> vehicles = vehicleRepository.findByModbusSlaveIdIsNotNull();

            for (Vehicle vehicle : vehicles) {
                if (Boolean.TRUE.equals(vehicle.getIsOnline())) {
                    readVehiclePosition(vehicle);
                    // 添加短暂延迟避免频繁请求
                    Thread.sleep(100);
                }
            }
        } catch (Exception e) {
            log.error("批量更新车辆位置异常: {}", e.getMessage());
        }
    }

    /**
     * 读取单个车辆的位置信息
     */
    public void readVehiclePosition(Vehicle vehicle) {
        if (vehicle.getModbusSlaveId() == null) {
            return;
        }

        try {
            CompletableFuture<ReadHoldingRegistersResponse> future =
                    modbusMaster.sendRequest(
                            new ReadHoldingRegistersRequest(0, 8), // 读取8个寄存器
                            vehicle.getModbusSlaveId()
                    );

            ReadHoldingRegistersResponse response = future.get(2, TimeUnit.SECONDS);

            if (response != null && response.getRegisters() != null) {
                processVehicleData(vehicle, response.getRegisters());
                log.debug("成功读取车辆 {} 的位置数据", vehicle.getLicensePlate());
            }
        } catch (Exception e) {
            log.warn("读取车辆 {} 的Modbus数据失败: {}", vehicle.getLicensePlate(), e.getMessage());
            // 标记车辆离线
            vehicle.setIsOnline(false);
            vehicleRepository.save(vehicle);
        }
    }

    private void processVehicleData(Vehicle vehicle, ByteBuf registers) {
        try {
            // 确保有足够的数据
            if (registers.readableBytes() < 16) { // 8个寄存器 * 2字节
                log.warn("寄存器数据不足，期望16字节，实际: {}", registers.readableBytes());
                return;
            }

            // 验证车辆ID匹配
            int receivedVehicleId = registers.readUnsignedShort();
            if (receivedVehicleId != vehicle.getId().intValue()) {
                log.warn("车辆ID不匹配: 期望 {}, 实际 {}", vehicle.getId(), receivedVehicleId);
                return;
            }

            // 解析经度（32位浮点数）
            int longitudeHigh = registers.readUnsignedShort();
            int longitudeLow = registers.readUnsignedShort();
            double longitude = registersToDouble(longitudeHigh, longitudeLow);

            // 解析纬度（32位浮点数）
            int latitudeHigh = registers.readUnsignedShort();
            int latitudeLow = registers.readUnsignedShort();
            double latitude = registersToDouble(latitudeHigh, latitudeLow);

            // 解析状态
            int statusValue = registers.readUnsignedShort();
            Vehicle.VehicleStatus status = intToVehicleStatus(statusValue);

            // 解析速度（可选）
            Integer speed = null;
            if (registers.readableBytes() >= 2) {
                speed = registers.readUnsignedShort();
            }

            // 解析油量（可选）
            Integer fuelLevel = null;
            if (registers.readableBytes() >= 2) {
                fuelLevel = registers.readUnsignedShort();
            }

            // 更新车辆信息
            updateVehicleData(vehicle, longitude, latitude, status, speed, fuelLevel);

        } catch (Exception e) {
            log.error("处理车辆 {} 的Modbus数据异常: {}", vehicle.getLicensePlate(), e.getMessage());
        }
    }

    private double registersToDouble(int high, int low) {
        // 方法1：大端序（当前方法）
        int bigEndianBits = (high << 16) | (low & 0xFFFF);
        float bigEndianValue = Float.intBitsToFloat(bigEndianBits);

        // 方法2：小端序
        int littleEndianBits = (low << 16) | (high & 0xFFFF);
        float littleEndianValue = Float.intBitsToFloat(littleEndianBits);

        log.debug("字节序测试 - 大端序: {}, 小端序: {}", bigEndianValue, littleEndianValue);

        // 根据实际情况返回正确的值
        // 如果大端序接近预期，返回bigEndianValue
        // 如果小端序接近预期，返回littleEndianValue

        // 返回大端序，根据测试结果调整
        return bigEndianValue;
    }

    private Vehicle.VehicleStatus intToVehicleStatus(int status) {
        switch (status) {
            case 0: return Vehicle.VehicleStatus.IDLE;
            case 1: return Vehicle.VehicleStatus.TRANSPORTING;
            case 2: return Vehicle.VehicleStatus.UNLOADING;
            case 3: return Vehicle.VehicleStatus.MAINTAINING;
            case 4: return Vehicle.VehicleStatus.REFUELING;
            case 5: return Vehicle.VehicleStatus.RESTING;
            case 6: return Vehicle.VehicleStatus.ACCIDENT;
            default: return Vehicle.VehicleStatus.IDLE;
        }
    }

    private void updateVehicleData(Vehicle vehicle, double longitude, double latitude,
                                   Vehicle.VehicleStatus status, Integer speed, Integer fuelLevel) {
        vehicle.setCurrentLongitude(longitude);
        vehicle.setCurrentLatitude(latitude);
        vehicle.setCurrentStatus(status);
        vehicle.setLastPositionUpdate(LocalDateTime.now());
        vehicle.setIsOnline(true);

        vehicleRepository.save(vehicle);

        log.info("更新车辆位置 - 车牌: {}, 经度: {}, 纬度: {}, 状态: {}",
                vehicle.getLicensePlate(),
                String.format("%.6f", longitude),
                String.format("%.6f", latitude),
                status);

        // 可选：记录速度和油量
        if (speed != null) {
            log.debug("车辆 {} 速度: {} km/h", vehicle.getLicensePlate(), speed);
        }
        if (fuelLevel != null) {
            log.debug("车辆 {} 油量: {} %", vehicle.getLicensePlate(), fuelLevel);
        }
    }

    /**
     * 手动触发车辆位置更新
     */
    public void triggerPositionUpdate(Long vehicleId) {
        vehicleRepository.findById(vehicleId).ifPresent(this::readVehiclePosition);
    }

    /**
     * 设置车辆在线状态
     */
    public void setVehicleOnlineStatus(Long vehicleId, boolean isOnline) {
        vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
            vehicle.setIsOnline(isOnline);
            vehicleRepository.save(vehicle);
            log.info("设置车辆 {} 在线状态: {}", vehicle.getLicensePlate(), isOnline);
        });
    }
}