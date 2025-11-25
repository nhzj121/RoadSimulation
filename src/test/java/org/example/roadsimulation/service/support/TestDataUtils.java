package org.example.roadsimulation.service.support;

import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.service.ModbusService;

public class TestDataUtils {

    public static Vehicle createTestVehicle(Long id, String licensePlate, Integer modbusSlaveId) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setLicensePlate(licensePlate);
        vehicle.setModbusSlaveId(modbusSlaveId);
        vehicle.setIsOnline(true);
        vehicle.setCurrentLongitude(116.4074);
        vehicle.setCurrentLatitude(39.9042);
        return vehicle;
    }

    /**
     * 创建Modbus寄存器数据的ByteBuf - 使用与ModbusService相同的编码方案
     */
    public static ByteBuf createModbusRegisterData(Long vehicleId, float longitude, float latitude,
                                                   int status, int speed, int fuelLevel) {
        int vehicleIdReg = vehicleId.intValue();

        // 使用与ModbusService相同的编码方案
        int[] encodedLongitude = ModbusService.encodeCoordinate(longitude);
        int[] encodedLatitude = ModbusService.encodeCoordinate(latitude);

        return Unpooled.buffer(16)
                .writeShort(vehicleIdReg)
                .writeShort(encodedLongitude[0])  // 经度整数部分+两位小数
                .writeShort(encodedLongitude[1])  // 经度剩余四位小数
                .writeShort(encodedLatitude[0])   // 纬度整数部分+两位小数
                .writeShort(encodedLatitude[1])   // 纬度剩余四位小数
                .writeShort(status)
                .writeShort(speed)
                .writeShort(fuelLevel);
    }

    /**
     * 创建模拟的ReadHoldingRegistersResponse
     */
    public static ReadHoldingRegistersResponse createMockResponse(ByteBuf data) {
        try {
            // 方法1: 尝试使用单个参数的构造函数
            return new ReadHoldingRegistersResponse(data);
        } catch (Exception e) {
            // 方法2: 如果上面的方法失败，尝试其他方式
            try {
                // 使用反射来创建实例
                Class<?> responseClass = ReadHoldingRegistersResponse.class;
                return (ReadHoldingRegistersResponse) responseClass
                        .getDeclaredConstructor(ByteBuf.class)
                        .newInstance(data.retain());
            } catch (Exception ex) {
                throw new RuntimeException("无法创建Mock响应，请检查Modbus库版本", ex);
            }
        }
    }

    /**
     * 创建包含测试数据的响应
     */
    public static ReadHoldingRegistersResponse createTestResponse(Long vehicleId, float longitude,
                                                                  float latitude, int status,
                                                                  int speed, int fuelLevel) {
        ByteBuf testData = createModbusRegisterData(vehicleId, longitude, latitude, status, speed, fuelLevel);
        return createMockResponse(testData);
    }

    /**
     * 创建超出范围的测试数据 - 用于边界测试
     */
    public static ReadHoldingRegistersResponse createOutOfRangeResponse(Long vehicleId,
                                                                        float longitude,
                                                                        float latitude) {
        // 使用相同的编码方案，即使坐标超出范围
        int[] encodedLongitude = ModbusService.encodeCoordinate(longitude);
        int[] encodedLatitude = ModbusService.encodeCoordinate(latitude);

        ByteBuf testData = Unpooled.buffer(16)
                .writeShort(vehicleId.intValue())
                .writeShort(encodedLongitude[0])
                .writeShort(encodedLongitude[1])
                .writeShort(encodedLatitude[0])
                .writeShort(encodedLatitude[1])
                .writeShort(1)  // 默认状态
                .writeShort(60) // 默认速度
                .writeShort(80);// 默认油量

        return createMockResponse(testData);
    }
}