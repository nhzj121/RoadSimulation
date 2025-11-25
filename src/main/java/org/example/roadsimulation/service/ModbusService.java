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
    @Scheduled(fixedRate = 9000) // 每分钟读取一次
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

            // 解析经度（按照新方案）
            int longitudeIntegerWithTwoDecimals = registers.readUnsignedShort();
            int longitudeRemainingFourDecimals = registers.readUnsignedShort();
            double longitude = parseCoordinate(longitudeIntegerWithTwoDecimals, longitudeRemainingFourDecimals);

            // 解析纬度（按照新方案）
            int latitudeIntegerWithTwoDecimals = registers.readUnsignedShort();
            int latitudeRemainingFourDecimals = registers.readUnsignedShort();
            double latitude = parseCoordinate(latitudeIntegerWithTwoDecimals, latitudeRemainingFourDecimals);

            // 验证经度范围 (-180 到 180)
            if (longitude < -180.0 || longitude > 180.0) {
                log.error("经度值超出有效范围: {}，有效范围为 -180 到 180", longitude);
                return;
            }

            // 验证纬度范围 (-90 到 90)
            if (latitude < -90.0 || latitude > 90.0) {
                log.error("纬度值超出有效范围: {}，有效范围为 -90 到 90", latitude);
                return;
            }

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

    /**
     * 解析坐标值（经度/纬度）
     * @param integerWithTwoDecimals 整数部分 + 两位小数
     * @param remainingFourDecimals 剩余四位小数，以1开头
     * @return 完整的坐标值
     */
    public double parseCoordinate(int integerWithTwoDecimals, int remainingFourDecimals) {
        try {
            // 解析整数部分和两位小数
            double mainPart = integerWithTwoDecimals / 100.0;

            // 解析剩余四位小数
            String remainingStr = String.valueOf(remainingFourDecimals);

            // 检查是否以1开头，如果是则去掉开头的1
            if (remainingStr.startsWith("1")) {
                remainingStr = remainingStr.substring(1);
            }

            // 补零到4位
            while (remainingStr.length() < 4) {
                remainingStr += "0";
            }

            // 将剩余部分转换为小数（除以10000得到4位小数）
            double decimalPart = Integer.parseInt(remainingStr) / 1000000.0;

            double result = mainPart + decimalPart;

            log.debug("坐标解析 - 主部分: {}, 剩余小数: {}, 完整值: {}",
                    mainPart, decimalPart, result);

            return result;
        } catch (Exception e) {
            log.error("坐标解析错误 - 输入: {}, {}, 错误: {}",
                    integerWithTwoDecimals, remainingFourDecimals, e.getMessage());
            return 0.0;
        }
    }
    /**
     * 将坐标值编码为两个Modbus寄存器值
     * @param coordinate 坐标值（如123.456789）
     * @return 包含两个整数的数组 [integerWithTwoDecimals, remainingFourDecimals]
     */
    public static int[] encodeCoordinate(double coordinate) {
        // 将坐标乘以1000000得到整数
        long scaledValue = Math.round(coordinate * 1000000);

        // 提取前5位数字（整数部分 + 2位小数）
        int integerWithTwoDecimals = (int) (scaledValue / 10000);

        // 提取后4位数字（剩余4位小数）
        int remainingFourDecimals = (int) (scaledValue % 10000);

        // 在剩余小数前补1，避免前导零
        remainingFourDecimals += 10000;

        return new int[]{integerWithTwoDecimals, remainingFourDecimals};
    }

    private double registersToDouble(int integerWithTwoDecimals, int remainingFourDecimals) {
        // 解析整数部分和两位小数
        double mainPart = integerWithTwoDecimals / 100.0;

        // 解析剩余四位小数
        // 因为remainingFourDecimals以1开头，我们需要去掉开头的1
        StringBuilder remainingStr = new StringBuilder(String.valueOf(remainingFourDecimals));
        if (remainingStr.toString().startsWith("1")) {
            remainingStr = new StringBuilder(remainingStr.substring(1));
        }

        // 补零到4位
        while (remainingStr.length() < 4) {
            remainingStr.append("0");
        }

        double decimalPart = Integer.parseInt(remainingStr.toString()) / 1000000.0;

        return mainPart + decimalPart;
    }

    public double testRegistersToDouble(int integerWithTwoDecimals, int remainingFourDecimals) {
        // 解析整数部分和两位小数
        double mainPart = integerWithTwoDecimals / 100.0;

        // 解析剩余四位小数
        // 因为remainingFourDecimals以1开头，我们需要去掉开头的1
        StringBuilder remainingStr = new StringBuilder(String.valueOf(remainingFourDecimals));
        if (remainingStr.toString().startsWith("1")) {
            remainingStr = new StringBuilder(remainingStr.substring(1));
        }

        // 补零到4位
        while (remainingStr.length() < 4) {
            remainingStr.append("0");
        }

        double decimalPart = Integer.parseInt(remainingStr.toString()) / 1000000.0;

        return mainPart + decimalPart;
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

    public Vehicle.VehicleStatus testIntToVehicleStatus(int status) {
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