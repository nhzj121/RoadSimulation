package org.example.roadsimulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.roadsimulation.dto.VehicleMatchResult;
import org.example.roadsimulation.dto.VehicleMatchingCriteria;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.VehicleGoodsMatch;
import org.example.roadsimulation.service.GoodsService;
import org.example.roadsimulation.service.VehicleMatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 车辆匹配控制器
 * 提供货物与车辆适配的 API 接口
 */
@RestController
@RequestMapping("/api/vehicle-matching")
@Tag(name = "车辆匹配管理", description = "货物与车辆适配匹配相关接口")
public class VehicleMatchingController {

    private final VehicleMatchingService vehicleMatchingService;
    private final GoodsService goodsService;

    @Autowired
    public VehicleMatchingController(VehicleMatchingService vehicleMatchingService,
                                     GoodsService goodsService) {
        this.vehicleMatchingService = vehicleMatchingService;
        this.goodsService = goodsService;
    }

    /**
     * 为指定货物匹配车辆
     */
    @GetMapping("/goods/{goodsId}")
    @Operation(summary = "为货物匹配车辆", description = "根据货物信息自动匹配适合的车辆")
    public ResponseEntity<?> matchVehiclesForGoods(
            @Parameter(description = "货物 ID") @PathVariable Long goodsId,
            @Parameter(description = "货物数量") @RequestParam(defaultValue = "1") Integer quantity) {

        Goods goods = goodsService.getGoodsById(goodsId)
                .orElseThrow(() -> new RuntimeException("货物不存在，ID: " + goodsId));

        List<VehicleMatchResult> results = vehicleMatchingService.matchVehiclesForGoods(goods, quantity);

        Map<String, Object> response = new HashMap<>();
        response.put("goods", goods);
        response.put("quantity", quantity);
        response.put("matchedVehicles", results);
        response.put("totalMatches", results.size());

        return ResponseEntity.ok(response);
    }

    /**
     * 根据自定义条件匹配车辆
     */
    @PostMapping("/criteria")
    @Operation(summary = "按条件匹配车辆", description = "根据自定义匹配条件筛选车辆")
    public ResponseEntity<List<VehicleMatchResult>> matchVehiclesByCriteria(
            @RequestBody VehicleMatchingCriteria criteria) {

        List<VehicleMatchResult> results = vehicleMatchingService.matchVehiclesByCriteria(criteria);
        return ResponseEntity.ok(results);
    }

    /**
     * 快速匹配 - 仅按载重
     */
    @GetMapping("/quick-match")
    @Operation(summary = "快速匹配（按载重）", description = "仅根据载重需求快速匹配车辆")
    public ResponseEntity<List<Vehicle>> quickMatchByLoad(
            @Parameter(description = "所需载重（吨）") @RequestParam Double load) {

        List<Vehicle> vehicles = vehicleMatchingService.quickMatchByLoadCapacity(load);
        return ResponseEntity.ok(vehicles);
    }

    /**
     * 就近匹配
     */
    @PostMapping("/proximity")
    @Operation(summary = "就近匹配", description = "考虑车辆与出发地距离进行匹配")
    public ResponseEntity<List<VehicleMatchResult>> matchByProximity(
            @RequestBody VehicleMatchingCriteria criteria) {

        List<VehicleMatchResult> results = vehicleMatchingService.matchVehiclesByProximity(criteria);
        return ResponseEntity.ok(results);
    }

    /**
     * 智能匹配
     */
    @PostMapping("/smart")
    @Operation(summary = "智能匹配", description = "综合考量匹配度和距离进行智能匹配")
    public ResponseEntity<List<VehicleMatchResult>> smartMatch(
            @RequestBody VehicleMatchingCriteria criteria,
            @Parameter(description = "距离权重 (0-1)") @RequestParam(defaultValue = "0.3") Double distanceWeight) {

        List<VehicleMatchResult> results = vehicleMatchingService.smartMatchWithDistance(criteria, distanceWeight);
        return ResponseEntity.ok(results);
    }

