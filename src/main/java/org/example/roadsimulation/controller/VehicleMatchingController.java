package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.VehicleMatchResult;
import org.example.roadsimulation.dto.VehicleMatchingCriteria;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.service.GoodsService;
import org.example.roadsimulation.service.VehicleMatchingService;
import org.example.roadsimulation.service.impl.VehicleMatchingServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle-matching")
public class VehicleMatchingController {

    private final VehicleMatchingService vehicleMatchingService;
    private final GoodsService goodsService;
    private final POIRepository poiRepository; // 新增

    public VehicleMatchingController(VehicleMatchingService vehicleMatchingService,
                                     GoodsService goodsService,
                                     POIRepository poiRepository) { // 修改构造器
        this.vehicleMatchingService = vehicleMatchingService;
        this.goodsService = goodsService;
        this.poiRepository = poiRepository;
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
     * 就近匹配车辆
     */
    @PostMapping("/match-by-proximity")
    public ResponseEntity<List<VehicleMatchResult>> matchByProximity(@RequestBody VehicleMatchingCriteria criteria) {
        List<VehicleMatchResult> results = vehicleMatchingService.matchVehiclesByProximity(criteria);
        return ResponseEntity.ok(results);
    }

    /**
     * 智能匹配（综合匹配度和距离）
     */
    @PostMapping("/smart-match")
    public ResponseEntity<List<VehicleMatchResult>> smartMatch(
            @RequestBody VehicleMatchingCriteria criteria,
            @RequestParam(required = false, defaultValue = "0.3") Double distanceWeight) {

        List<VehicleMatchResult> results = vehicleMatchingService.smartMatchWithDistance(criteria, distanceWeight);
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
     * 获取区域内的可用车辆
     */
    @GetMapping("/vehicles-in-area")
    public ResponseEntity<List<Vehicle>> getVehiclesInArea(
            @RequestParam Long poiId,
            @RequestParam Double radiusKm) {

        POI poi = poiRepository.findById(poiId).orElse(null);
        if (poi == null) {
            return ResponseEntity.notFound().build();
        }

        // 需要将getAvailableVehiclesInArea方法添加到接口或通过类型转换调用
        VehicleMatchingServiceImpl serviceImpl = (VehicleMatchingServiceImpl) vehicleMatchingService;
        List<Vehicle> vehicles = serviceImpl.getAvailableVehiclesInArea(poiId, radiusKm);
        return ResponseEntity.ok(vehicles);
    }

    /**
     * 为特定货物推荐就近车辆
     */
    @PostMapping("/recommend-for-goods/{goodsId}")
    public ResponseEntity<List<Vehicle>> recommendVehiclesForGoods(
            @PathVariable Long goodsId,
            @RequestParam Long originPoiId,
            @RequestParam Integer quantity) {

        return goodsService.getGoodsById(goodsId)
                .map(goods -> {
                    // 计算货物需求
                    Double totalWeight = vehicleMatchingService.calculateTotalWeight(goods, quantity);

                    // 构建匹配条件
                    VehicleMatchingCriteria criteria = new VehicleMatchingCriteria();
                    criteria.setMinLoadCapacity(totalWeight);
                    criteria.setRequireTempControl(goods.getRequireTemp());
                    criteria.setHazmatLevel(goods.getHazmatLevel());

                    // 获取推荐车辆
                    List<Vehicle> vehicles = vehicleMatchingService.getRecommendedVehicles(originPoiId, criteria);
                    return ResponseEntity.ok(vehicles);
                })
                .orElse(ResponseEntity.notFound().build());
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