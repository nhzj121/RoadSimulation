package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController // 1. 表明这是一个RESTful控制器
@RequestMapping("/api/vehicles") // 2. 为整个控制器设置基础路径
public class VehicleController {

    // 3. 注入Repository
    private final VehicleRepository vehicleRepository;

    // 推荐使用构造函数注入（而非@Autowired字段注入）
    public VehicleController(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    // 4. 处理GET请求，获取所有车辆
    // URL: GET http://localhost:8080/api/vehicles
    @GetMapping
    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll(); // 调用JpaRepository内置的findAll()方法
    }

    // 5. 处理GET请求，根据ID获取一辆车
    // URL: GET http://localhost:8080/api/vehicles/1
    @GetMapping("/{id}")
    public Vehicle getVehicleById(@PathVariable Long id) {
        return vehicleRepository.findById(id) // 返回Optional<Vehicle>
                .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id)); // 如果找不到则抛出异常
    }

    // 6. 处理POST请求，创建一辆新车
    // URL: POST http://localhost:8080/api/vehicles
    // Body: JSON对象 { "licensePlate": "川A12345", "modelType": "重型卡车", "loadCapacity": 20.5, "currentStatus": "空闲" }
    @PostMapping
    public Vehicle createVehicle(@RequestBody Vehicle vehicle) { // @RequestBody将传入的JSON自动映射到Vehicle对象
        return vehicleRepository.save(vehicle); // 调用JpaRepository的save()方法，插入或更新
    }

    // 7. 调用自定义的Repository方法
    // URL: GET http://localhost:8080/api/vehicles/status/空闲
    @GetMapping("/status/{status}")
    public List<Vehicle> getVehiclesByStatus(@PathVariable Vehicle.VehicleStatus status) {
        return vehicleRepository.findByCurrentStatus(status);
    }
}
