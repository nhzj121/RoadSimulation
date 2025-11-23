package org.example.roadsimulation.service.support;

import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.example.roadsimulation.entity.Vehicle;

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
     * 创建Modbus寄存器数据的ByteBuf
     */
    public static ByteBuf createModbusRegisterData(Long vehicleId, float longitude, float latitude,
                                                   int status, int speed, int fuelLevel) {
        int vehicleIdReg = vehicleId.intValue();

        // 将浮点数转换为32位整数，然后拆分为两个16位寄存器
        int longitudeBits = Float.floatToIntBits(longitude);
        int lonHigh = (longitudeBits >> 16) & 0xFFFF;
        int lonLow = longitudeBits & 0xFFFF;

        int latitudeBits = Float.floatToIntBits(latitude);
        int latHigh = (latitudeBits >> 16) & 0xFFFF;
        int latLow = latitudeBits & 0xFFFF;

        return Unpooled.buffer(16)
                .writeShort(vehicleIdReg)
                .writeShort(lonHigh)
                .writeShort(lonLow)
                .writeShort(latHigh)
                .writeShort(latLow)
                .writeShort(status)
                .writeShort(speed)
                .writeShort(fuelLevel);
    }

    /**
     * 创建模拟的ReadHoldingRegistersResponse
     * 根据digitalpetri库版本，构造函数可能不同
     */
    public static ReadHoldingRegistersResponse createMockResponse(ByteBuf data) {
        try {
            // 方法1: 尝试使用单个参数的构造函数
            return new ReadHoldingRegistersResponse(data);
        } catch (Exception e) {
            // 方法2: 如果上面的方法失败，尝试其他方式
            try {
                // 使用反射或其他方式创建响应对象
                return createMockResponseAlternative(data);
            } catch (Exception ex) {
                throw new RuntimeException("无法创建Mock响应，请检查Modbus库版本", ex);
            }
        }
    }

    /**
     * 备用的Mock响应创建方法
     */
    private static ReadHoldingRegistersResponse createMockResponseAlternative(ByteBuf data) {
        try {
            // 使用反射来创建实例
            Class<?> responseClass = ReadHoldingRegistersResponse.class;
            return (ReadHoldingRegistersResponse) responseClass
                    .getDeclaredConstructor(ByteBuf.class)
                    .newInstance(data.retain());
        } catch (Exception e) {
            throw new RuntimeException("备用的Mock响应创建也失败了", e);
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
}