<<<<<<< Updated upstream
//package org.example.roadsimulation.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.scheduling.annotation.EnableScheduling;
//
//@Configuration
//@EnableScheduling
//public class TimeModuleConfig {
//
//    @Bean
//    public SimulationTimer simulationTimer() {
//        return new SimulationTimer();
//    }
//}
=======
package org.example.roadsimulation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 时间/调度配置
 *
 * ✅ 保留 @EnableScheduling：用于启用 SimulationMainLoop 的 @Scheduled 主循环
 * ❌ 不再注册旧的 SimulationTimer：避免与主循环双驱动
 */
@Configuration
@EnableScheduling
public class TimeModuleConfig {
    // 主循环模式无需额外 Bean
}
>>>>>>> Stashed changes
