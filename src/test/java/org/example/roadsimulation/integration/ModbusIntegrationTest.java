package org.example.roadsimulation.integration;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import org.example.roadsimulation.config.ModbusConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "modbus.slave.host=127.0.0.1",
        "modbus.slave.port=502"
})
class ModbusIntegrationTest {

    @Autowired
    private ModbusConfig modbusConfig;

    @Test
    void testModbusMasterCreation() {
        // When
        ModbusTcpMaster master = modbusConfig.modbusMaster();

        // Then
        assertNotNull(master);
        // 可以验证连接配置等
    }

    @Test
    void testModbusServiceScheduledTask() throws InterruptedException {
        // 这个测试需要实际的Modbus从站运行
        // 可以验证定时任务是否按预期执行
        // 但由于依赖外部服务，可能需要在特定环境下运行

        Thread.sleep(15000); // 等待超过10秒的调度间隔
        // 验证车辆位置是否被更新
    }
}