package org.example.roadsimulation.controller;

import jakarta.validation.Valid;
import org.example.roadsimulation.dto.ActiveShipmentSummaryDTO;
import org.example.roadsimulation.dto.RouteMetricsResponse;
import org.example.roadsimulation.dto.ShipmentProgressDTO;
import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.service.ShipmentProgressService;
import org.example.roadsimulation.service.ShipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.HashMap;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentController.class);

    private final ShipmentService shipmentService;
    private final ShipmentProgressService shipmentProgressService;

    @Autowired
    public ShipmentController(ShipmentService shipmentService,
                              ShipmentProgressService shipmentProgressService) {
        this.shipmentService = shipmentService;
        this.shipmentProgressService = shipmentProgressService;
    }

    // 1. 创建运单 - POST /api/shipments
    @PostMapping
    public ResponseEntity<Shipment> createShipment(@Valid @RequestBody Shipment shipment) {
        try {
            Shipment createdShipment = shipmentService.createShipment(shipment);
            return new ResponseEntity<>(createdShipment, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 2. 更新运单 - PUT /api/shipments/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Shipment> updateShipment(
            @PathVariable Long id,
            @Valid @RequestBody Shipment shipmentDetails) {
        try {
            Shipment updatedShipment = shipmentService.updateShipment(id, shipmentDetails);
            return ResponseEntity.ok(updatedShipment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 3. 获取所有运单 - GET /api/shipments
    @GetMapping
    public ResponseEntity<List<Shipment>> getAllShipments() {
        List<Shipment> shipments = shipmentService.getAllShipments();
        return ResponseEntity.ok(shipments);
    }

    // 4. 分页获取运单 - GET /api/shipments/page
    @GetMapping("/page")
    public ResponseEntity<Page<Shipment>> getShipmentsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<Shipment> shipmentPage = shipmentService.getAllShipments(pageable);
        return ResponseEntity.ok(shipmentPage);
    }

    // 5. 根据ID获取运单 - GET /api/shipments/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Shipment> getShipmentById(@PathVariable Long id) {
        Optional<Shipment> shipment = shipmentService.getShipmentById(id);
        return shipment.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 6. 根据参考号获取运单 - GET /api/shipments/ref-no/{refNo}
    @GetMapping("/ref-no/{refNo}")
    public ResponseEntity<Shipment> getShipmentByRefNo(@PathVariable String refNo) {
        Optional<Shipment> shipment = shipmentService.getShipmentByRefNo(refNo);
        return shipment.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 7. 根据状态查询运单 - GET /api/shipments/status/{status}
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Shipment>> getShipmentsByStatus(
            @PathVariable Shipment.ShipmentStatus status) {
        List<Shipment> shipments = shipmentService.getShipmentsByStatus(status);
        return ResponseEntity.ok(shipments);
    }

    // 8. 根据客户ID查询运单 - GET /api/shipments/customer/{customerId}
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Shipment>> getShipmentsByCustomerId(@PathVariable Long customerId) {
        List<Shipment> shipments = shipmentService.getShipmentsByCustomerId(customerId);
        return ResponseEntity.ok(shipments);
    }

    // 9. 综合查询 - GET /api/shipments/search
    @GetMapping("/search")
    public ResponseEntity<Page<Shipment>> searchShipments(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Shipment.ShipmentStatus status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        try {
            if (startDate != null) {
                startDateTime = LocalDateTime.parse(startDate);
            }
            if (endDate != null) {
                endDateTime = LocalDateTime.parse(endDate);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Shipment> result = shipmentService.searchShipments(
                customerId, status, startDateTime, endDateTime, pageable);

        return ResponseEntity.ok(result);
    }

    // 10. 更新运单状态 - PATCH /api/shipments/{id}/status
    @PatchMapping("/{id}/status")
    public ResponseEntity<Shipment> updateShipmentStatus(
            @PathVariable Long id,
            @RequestParam Shipment.ShipmentStatus status) {
        try {
            Shipment updatedShipment = shipmentService.updateStatus(id, status);
            return ResponseEntity.ok(updatedShipment);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 11. 删除运单 - DELETE /api/shipments/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShipment(@PathVariable Long id) {
        try {
            shipmentService.deleteShipment(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 12. 检查参考号是否存在 - GET /api/shipments/exists/{refNo}
    @GetMapping("/exists/{refNo}")
    public ResponseEntity<Boolean> checkRefNoExists(@PathVariable String refNo) {
        boolean exists = shipmentService.existsByRefNo(refNo);
        return ResponseEntity.ok(exists);
    }

    /**
     * 13. 计算并保存路线指标
     * POST /api/shipments/{shipmentId}/vehicles/{vehicleId}/route-metrics
     */
    @PostMapping("/{shipmentId}/vehicles/{vehicleId}/route-metrics")
    public ResponseEntity<RouteMetricsResponse> calculateAndStoreRouteMetrics(
            @PathVariable Long shipmentId,
            @PathVariable Long vehicleId) {
        try {
            RouteMetricsResponse response = shipmentService.calculateAndStoreRouteMetrics(shipmentId, vehicleId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("计算路线指标失败，参数错误，shipmentId: {}, vehicleId: {}, error: {}",
                    shipmentId, vehicleId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            logger.error("计算路线指标失败，shipmentId: {}, vehicleId: {}", shipmentId, vehicleId, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("计算路线指标失败，shipmentId: {}, vehicleId: {}", shipmentId, vehicleId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ============================
    // 运单进度相关接口
    // ============================

    /**
     * 批量生成运单
     * POST /api/shipments/batch-generate
     * 请求体: { "count": 5 }
     * 返回: List<Shipment>
     */
    @PostMapping("/batch-generate")
    public ResponseEntity<List<Shipment>> batchGenerateShipments(@RequestBody Map<String, Integer> params) {
        int count = params.getOrDefault("count", 1);
        try {
            List<Shipment> shipments = shipmentService.batchGenerateShipments(count);
            return ResponseEntity.ok(shipments);
        } catch (Exception e) {
            logger.error("批量生成运单失败", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 获取活跃运单列表（包含进度概览）
     * GET /api/shipments/active
     */

    @GetMapping("/active")
    public ResponseEntity<List<ActiveShipmentSummaryDTO>> getActiveShipments() {
        logger.info("请求获取活跃运单列表");

        try {
            List<ActiveShipmentSummaryDTO> activeShipments = shipmentProgressService.getActiveShipments();
            return ResponseEntity.ok(activeShipments);
        } catch (Exception e) {
            logger.error("获取活跃运单列表失败", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 获取运单的完整进度信息
     * GET /api/shipments/{id}/progress
     */
    @GetMapping("/{id}/progress")
    public ResponseEntity<ShipmentProgressDTO> getShipmentProgress(@PathVariable Long id) {
        logger.info("请求获取运单进度信息，运单ID: {}", id);

        try {
            ShipmentProgressDTO progress = shipmentProgressService.getShipmentProgress(id);
            return ResponseEntity.ok(progress);
        } catch (RuntimeException e) {
            logger.error("获取运单进度信息失败，运单ID: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("获取运单进度信息失败，运单ID: {}", id, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 批量获取运单进度信息
     * POST /api/shipments/batch-progress
     */
    @PostMapping("/batch-progress")
    public ResponseEntity<List<ShipmentProgressDTO>> getBatchShipmentProgress(@RequestBody List<Long> shipmentIds) {
        logger.info("请求批量获取运单进度信息，运单数量: {}", shipmentIds.size());

        try {
            List<ShipmentProgressDTO> progressList = shipmentProgressService.getBatchShipmentProgress(shipmentIds);
            return ResponseEntity.ok(progressList);
        } catch (Exception e) {
            logger.error("批量获取运单进度信息失败", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }


    /**
     * 更新运单进度（通常由车辆到达事件触发）
     * PATCH /api/shipments/{id}/update-progress
     */
    @PatchMapping("/{id}/update-progress")
    public ResponseEntity<Void> updateShipmentProgress(@PathVariable Long id) {
        logger.info("请求更新运单进度，运单ID: {}", id);

        try {
            shipmentProgressService.updateShipmentProgress(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            logger.error("更新运单进度失败，运单ID: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("更新运单进度失败，运单ID: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有运单的进度摘要（用于仪表板）
     * GET /api/shipments/progress-summary
     */
    @GetMapping("/progress-summary")
    public ResponseEntity<Map<String, Object>> getOverallProgressSummary() {
        logger.info("请求获取所有运单进度摘要");

        try {
            Map<String, Object> summary = shipmentProgressService.getOverallProgressSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("获取运单进度摘要失败", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }
}
