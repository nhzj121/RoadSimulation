package org.example.roadsimulation.controller;

import org.example.roadsimulation.service.ModbusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/modbus")
public class ModbusController {

    @Autowired
    private ModbusService modbusService;

    @PostMapping("/vehicle/{vehicleId}/refresh")
    public ResponseEntity<String> refreshVehiclePosition(@PathVariable Long vehicleId) {
        try {
            modbusService.triggerPositionUpdate(vehicleId);
            return ResponseEntity.ok("已触发车辆位置更新");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("更新失败: " + e.getMessage());
        }
    }

    @PostMapping("/vehicle/{vehicleId}/online")
    public ResponseEntity<String> setVehicleOnline(@PathVariable Long vehicleId,
                                                   @RequestParam boolean online) {
        try {
            modbusService.setVehicleOnlineStatus(vehicleId, online);
            return ResponseEntity.ok("设置车辆在线状态成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("设置失败: " + e.getMessage());
        }
    }

    @PostMapping("/refresh-all")
    public ResponseEntity<String> refreshAllVehicles() {
        try {
            // 这里可以直接调用服务的方法，但由于是定时任务，我们可以返回提示
            return ResponseEntity.ok("系统会自动定时更新车辆位置，如需立即更新请等待下次调度");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("操作失败: " + e.getMessage());
        }
    }
}