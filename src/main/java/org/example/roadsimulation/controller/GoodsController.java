package org.example.roadsimulation.controller;

import jakarta.validation.Valid;
import org.example.roadsimulation.entity.Assignment;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.exception.GoodsAlreadyExistsException;
import org.example.roadsimulation.service.GoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    private GoodsService goodsService;

    @Autowired
    public GoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    //创建货物
    @PostMapping
    public ResponseEntity<?> createGoods(@Valid @RequestBody Goods goods) {
        try{
            Goods createdGoods = goodsService.createGoods(goods);
            return new ResponseEntity<>(createdGoods, HttpStatus.CREATED);
        } catch (GoodsAlreadyExistsException e){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "货物已存在",
                            "message", e.getMessage(),
                            "existingGoodsId", e.getExistingGoodsId(),
                            "suggestion", "请使用现用货物或修改SKU"
                    ));
        }
    }

    // 获取所有货物
    @GetMapping
    public ResponseEntity<Page<Goods>> getAllGoods(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction){
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<Goods> goodsPage = goodsService.getAllGoods(pageable);
        return ResponseEntity.ok(goodsPage);
    }
    // 根据ID获取货物
    @GetMapping("/{id}")
    public ResponseEntity<Goods> getGoodsById(@PathVariable Long id){
        Optional<Goods> goods = goodsService.getGoodsById(id);
        return goods.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    // 根据SKU获取货物
    @GetMapping("/sku/{sku}")
    public ResponseEntity<Goods> getGoodsBySku(@PathVariable String sku){
        Optional<Goods> goods = goodsService.getGoodsBySku(sku);
        return goods.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    // 根据名称搜索货物
    @GetMapping("/search/name")
    public ResponseEntity<List<Goods>> searchGoodsByName(@RequestParam String name) {
        List<Goods> goodsList = goodsService.searchGoodsByName(name);
        return ResponseEntity.ok(goodsList);
    }
    // 6. 多条件搜索货物（支持分页）
    @GetMapping("/search")
    public ResponseEntity<Page<Goods>> searchGoods(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean requireTemp,
            @RequestParam(required = false) String hazmatLevel,
            @RequestParam(required = false) Double minWeight,
            @RequestParam(required = false) Double maxWeight,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        try {
            Page<Goods> result = goodsService.searchGoods(
                    name, category, requireTemp, hazmatLevel, minWeight, maxWeight, pageable);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    // 7. 更新货物信息
    @PutMapping("/{id}")
    public ResponseEntity<Goods> updateGoods(@PathVariable Long id, @Valid @RequestBody Goods goodsDetails) {
        try {
            Goods updatedGoods = goodsService.updateGoods(id, goodsDetails);
            return ResponseEntity.ok(updatedGoods);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 8. 删除货物
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoods(@PathVariable Long id) {
        try {
            goodsService.deleteGoods(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (GoodsInUseException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // 9. 获取货物的运单项
    @GetMapping("/{id}/shipment-items")
    public ResponseEntity<List<ShipmentItem>> getShipmentItemsByGoodsId(@PathVariable Long id) {
        try {
            List<ShipmentItem> items = goodsService.getShipmentItemsByGoodsId(id);
            return ResponseEntity.ok(items);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 10. 获取货物的分配任务
    @GetMapping("/{id}/assignments")
    public ResponseEntity<List<Assignment>> getAssignmentsByGoodsId(@PathVariable Long id) {
        try {
            List<Assignment> assignments = goodsService.getAssignmentsByGoodsId(id);
            return ResponseEntity.ok(assignments);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 11. 获取货物运输统计
    @GetMapping("/{id}/stats")
    public ResponseEntity<GoodsTransportStats> getTransportStats(@PathVariable Long id) {
        try {
            GoodsTransportStats stats = goodsService.getTransportStats(id);
            return ResponseEntity.ok(stats);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