    /**
     * 匹配并保存记录
     */
    @PostMapping("/goods/{goodsId}/match-and-save")
    @Operation(summary = "匹配并保存记录", description = "为货物匹配车辆并保存匹配记录到数据库")
    public ResponseEntity<?> matchAndSave(
            @Parameter(description = "货物 ID") @PathVariable Long goodsId,
            @Parameter(description = "货物数量") @RequestParam(defaultValue = "1") Integer quantity,
            @Parameter(description = "出发地 POI ID") @RequestParam(required = false) Long originPoiId,
            @Parameter(description = "目的地 POI ID") @RequestParam(required = false) Long destinationPoiId) {

        Goods goods = goodsService.getGoodsById(goodsId)
                .orElseThrow(() -> new RuntimeException("货物不存在，ID: " + goodsId));

        List<VehicleGoodsMatch> matches = vehicleMatchingService.matchAndSaveRecords(
                goods, quantity, originPoiId, destinationPoiId);

        Map<String, Object> response = new HashMap<>();
        response.put("goods", goods);
        response.put("matches", matches);
        response.put("totalMatches", matches.size());

        return ResponseEntity.ok(response);
    }

    /**
     * 查找最佳匹配并保存
     */
    @PostMapping("/goods/{goodsId}/best-match")
    @Operation(summary = "查找最佳匹配", description = "为货物查找最佳匹配车辆并保存记录")
    public ResponseEntity<?> findBestMatch(
            @Parameter(description = "货物 ID") @PathVariable Long goodsId,
            @Parameter(description = "货物数量") @RequestParam(defaultValue = "1") Integer quantity,
            @Parameter(description = "出发地 POI ID") @RequestParam(required = false) Long originPoiId,
            @Parameter(description = "目的地 POI ID") @RequestParam(required = false) Long destinationPoiId) {

        Goods goods = goodsService.getGoodsById(goodsId)
                .orElseThrow(() -> new RuntimeException("货物不存在，ID: " + goodsId));

        VehicleGoodsMatch match = vehicleMatchingService.findBestMatchAndSave(
                goods, quantity, originPoiId, destinationPoiId);

        if (match == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(match);
    }

    /**
     * 确认匹配记录
     */
    @PostMapping("/match/{matchId}/confirm")
    @Operation(summary = "确认匹配", description = "确认匹配记录，表示接受该匹配")
    public ResponseEntity<VehicleGoodsMatch> confirmMatch(
            @Parameter(description = "匹配记录 ID") @PathVariable Long matchId) {

        VehicleGoodsMatch match = vehicleMatchingService.confirmMatch(matchId);
        return ResponseEntity.ok(match);
    }

    /**
     * 拒绝匹配记录
     */
    @PostMapping("/match/{matchId}/reject")
    @Operation(summary = "拒绝匹配", description = "拒绝匹配记录")
    public ResponseEntity<VehicleGoodsMatch> rejectMatch(
            @Parameter(description = "匹配记录 ID") @PathVariable Long matchId,
            @Parameter(description = "拒绝原因") @RequestParam String reason) {

        VehicleGoodsMatch match = vehicleMatchingService.rejectMatch(matchId, reason);
        return ResponseEntity.ok(match);
    }

    /**
     * 获取所有可用车辆
     */
    @GetMapping("/available-vehicles")
    @Operation(summary = "获取可用车辆", description = "获取所有空闲状态的车辆")
    public ResponseEntity<List<Vehicle>> getAvailableVehicles() {
        List<Vehicle> vehicles = vehicleMatchingService.getAvailableVehicles();
        return ResponseEntity.ok(vehicles);
    }

    /**
     * 获取匹配统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取匹配统计", description = "获取车辆匹配的统计信息")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAvailableVehicles", vehicleMatchingService.getAvailableVehicles().size());
        stats.put("totalBrands", vehicleMatchingService.getAllBrands().size());
        stats.put("totalVehicleTypes", vehicleMatchingService.getAllVehicleTypes().size());
        return ResponseEntity.ok(stats);
    }
}
