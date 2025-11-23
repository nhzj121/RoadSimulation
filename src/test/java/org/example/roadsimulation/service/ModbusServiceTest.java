package org.example.roadsimulation.service;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import io.netty.buffer.ByteBuf;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.support.TestDataUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModbusServiceTest {

    @Mock
    private ModbusTcpMaster modbusMaster;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private ModbusService modbusService;

    @Captor
    private ArgumentCaptor<ReadHoldingRegistersRequest> requestCaptor;

    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        testVehicle = TestDataUtils.createTestVehicle(1L, "京A12345", 1);
    }

    @Test
    void testReadVehiclePosition_Success() throws Exception {
        // Given - 创建测试数据
        ReadHoldingRegistersResponse mockResponse = TestDataUtils.createTestResponse(
                1L, 116.4074f, 39.9042f, 1, 60, 80);

        CompletableFuture<ReadHoldingRegistersResponse> future =
                CompletableFuture.completedFuture(mockResponse);

        doReturn(future).when(modbusMaster)
                .sendRequest(any(ReadHoldingRegistersRequest.class), eq(1));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(testVehicle);

        // When
        modbusService.readVehiclePosition(testVehicle);

        // Then
        verify(modbusMaster).sendRequest(requestCaptor.capture(), eq(1));
        ReadHoldingRegistersRequest request = requestCaptor.getValue();
        assertEquals(0, request.getAddress());
        assertEquals(8, request.getQuantity());

        verify(vehicleRepository, atLeastOnce()).save(any(Vehicle.class));
    }

    @Test
    void testReadVehiclePosition_Timeout() throws Exception {
        // Given - 模拟超时
        CompletableFuture<ReadHoldingRegistersResponse> timeoutFuture =
                new CompletableFuture<>();

        doReturn(timeoutFuture).when(modbusMaster)
                .sendRequest(any(ReadHoldingRegistersRequest.class), eq(1));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(testVehicle);

        // When
        modbusService.readVehiclePosition(testVehicle);

        // Then - 车辆应该被标记为离线
        verify(vehicleRepository).save(argThat(vehicle ->
                !vehicle.getIsOnline()
        ));
    }

    @Test
    void testReadVehiclePosition_NoSlaveId() {
        // Given
        testVehicle.setModbusSlaveId(null);

        // When
        modbusService.readVehiclePosition(testVehicle);

        // Then - 不应该发送Modbus请求
        verify(modbusMaster, never()).sendRequest(any(), anyInt());
    }

    @Test
    void testProcessVehicleData_VehicleIdMismatch() throws Exception {
        // Given - 车辆ID不匹配的数据
        ReadHoldingRegistersResponse mismatchedResponse = TestDataUtils.createTestResponse(
                999L, 116.4074f, 39.9042f, 1, 60, 80);

        CompletableFuture<ReadHoldingRegistersResponse> future =
                CompletableFuture.completedFuture(mismatchedResponse);

        doReturn(future).when(modbusMaster)
                .sendRequest(any(ReadHoldingRegistersRequest.class), eq(1));

        // When
        modbusService.readVehiclePosition(testVehicle);

        // Then - 不应该保存数据
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void testProcessVehicleData_InsufficientData() throws Exception {
        // Given - 数据不足（只有4个寄存器）
        ByteBuf insufficientData = TestDataUtils.createModbusRegisterData(
                1L, 116.4074f, 39.9042f, 1, 60, 80);
        insufficientData.readerIndex(8); // 跳过一半数据，模拟数据不足

        ReadHoldingRegistersResponse mockResponse = TestDataUtils.createMockResponse(insufficientData);

        CompletableFuture<ReadHoldingRegistersResponse> future =
                CompletableFuture.completedFuture(mockResponse);

        doReturn(future).when(modbusMaster)
                .sendRequest(any(ReadHoldingRegistersRequest.class), eq(1));

        // When
        modbusService.readVehiclePosition(testVehicle);

        // Then - 不应该保存数据
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void testRegistersToDouble_Endianness() {
        // 测试用例1: 123.456789
        int[] encoded1 = ModbusService.encodeCoordinate(123.456789);
        double result1 = modbusService.parseCoordinate(encoded1[0], encoded1[1]);
        assertEquals(123.456789, result1, 0.000001, "123.456789 解析应该正确");

        // 测试用例2: 23.456789
        int[] encoded2 = ModbusService.encodeCoordinate(23.456789);
        double result2 = modbusService.parseCoordinate(encoded2[0], encoded2[1]);
        assertEquals(23.456789, result2, 0.000001, "23.456789 解析应该正确");

        // 测试用例3: 123.450780
        int[] encoded3 = ModbusService.encodeCoordinate(123.450780);
        double result3 = modbusService.parseCoordinate(encoded3[0], encoded3[1]);
        assertEquals(123.450780, result3, 0.000001, "123.450780 解析应该正确");

        // 测试用例4: 边界值测试 - 180.000000
        int[] encoded4 = ModbusService.encodeCoordinate(180.000000);
        double result4 = modbusService.parseCoordinate(encoded4[0], encoded4[1]);
        assertEquals(180.000000, result4, 0.000001, "180.000000 解析应该正确");

        // 测试用例5: 边界值测试 - 0.000001
        int[] encoded5 = ModbusService.encodeCoordinate(0.000001);
        double result5 = modbusService.parseCoordinate(encoded5[0], encoded5[1]);
        assertEquals(0.000001, result5, 0.000001, "0.000001 解析应该正确");
    }

    @Test
    void testEncodeCoordinate() {
        // 测试编码过程
        int[] encoded = ModbusService.encodeCoordinate(123.456789);

        // 验证编码结果
        assertEquals(12345, encoded[0], "整数部分+两位小数应该为12345");
        assertEquals(16789, encoded[1], "剩余四位小数应该为16789（10000 + 6789）");

        // 测试解码过程
        double result = modbusService.parseCoordinate(encoded[0], encoded[1]);
        assertEquals(123.456789, result, 0.000001, "编码再解码应该得到原值");
    }

    @Test
    void testIntToVehicleStatus() {
        // 测试状态转换
        assertEquals(Vehicle.VehicleStatus.IDLE, modbusService.testIntToVehicleStatus(0));
        assertEquals(Vehicle.VehicleStatus.TRANSPORTING, modbusService.testIntToVehicleStatus(1));
        assertEquals(Vehicle.VehicleStatus.UNLOADING, modbusService.testIntToVehicleStatus(2));
        assertEquals(Vehicle.VehicleStatus.ACCIDENT, modbusService.testIntToVehicleStatus(6));
        assertEquals(Vehicle.VehicleStatus.IDLE, modbusService.testIntToVehicleStatus(99)); // 默认值
    }
}