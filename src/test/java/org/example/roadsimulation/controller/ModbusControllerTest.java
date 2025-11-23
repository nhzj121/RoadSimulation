package org.example.roadsimulation.controller;

import org.example.roadsimulation.service.ModbusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModbusControllerTest {

    @Mock
    private ModbusService modbusService;

    @InjectMocks
    private ModbusController modbusController;

    // 移除全局的 @BeforeEach setUp 方法
    // 改为在每个测试方法中单独设置Mock行为

    @Test
    void testRefreshVehiclePosition_Success() {
        // Given - 只为这个测试设置必要的Mock行为
        doNothing().when(modbusService).triggerPositionUpdate(1L);

        // When
        ResponseEntity<String> response = modbusController.refreshVehiclePosition(1L);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("已触发车辆位置更新", response.getBody());
        verify(modbusService).triggerPositionUpdate(1L);
    }

    @Test
    void testRefreshVehiclePosition_ServiceThrowsException() {
        // Given - 只为这个测试设置必要的Mock行为
        doThrow(new RuntimeException("模拟异常"))
                .when(modbusService).triggerPositionUpdate(1L);

        // When
        ResponseEntity<String> response = modbusController.refreshVehiclePosition(1L);

        // Then
        assertEquals(400, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("更新失败"));
    }

    @Test
    void testSetVehicleOnline_Success() {
        // Given - 只为这个测试设置必要的Mock行为
        doNothing().when(modbusService).setVehicleOnlineStatus(1L, true);

        // When
        ResponseEntity<String> response = modbusController.setVehicleOnline(1L, true);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("设置车辆在线状态成功", response.getBody());
        verify(modbusService).setVehicleOnlineStatus(1L, true);
    }

    @Test
    void testSetVehicleOnline_ServiceThrowsException() {
        // Given - 只为这个测试设置必要的Mock行为
        doThrow(new RuntimeException("模拟异常"))
                .when(modbusService).setVehicleOnlineStatus(1L, true);

        // When
        ResponseEntity<String> response = modbusController.setVehicleOnline(1L, true);

        // Then
        assertEquals(400, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("设置失败"));
    }

    @Test
    void testRefreshAllVehicles() {
        // 这个测试不需要任何Mock设置，因为它不调用任何服务方法
        // 或者如果它调用了服务方法，需要在这里设置相应的Mock行为

        // When
        ResponseEntity<String> response = modbusController.refreshAllVehicles();

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        // 验证没有调用不应该调用的服务方法
        verify(modbusService, never()).triggerPositionUpdate(anyLong());
        verify(modbusService, never()).setVehicleOnlineStatus(anyLong(), anyBoolean());
    }
}