package org.example.roadsimulation;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.service.impl.StateTransitionServiceImpl;
import org.example.roadsimulation.service.StateTransitionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "simulation.init.enabled=false",
        "spring.task.scheduling.enabled=false"
})

public class StateTransitionServiceImplTest {

    @Autowired
    private DataInitializer dataInitializer;  // 注入 DataInitializer 而非使用 MockBean

    private final StateTransitionService stateTransitionService = new StateTransitionServiceImpl();

    // 测试状态转移逻辑
    @Test
    void testSelectNextState() {
        Vehicle.VehicleStatus currentStatus = Vehicle.VehicleStatus.IDLE;  // 当前状态
        Vehicle.VehicleStatus nextState = stateTransitionService.selectNextState(currentStatus);  // 下一状态

        // 验证下一个状态是否合法
        assertNotNull(nextState);
        assertTrue(nextState != Vehicle.VehicleStatus.IDLE);  // 验证状态是否发生了转移
    }

    // 测试带有上下文的状态转移
    @Test
    void testSelectNextStateWithContext() {
        Vehicle vehicle = new Vehicle();
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.ORDER_DRIVING);  // 当前状态
        Vehicle.VehicleStatus nextState = stateTransitionService.selectNextStateWithContext(vehicle);  // 下一状态

        // 根据任务上下文判断状态
        assertNotNull(nextState);
        assertEquals(Vehicle.VehicleStatus.LOADING, nextState);  // 验证根据上下文返回正确的状态
    }

    // 批量状态转移测试
    @Test
    void testBatchSelectNextState() {
        Map<Long, Vehicle.VehicleStatus> currentStates = new HashMap<>();
        currentStates.put(1L, Vehicle.VehicleStatus.IDLE);  // 车辆1状态
        currentStates.put(2L, Vehicle.VehicleStatus.ORDER_DRIVING);  // 车辆2状态

        Map<Long, Vehicle.VehicleStatus> nextStates = stateTransitionService.batchSelectNextState(currentStates);  // 批量处理

        // 批量状态转移的断言
        assertNotNull(nextStates);
        assertEquals(Vehicle.VehicleStatus.ORDER_DRIVING, nextStates.get(1L));  // 车辆1转移到 ORDER_DRIVING
        assertEquals(Vehicle.VehicleStatus.LOADING, nextStates.get(2L));  // 车辆2转移到 LOADING
    }

    // 异常状态转移测试：传入无效状态
    @Test
    void testInvalidStateTransition() {
        // 假设有一个无效的状态枚举
        Vehicle.VehicleStatus currentStatus = null;

        // 确保状态转移系统能够处理这种无效输入
        assertThrows(IllegalArgumentException.class, () -> {
            stateTransitionService.selectNextState(currentStatus);  // 应该抛出异常
        });
    }

    // 集成测试：仿真循环中的状态转移
    @Test
    void testSimulationStateTransition() {
        Vehicle vehicle = new Vehicle();
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.ORDER_DRIVING);  // 当前状态

        // 仿真循环中状态转移
        Vehicle.VehicleStatus nextState = stateTransitionService.selectNextStateWithContext(vehicle);

        // 验证状态转移
        assertNotNull(nextState);
        assertEquals(Vehicle.VehicleStatus.LOADING, nextState);  // 验证是否按预期转移到 LOADING
    }

    // 测试故障/特殊状态时的转移
    @Test
    void testStateTransitionWhenVehicleIsBrokenDown() {
        Vehicle vehicle = new Vehicle();
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.BREAKDOWN);  // 当前状态为故障

        // 假设故障时不能继续运输，应该转移到 WAITING 或其他状态
        Vehicle.VehicleStatus nextState = stateTransitionService.selectNextStateWithContext(vehicle);

        assertNotNull(nextState);
        assertEquals(Vehicle.VehicleStatus.WAITING, nextState);  // 验证转移到 WAITING 状态
    }
}
