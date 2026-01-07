// SimulationMainLoop.java
package org.example.roadsimulation;

import org.example.roadsimulation.DataInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 仿真主循环 - 控制中心
 * 职责：控制所有仿真模块的执行节奏
 */
@Component
public class SimulationMainLoop {

    private DataInitializer dataInitializer;

    @Autowired
    SimulationMainLoop(DataInitializer dataInitializer) {
        this.dataInitializer = dataInitializer;
    }

    // 循环计数器
    private int loopCount = 0;

    // 运行状态
    private boolean isRunning = false;

    // 每个循环代表的分钟数
    private final int MINUTES_PER_LOOP = 30;

    /**
     * 主循环方法 - 每5秒执行一次循环
     */
    @Scheduled(fixedRate = 5000)  // 每5秒一次循环
    public void executeMainLoop() {
        // ToDo 控制主循环的启动与否，前端使用API进行控制，这里处于测试需要先注释掉
        if (!isRunning) {
            return;
        }
        if(loopCount == 0){
            loopCount++;
            return;
        }

        System.out.println("=== 主循环第 " + loopCount + " 次 ===");
        System.out.println("模拟时间: " + (loopCount * MINUTES_PER_LOOP / 60.0) + " 小时");

        // === 这里就是控制中心 ===
        // 1. 每2个循环生成一次货物（每1小时）
        if (loopCount % 2 == 0) {
            System.out.println(">>> 执行货物生成逻辑");
            dataInitializer.generateGoods(loopCount);
        }

        // 2. 每4个循环运出一次货物（每2小时）
        if (loopCount % 4 == 0) {
            System.out.println(">>> 执行货物运出逻辑");
            dataInitializer.shipOutGoods(loopCount);
        }

        // 3. 每10个循环打印一次状态（每5小时）
        if (loopCount % 10 == 0) {
            System.out.println(">>> 打印仿真状态");
            dataInitializer.printSimulationStatus(loopCount);
        }

        // 4. 这里可以添加其他模块的调用
        // if (loopCount % 3 == 0) {
        //     vehicleService.updateVehicles(loopCount);
        // }
        // ======================


        loopCount++;
    }

    /**
     * 启动仿真
     */
    public void start() {
        isRunning = true;
        System.out.println("仿真主循环已启动");
    }

    /**
     * 停止仿真
     */
    public void stop() {
        isRunning = false;
        System.out.println("仿真主循环已停止");
    }

    /**
     * 单步执行
     */
    public void step() {
        if (isRunning) {
            System.out.println("请先停止仿真再进行单步执行");
            return;
        }
        isRunning = true;
        executeMainLoop();  // 执行一次
        isRunning = false;
    }

    /**
     * 重置仿真
     */
    public void reset() {
        loopCount = 0;
        isRunning = false;
        System.out.println("仿真已重置");
    }

    /**
     * 获取当前循环次数
     */
    public int getLoopCount() {
        return loopCount;
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
}