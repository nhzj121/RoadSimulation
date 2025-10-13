package org.example.roadsimulation.config;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ModbusConfig {

    @Value("${modbus.slave.host:127.0.0.1}")
    private String slaveHost;

    @Value("${modbus.slave.port:502}")
    private int slavePort;

    @Bean
    public ModbusTcpMaster modbusMaster() {
        ModbusTcpMasterConfig config = new ModbusTcpMasterConfig.Builder(slaveHost)
                .setPort(slavePort)
                .setTimeout(Duration.ofDays(3000))
                .build();
        return new ModbusTcpMaster(config);
    }
}