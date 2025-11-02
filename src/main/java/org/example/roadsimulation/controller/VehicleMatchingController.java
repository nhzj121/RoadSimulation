package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.VehicleMatchResult;
import org.example.roadsimulation.dto.VehicleMatchingCriteria;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.service.GoodsService;
import org.example.roadsimulation.service.VehicleMatchingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle-matching")
public class VehicleMatchingController {

    private final VehicleMatchingService vehicleMatchingService;
    private final GoodsService goodsService;

    public VehicleMatchingController(VehicleMatchingService vehicleMatchingService,
                                     GoodsService goodsService) {
        this.vehicleMatchingService = vehicleMatchingService;
        this.goodsService = goodsService;
    }

    /**
     * 为特定货物匹配车辆
     */
    @PostMapping("/match-for-goods/{goodsId}")
    public ResponseEntity<List<VehicleMatchResult>> matchForGoods(
            @PathVariable Long goodsId,
            @RequestParam Integer quantity) {

        return goodsService.getGoodsById(goodsId)
                .map(goods -> {
                    List<VehicleMatchResult> results = vehicleMatchingService.matchVehiclesForGoods(goods, quantity);
                    return ResponseEntity.ok(results);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据自定义条件匹配车辆
     */
    @PostMapping("/match-by-criteria")
    public ResponseEntity<List<VehicleMatchResult>> matchByCriteria(@RequestBody VehicleMatchingCriteria criteria) {
        List<VehicleMatchResult> results = vehicleMatchingService.matchVehiclesByCriteria(criteria);
        return ResponseEntity.ok(results);
    }

    /**
     * 快速匹配 - 仅基于载重
     */
    @GetMapping("/quick-match")
    public ResponseEntity<List<Vehicle>> quickMatch(@RequestParam Double requiredLoad) {
        List<Vehicle> vehicles = vehicleMatchingService.quickMatchByLoadCapacity(requiredLoad);
        return ResponseEntity.ok(vehicles);
    }

    /**
     * 获取所有可用车辆
     */
    @GetMapping("/available-vehicles")
    public ResponseEntity<List<Vehicle>> getAvailableVehicles() {
        List<Vehicle> vehicles = vehicleMatchingService.getAvailableVehicles();
        return ResponseEntity.ok(vehicles);
    }

    /**
     * 获取所有品牌
     */
    @GetMapping("/brands")
    public ResponseEntity<List<String>> getAllBrands() {
        List<String> brands = vehicleMatchingService.getAllBrands();
        return ResponseEntity.ok(brands);
    }

    /**
     * 获取所有车辆类型
     */
    @GetMapping("/vehicle-types")
    public ResponseEntity<List<String>> getAllVehicleTypes() {
        List<String> types = vehicleMatchingService.getAllVehicleTypes();
        return ResponseEntity.ok(types);
    }
}