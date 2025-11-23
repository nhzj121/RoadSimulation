package org.example.roadsimulation.performance;

import org.example.roadsimulation.service.ModbusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ModbusPerformanceTest {

    @Autowired
    private ModbusService modbusService;

    @Test
    void testConcurrentVehicleUpdates() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numberOfThreads; i++) {
            final int vehicleId = i;
            service.submit(() -> {
                try {
                    // 模拟并发更新不同车辆
                    modbusService.triggerPositionUpdate((long) vehicleId);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有任务完成或超时
        assertTrue(latch.await(30, TimeUnit.SECONDS));

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("并发更新 " + numberOfThreads + " 辆车耗时: " + totalTime + "ms");
        assertTrue(totalTime < 10000, "并发性能应该在10秒内完成");
    }
}